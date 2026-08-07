package fuse
import scala.quoted.*
import scala.collection.mutable.ArrayBuilder
import fuse.streamInternal.Stream
import scala.annotation.targetName

final class Parser[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes
  import ir.*
  import ir.quotes.reflect.*

  def parseExpression[A: Type, Buf: Type, R: Type](stream: Expr[fuse.streamInternal.Stream[A]], collector: Expr[Collector[A, Buf, R]]): Ast[A, Buf, R] = {
    println("Starting  to parse the stream body")
    println("=== parseTerm ===")
    println(stream.asTerm.show)
    println(stream.asTerm.show(using Printer.TreeStructure))
    val parsedTree = parseTerm(stream.asTerm)
    println("Stream body parsing done")

    // Extract the type parsed as the output and check for validity
    // TODO:do we really need it? isn't alreadt guaranteed by the compilation
    type ParsedOut = parsedTree.Out
    given Type[ParsedOut] = parsedTree.outType
    val actualType = TypeRepr.of[ParsedOut]
    val expectedType = TypeRepr.of[A]
    if (!(actualType =:= expectedType)) {
      report.errorAndAbort(
        s"""StreamFusion: parsed stream output type mismatchhh.
         |Expected: ${expectedType.show}
         |Found:    ${actualType.show}
         |""".stripMargin,
        stream.asTerm.pos
      )
    }
    println("Starting to parse the terminal")
    val parsedCollector = extractCollectionStrategy(collector)
    println("Terminal parsing done")
    println("Parsing the done")

    Ast(parsedTree.getCurrent(), parsedCollector)
  }

  private def parseTerm(expr: ir.quotes.reflect.Term): ParsedTree = {

    expr match {
      // Go into the inlined expression
      case Inlined(_, _, inner) => parseTerm(inner)

      // Explode a coode block, Pattern maching against the parsed expression in order to go to the implementation and deconstruct it
      case Block(blockStatements, expr) => parseTerm(expr) match { case ParsedTreeImpl(tree, decls) => ParsedTreeImpl(tree, blockStatements ++ decls) }

      /** ==================================== Sources ==================================== */
      // From expressions
      case Apply(TypeApply(NamedMethod("from"), _), List(arr)) => createSource(arr)
      // Of
      case Apply(TypeApply(NamedMethod("of"), _), List(arr)) => createSourceOf(arr)

      /** ====================  (filter, skip, limit) ==================== */
      case UnaryOperator(name, upstream, arg) => {
        val parsedUpstream = parseTerm(upstream)
        name match {
          case "filter" => appendFilter(parsedUpstream, arg)
          case "skip"   => appendSkip(parsedUpstream, arg)
          case "limit"  => appendLimit(parsedUpstream, arg)
          case _        => report.errorAndAbort(s"StreamFusion: operator non supportato: $name")
        }
      }

      /** ==================== Map / FlatMap) ==================== */
      case OperatorWithOutput(name, upstream, f, maybeTypeTree) => {
        val parsedUpstream = parseTerm(upstream)
        val typeTree = maybeTypeTree.getOrElse(TypeTree.of[Any])

        name match {
          case "map"     => appendMap(parsedUpstream, f, typeTree)
          case "flatMap" => appendFlatMap(parsedUpstream, f, typeTree)
          case _         => report.errorAndAbort(s"StreamFusion: trasforma sconosciuta: $name")
        }
      }

      /** ==================================== Error fallback ==================================== */
      case other => report.errorAndAbort(s"StreamFusion: unexpected expression: ${other}")
    }
  }

  /** Parsing a .from() can be from an iterable or from an Array */
  private def createSource(term: Term): ParsedTree = {
    val sourceTpr = getTypeRepr(term)
    val arraySymbol = TypeRepr.of[Array].typeSymbol
    val iterableSymbol = TypeRepr.of[Iterable].typeSymbol
    // Specialize the different sources, so we can access the most efficient way
    sourceTpr match {
      case arr if arr.derivesFrom(arraySymbol) =>
        arr.baseType(arraySymbol) match {
          case AppliedType(_, List(elemTpe)) => getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](ArraySource[elem](term.asExprOf[Array[elem]], Type.of[elem])) }
        }

      case iter if iter.derivesFrom(iterableSymbol) =>
        iter.baseType(iterableSymbol) match {
          case AppliedType(_, List(elemTpe)) => getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](IterableSource[elem](term.asExprOf[Iterable[elem]], Type.of[elem])) }
        }
      case _ => report.errorAndAbort(s"StreamFusion: from() expected one of Iterable[T]/Array[T], got ${sourceTpr}", term.pos)
    }
  }

  /** Parsing an of source, consisting of a single element */
  private def createSourceOf(item: Term): ParsedTree = {

    getType(item) match {
      case '[elem] =>
        //  Given the type of the element elem at runtime it will be converted to an iteratorable and enter in the from(iterable<>) flow. this can be optimized
        val singleExpr = item.asExprOf[elem]
        val iterableExpr: Expr[Iterable[elem]] = '{ Iterable.single[elem](${ singleExpr }) }
        ParsedTreeImpl[elem](IterableSource[elem](iterableExpr, Type.of[elem]))
    }
  }

  private def appendFilter(upstream: ParsedTree, predicateTerm: Term): ParsedTree = {
    // The filter do not alter the type
    type InOut = upstream.Out
    given Type[InOut] = upstream.outType

    val predicate = predicateTerm.asExprOf[InOut => Boolean]
    val filter = Filter[InOut](upstream.current, predicate, upstream.outType)

    ParsedTreeImpl[InOut](filter)
  }

  private def appendSkip(upstream: ParsedTree, skipTerm: Term): ParsedTree = {
    // The skip do not alter the type
    type InOut = upstream.Out

    val skipNum = skipTerm.asExprOf[Int].value.getOrElse {
      report.errorAndAbort(s"StreamFusion: 'skip' requires a constant Int known at compile-time. Got: ${skipTerm}", skipTerm.pos)
    }

    val skip = Skip[InOut](upstream.current, skipNum, upstream.outType)
    ParsedTreeImpl[InOut](skip)
  }

  private def appendLimit(upstream: ParsedTree, limitTerm: Term): ParsedTree = {
    // The skip do not alter the type
    type InOut = upstream.Out

    val limitNum = limitTerm.asExprOf[Int].value.getOrElse {
      report.errorAndAbort(s"StreamFusion: 'skip' requires a constant Int known at compile-time. Got: ${limitTerm}", limitTerm.pos)
    }

    val skip = Limit[InOut](upstream.current, limitNum, upstream.outType)
    ParsedTreeImpl[InOut](skip)
  }

  private def appendFlatMap(upstream: ParsedTree, functionTerm: Term, outputType: TypeTree): ParsedTree = {
    // The input of the flat map is the output of the upstrea, the Out is derived from the function
    type In = upstream.Out
    given Type[In] = upstream.outType

    getType(outputType) match {
      case '[out] =>
        val function = functionTerm.asExprOf[In => Stream[out]]

        // Logical binder representing the current element produced by the upstream stream.
        // The code generator later will emit a ValDef for this symbol, binding it to the actual current upstream element.
        // We use this trick in order to betareduce the expression thus parsing the inner stream definition
        val flatMapBinder = createConstant[In]("flatMapElem")
        val elemRef = Ref(flatMapBinder).asExprOf[In]

        // Converts: x => from(source(x)).map(...)
        // into: from(source(elemRef)).map(...) linking against the just created binder
        // This way the inner stream can be parsed recursively.
        val innerStreamExpr: Expr[Stream[out]] = Expr.betaReduce { '{ $function($elemRef) } }
        // Once the inner stream is extracted we now parse it as a standalone stream
        val parsedInner = parseTerm(innerStreamExpr.asTerm)

        // TODO: into a method
        type InnerOutput = parsedInner.Out
        given Type[InnerOutput] = parsedInner.outType

        val expectedType = TypeRepr.of[out]
        val actualType = TypeRepr.of[InnerOutput]

        if (!(actualType =:= expectedType)) {
          report.errorAndAbort(
            s"""StreamFusion: flatMap inner stream output type mismatch.
             |Expected: ${expectedType.show}
             |Found:    ${actualType.show}
             |""".stripMargin,
            functionTerm.pos
          )
        }

        val flatMap = FlatMap[In, out](
          upstream = upstream.current, // keep the reference to the previous node
          innerTree = parsedInner.getCurrent[out](), // The inner tree is the result of the parsing of the function
          inType = upstream.outType, // The input of the flatmap is the output of the previous node
          outType = Type.of[out], // The outptu is out, the type of the domain of the function
          elemSymbol = flatMapBinder, // keep the reference to the binder, will be linked later to the previous step
          innerDeclarations = parsedInner.declarations // the declarations extracted during the parsing of the inner stream
        )

        ParsedTreeImpl[out](current = flatMap, declarations = upstream.declarations)
    }
  }

  private def appendMap(upstream: ParsedTree, functionTerm: Term, outputTypeTree: TypeTree): ParsedTree = {
    // The input type is given by the upstream, the output is extracted from the
    type In = upstream.Out
    given Type[In] = upstream.outType // need for the asExprOf

    getType(outputTypeTree) match {
      case '[out] =>
        // Cast the function term to an actual Function
        val function = functionTerm.asExprOf[In => out]
        val map = Map[In, out](upstream.current, function, upstream.outType, Type.of[out])
        ParsedTreeImpl[out](map)
    }
  }

  private def extractCollectionStrategy[A: Type, Buf: Type, R: Type](collector: Expr[Collector[A, Buf, R]])(using Quotes): CollectionStrategy[A, Buf, R] = {

    // Check if we are treating the "fake" toArray collector
    val rawTpe = collector.asTerm.tpe
    val widenedTpe = rawTpe.widen
    val dealiasedTpe = getTypeRepr(collector.asTerm)

    val toArraySymbol = TypeRepr.of[Collector.ToArrayCollector[Any]].typeSymbol
    val isTheOpaqueToArray = dealiasedTpe.typeSymbol == toArraySymbol
    println(
      s"""|
      |=== Collector type debug ===
      |term:              ${collector.asTerm.show}
      |raw type:          ${rawTpe.show}
      |raw symbol:        ${rawTpe.typeSymbol.fullName}
      |widened type:      ${widenedTpe.show}
      |widened symbol:    ${widenedTpe.typeSymbol.fullName}
      |dealiased type:    ${dealiasedTpe.show}
      |dealiased symbol:  ${dealiasedTpe.typeSymbol.fullName}
      |type args:         ${widenedTpe.typeArgs.map(_.show).mkString("[", ", ", "]")}
      |============================
      |""".stripMargin
    )

    if (isTheOpaqueToArray) {
      println(" --> is a specialized to array")
      ToArray[A]().asInstanceOf[CollectionStrategy[A, Buf, R]]
    } else {
      println(" --> is a generic collector")
      WithCollector[A, Buf, R](collector)
    }
  }

  /** Given a term returns it typeRepr */
  private def getTypeRepr(item: Term) = {
    item.tpe.widen.dealias
  }

  /** Given a type representation returns a Type[?] this can be pattern matched to extrat a type to use in quoted expressions */
  private def getType(item: TypeRepr) = {
    item.asType
  }

  /** Convert a typeTree to his type Type[?] this can be pattern matched to extrat a type to use in quoted expressions */
  @targetName("getTypeFromTree")
  private def getType(typeTree: TypeTree) = {
    typeTree.tpe.asType
  }

  /** Convert a term to his type Type[?] this can be pattern matched to extrat a type to use in quoted expressions */
  @targetName("getTypeFromTerm")
  private def getType(item: Term) = {
    item.tpe.widen.dealias.asType
  }

  // Unifica Ident("x") e Select(_, "x")
  object NamedMethod {
    def unapply(term: Term): Option[String] = term match {
      case Ident(name)     => Some(name)
      case Select(_, name) => Some(name)
      case _               => None
    }
  }

  // Riconosce la struttura del secondo TypeApply (es. per map/flatMap)
  // Estrae (upstream, f, Option[TypeTree]) gestendo sia la presenza che l'assenza del TypeTree di output
  object OperatorWithOutput {
    // Estrattore per (Name, Upstream, Function, Option[TypeTree])
    def unapply(term: Term): Option[(String, Term, Term, Option[TypeTree])] = term match {
      // Forma curried con TypeTree esplicito: Apply(TypeApply(Apply(TypeApply(method, _), List(upstream)), List(outTypeTree)), List(f))
      case Apply(TypeApply(Apply(TypeApply(NamedMethod(name), _), List(upstream)), List(outTypeTree: TypeTree)), List(f)) => Some((name, upstream, f, Some(outTypeTree)))

      // Forma con TypeTree o Senza (Fallback generale per flatMap / map curried)
      case Apply(Apply(TypeApply(NamedMethod(name), _), List(upstream)), List(f)) => Some((name, upstream, f, None))

      // Forma diretta: upstream.flatMap(f)
      case Apply(Select(upstream, name), List(f)) => Some((name, upstream, f, None))

      // Forma diretta con TypeApply: upstream.flatMap[T](f)
      case Apply(TypeApply(Select(upstream, name), _), List(f)) => Some((name, upstream, f, None))

      case _ => None
    }
  }

  object UnaryOperator {
    // Restituisce: Option[(NomeOperatore, UpstreamTerm, ArgomentoTerm)]
    def unapply(term: Term): Option[(String, Term, Term)] = term match {
      // Gestisce la forma curried Apply(Apply(TypeApply(method, _), List(upstream)), List(arg))
      case Apply(Apply(TypeApply(NamedMethod(name), _), List(upstream)), List(arg)) => Some((name, upstream, arg))
      // Gestisce anche chiamate dirette non-TypeApply (es. upstream.filter(p)):
      case Apply(Select(upstream, name), List(arg)) => Some((name, upstream, arg))
      case _                                        => None
    }
  }

  private sealed trait ParsedTree {
    type Out

    val current: StreamTree[Out]
    val declarations: List[ir.quotes.reflect.Statement]
    final def outType: Type[Out] = current.outType

    def getCurrent[A](): StreamTree[A] = {
      current.asInstanceOf[StreamTree[A]]
    }
  }

  private final case class ParsedTreeImpl[A](current: StreamTree[A], declarations: List[ir.quotes.reflect.Statement] = Nil) extends ParsedTree {
    type Out = A
  }
}

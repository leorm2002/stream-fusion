package fuse.internal
import scala.quoted.*
import scala.collection.mutable.ArrayBuilder
import scala.annotation.targetName
import CollectionStrategy.*
import fuse.internal.ir.{AnyIR, ExecutionMode, Phase}
import fuse.internal.CollectionStrategy
import fuse.Collector
import fuse.TerminationPolicy
import fuse.Summable
import fuse.ShortCircuiting
import fuse.CollectorBase
import  fuse.Stream

final class Parser[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes
  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*

  def parseExpression[A: Type, Buf: Type, R: Type, S <: TerminationPolicy](
      stream: Expr[Stream[A]],
      collector: Expr[Collector[A, Buf, R, S]],
      executionMode: ExecutionMode
  ): Ast[A, Buf, R] = {
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

    Ast(parsedTree.getCurrent(), parsedCollector, parsedTree.declarations, executionMode)
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

      /** ====================      (filter)   ==================== */
      case Apply(Select(upstream, "filter"), List(pred)) => appendFilter(parseTerm(upstream), pred)

      /** ====================      (skip)     ==================== */
      case Apply(Select(upstream, "skip"), List(n)) => appendSkip(parseTerm(upstream), n)

      /** ====================      (limit)    ==================== */
      case Apply(Select(upstream, "limit"), List(n)) => appendLimit(parseTerm(upstream), n)

      /** ====================      (Map)      ==================== */
      case Apply(TypeApply(Select(upstream, "map"), List(outType: TypeTree)), List(f)) => appendMap(parseTerm(upstream), f, outType)

      /** ====================      (FlatMap)  ==================== */
      case Apply(TypeApply(Select(upstream, "flatMap"), List(outType: TypeTree)), List(f)) => appendFlatMap(parseTerm(upstream), f, outType)
      /** ====================      (Parallel)  ==================== */
      case Apply(Select(upstream, "parallel"), _) => parseTerm(upstream) // it's used just for to type check the stream, all the information have already been used here

      /** ==================================== Error fallback ==================================== */
      case other => report.errorAndAbort(s"StreamFusion: unexpected expression: ${other}")
    }
  }

  /** Parsing a .from() can be from an iterable or from an Array */
  private def createSource(term: Term): ParsedTree = {
    val sourceTpr = getTypeRepr(term)
    val arraySymbol = TypeRepr.of[Array].typeSymbol
    val iterableSymbol = TypeRepr.of[Iterable].typeSymbol
    val javaList = TypeRepr.of[java.util.List].typeSymbol
    val javaIterableSymbol = TypeRepr.of[java.lang.Iterable].typeSymbol
    // Specialize the different sources, so we can access the most efficient way
    sourceTpr match {
      // E' una specializzazione di un javaIterableSymbol, abbiamo assunzioni particolari se abbiamo una list da fare che velocizzano l'esecuzione
      case jlist if jlist.derivesFrom(javaList) =>
        jlist.baseType(javaList) match {
          case AppliedType(_, List(elemTpe)) =>
            getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](JListSource[elem](term.asExprOf[java.util.List[elem]], Type.of[elem]), Nil) }
        }

      case jiter if jiter.derivesFrom(javaIterableSymbol) =>
        jiter.baseType(javaIterableSymbol) match {
          case AppliedType(_, List(elemTpe)) =>
            getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](JIterableSource[Phase.Raw,elem](term.asExprOf[java.lang.Iterable[elem]], Type.of[elem]), Nil) }
        }

      case arr if arr.derivesFrom(arraySymbol) =>
        arr.baseType(arraySymbol) match {
          case AppliedType(_, List(elemTpe)) => getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](ArraySource[elem](term.asExprOf[Array[elem]], Type.of[elem]), Nil) }
        }

      case iter if iter.derivesFrom(iterableSymbol) =>
        iter.baseType(iterableSymbol) match {
          case AppliedType(_, List(elemTpe)) =>
            getType(elemTpe) match { case '[elem] => ParsedTreeImpl[elem](IterableSource[Phase.Raw,elem](term.asExprOf[Iterable[elem]], Type.of[elem]), Nil) }
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
        ParsedTreeImpl[elem](IterableSource[Phase.Raw,elem](iterableExpr, Type.of[elem]), Nil)
    }
  }

  private def appendFilter(upstream: ParsedTree, predicateTerm: Term): ParsedTree = {
    // The filter do not alter the type
    type InOut = upstream.Out
    given Type[InOut] = upstream.outType

    val predicate = predicateTerm.asExprOf[InOut => Boolean]
    val filter = Filter[Phase.Raw,InOut](upstream.current, predicate, upstream.outType)

    ParsedTreeImpl[InOut](filter, upstream.declarations)
  }

  private def appendSkip(upstream: ParsedTree, skipTerm: Term): ParsedTree = {
    // The skip do not alter the type
    type InOut = upstream.Out

    val skipNum = skipTerm.asExprOf[Int]
    val skip = Slice[InOut](upstream.current, Option(skipNum), Option.empty, upstream.outType)
    ParsedTreeImpl[InOut](skip, upstream.declarations)
  }

  private def appendLimit(upstream: ParsedTree, limitTerm: Term): ParsedTree = {
    // The skip do not alter the type
    type InOut = upstream.Out

    val limitNum = limitTerm.asExprOf[Int]
    val limit = Slice[InOut](upstream.current, Option.empty, Option(limitNum), upstream.outType)

    ParsedTreeImpl[InOut](limit, upstream.declarations)
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
        val map = Map[Phase.Raw,In, out](upstream.current, function, upstream.outType, Type.of[out])
        ParsedTreeImpl[out](map, upstream.declarations)
    }
  }

  private def extractCollectionStrategy[A: Type, Buf: Type, R: Type, S <: TerminationPolicy](collector: Expr[Collector[A, Buf, R, S]])(using Quotes): CollectionStrategy[A, Buf, R] = {

    // Check if we are treating the "fake" toArray collector
    val rawTpe = collector.asTerm.tpe
    val widenedTpe = rawTpe.widen
    val dealiasedTpe = getTypeRepr(collector.asTerm)

    val toArraySymbol = TypeRepr.of[Collector.ToArrayCollector[Any]].typeSymbol
    val isTheOpaqueToArray = dealiasedTpe.typeSymbol == toArraySymbol

    val summingSymbol = TypeRepr.of[Collector.SummingCollector[Nothing]].typeSymbol
    val isTheOpaqueSumming = dealiasedTpe.typeSymbol == summingSymbol

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
    } else if (isTheOpaqueSumming) {
      println(" --> is a specialized sum")
      Summing[A & Summable]().asInstanceOf[CollectionStrategy[A, Buf, R]]
    } else {
      println(" --> is a generic collector")
      val collectorBaseType = dealiasedTpe.baseType(TypeRepr.of[Collector].typeSymbol)

      val stopPolicy = collectorBaseType match {
          case AppliedType(_, List(_, _, _, policy)) =>policy
          case _ =>report.errorAndAbort(s"Unexpected Collector type: ${dealiasedTpe.show}")
        }
      val isEarlyStopping = stopPolicy <:< TypeRepr.of[ShortCircuiting]
      WithCollector(collector.asExprOf[CollectorBase[A, Buf, R]], isEarlyStopping)

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
    typeTree.tpe.widen.dealias.asType
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

  private sealed trait ParsedTree {
    type Out

    val current: StreamTree[Phase.Raw, Out]
    val declarations: List[ir.quotes.reflect.Statement]
    final def outType: Type[Out] = current.outType

    def getCurrent[A](): StreamTree[Phase.Raw, A] = { current.asInstanceOf[StreamTree[Phase.Raw, A]]}
  }

  private final case class ParsedTreeImpl[A](current: StreamTree[Phase.Raw, A], declarations: List[ir.quotes.reflect.Statement]) extends ParsedTree {
    type Out = A
  }
}

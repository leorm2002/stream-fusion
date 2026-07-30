package fuse
import scala.quoted.*
import scala.collection.mutable.ArrayBuilder
import fuse.streamInternal.Stream

final class Parser[IR <: StreamIr & Singleton](val ir: IR) {
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
      case Inlined(_, _, inner) => parseTerm(inner)
      case Block(stats, expr)   => {
        val parsedExpr = parseTerm(expr)
        val blockStatements = stats.collect { case s: Statement => s }

        parsedExpr match {
          case ParsedTreeImpl(tree, decls) =>
            ParsedTreeImpl(tree, blockStatements ++ decls)
        }
      }
      // From expressions
      case Apply(TypeApply(Ident("from"), _), List(arr))     => createSource(arr)
      case Apply(TypeApply(Select(_, "from"), _), List(arr)) => createSource(arr)

      // Of
      case Apply(TypeApply(Ident("of"), _), List(arr))     => createSourceOf(arr)
      case Apply(TypeApply(Select(_, "of"), _), List(arr)) => createSourceOf(arr)

      // Filter expressions
      case Apply(Apply(TypeApply(Ident("filter"), _), List(upstream)), List(pred))     => appendFilter(parseTerm(upstream), pred)
      case Apply(Apply(TypeApply(Select(_, "filter"), _), List(upstream)), List(pred)) => appendFilter(parseTerm(upstream), pred)

      // Skip expressions
      case Apply(Apply(TypeApply(Ident("skip"), _), List(upstream)), List(pred))     => appendSkip(parseTerm(upstream), pred)
      case Apply(Apply(TypeApply(Select(_, "skip"), _), List(upstream)), List(pred)) => appendSkip(parseTerm(upstream), pred)

      // Limit
      case Apply(Apply(TypeApply(Ident("limit"), _), List(upstream)), List(pred))     => appendLimit(parseTerm(upstream), pred)
      case Apply(Apply(TypeApply(Select(_, "limit"), _), List(upstream)), List(pred)) => appendLimit(parseTerm(upstream), pred)

      // Map expressions
      case complete @ Apply(TypeApply(Apply(TypeApply(Ident("map"), _), List(upstream)), List(outputTypeTree)), List(f)) =>
        appendMap(parseTerm(upstream), f, outputTypeTree.tpe)
      case complete @ Apply(TypeApply(Apply(TypeApply(Select(_, "map"), _), List(upstream)), List(outputTypeTree)), List(f)) =>
        appendMap(parseTerm(upstream), f, outputTypeTree.tpe)

      // Flatmap
      case complete @ Apply(TypeApply(Apply(TypeApply(Ident("flatMap"), _), List(upstream)), List(outputTypeTree)), List(f)) =>
        appendFlatMap(parseTerm(upstream), f, outputTypeTree.tpe)
      case complete @ Apply(TypeApply(Apply(TypeApply(Select(_, "flatMap"), _), List(upstream)), List(outputTypeTree)), List(f)) =>
        appendFlatMap(parseTerm(upstream), f, outputTypeTree.tpe)
      case Apply(Apply(TypeApply(Select(_, "flatMap"), _), List(upstream)), List(f)) => appendFlatMap(parseTerm(upstream), f, TypeRepr.of[Any])
      case Apply(Apply(TypeApply(Ident("flatMap"), _), List(upstream)), List(f))     => appendFlatMap(parseTerm(upstream), f, TypeRepr.of[Any])
      case Apply(Select(upstream, "flatMap"), List(f))                               => appendFlatMap(parseTerm(upstream), f, TypeRepr.of[Any])
      case Apply(TypeApply(Select(upstream, "flatMap"), _), List(f))                 => appendFlatMap(parseTerm(upstream), f, TypeRepr.of[Any])
      case Apply(
            TypeApply(
              Apply(
                TypeApply(flatMapMethod, _),
                List(upstream)
              ),
              List(outputType)
            ),
            List(f)
          ) if flatMapMethod.symbol.name == "flatMap" =>
        appendFlatMap(parseTerm(upstream), f, outputType.tpe)

// case Apply(TypeApply(Select(reciever,method),List(f)),List(Ident(a)))

      // Apply(TypeApply(Select(This(Ident(E2eTests)),getLimitedStream),List(TypeTree[TypeRef(ThisType(TypeRef(NoPrefix,module class lang)),class String)])),List(Ident(a)))sbtExplain the error
      case other => report.errorAndAbort(s"StreamFusion: unexpected expression: ${other}")
    }
  }

  private def createSource(arr: Term): ParsedTree = {
    val sourceTpe = arr.tpe.widen.dealias

    val arraySymbol = TypeRepr.of[Array].typeSymbol
    val iterableSymbol = TypeRepr.of[Iterable].typeSymbol

    sourceTpe match {
      case t if t.derivesFrom(arraySymbol) =>
        t.baseType(arraySymbol) match {
          case AppliedType(_, List(elemTpe)) => elemTpe.asType match { case '[elem] => ParsedTreeImpl[elem](ArraySource[elem](arr.asExprOf[Array[elem]], Type.of[elem])) }
        }

      case t if t.derivesFrom(iterableSymbol) =>
        t.baseType(iterableSymbol) match {
          case AppliedType(_, List(elemTpe)) => elemTpe.asType match { case '[elem] => ParsedTreeImpl[elem](IterableSource[elem](arr.asExprOf[Iterable[elem]], Type.of[elem])) }
        }
      case _ => report.errorAndAbort(s"StreamFusion: from() expected one of Iterable[T]/Array[T], got ${sourceTpe}", arr.pos)
    }
  }

  private def createSourceOf(item: Term): ParsedTree = {
    val sourceTpe = item.tpe.widen.dealias

    sourceTpe.asType match {
      case '[elem] =>
        // Costruiamo al volo l'Expr di un Iterable da un singolo elemento
        val singleExpr = item.asExprOf[elem]
        val iterableExpr: Expr[Iterable[elem]] = '{ Iterable.single[elem](${ singleExpr }) }
        ParsedTreeImpl[elem](IterableSource[elem](iterableExpr, Type.of[elem]))
    }
  }

  private def appendFilter(upstream: ParsedTree, predicateTerm: Term): ParsedTree = {
    // The filter do not alter the type
    type Current = upstream.Out
    given Type[Current] = upstream.outType

    val predicate = predicateTerm.asExprOf[Current => Boolean]
    val filter = Filter[Current](upstream.current, predicate, upstream.outType)

    ParsedTreeImpl[Current](filter)
  }

  private def appendSkip(upstream: ParsedTree, skipTerm: Term): ParsedTree = {
    // The filter do not alter the type
    val sourceTpe = skipTerm.tpe.widen.dealias
    type Current = upstream.Out

    val skipNum = skipTerm.asExprOf[Int].value.getOrElse {
      report.errorAndAbort(
        s"StreamFusion: 'skip' requires a constant Int known at compile-time. Got: ${skipTerm}",
        skipTerm.pos
      )
    }
    val skip = Skip[Current](upstream.current, skipNum, upstream.outType)

    ParsedTreeImpl[Current](skip)
  }

  private def appendLimit(upstream: ParsedTree, limitTerm: Term): ParsedTree = {
    // The filter do not alter the type
    val sourceTpe = limitTerm.tpe.widen.dealias
    type Current = upstream.Out

    val limitNum = limitTerm.asExprOf[Int].value.getOrElse {
      report.errorAndAbort(
        s"StreamFusion: 'skip' requires a constant Int known at compile-time. Got: ${limitTerm}",
        limitTerm.pos
      )
    }
    val skip = Limit[Current](upstream.current, limitNum, upstream.outType)

    ParsedTreeImpl[Current](skip)
  }

  private def createVairble[T: Type](name: String) = {
    Symbol.newVal(
      parent = Symbol.spliceOwner,
      name = Symbol.freshName("flatMapElem"),
      tpe = TypeRepr.of[T],
      flags = Flags.EmptyFlags,
      privateWithin = Symbol.noSymbol
    )
  }

  private def appendFlatMap(upstream: ParsedTree, functionTerm: Term, outputType: TypeRepr): ParsedTree = {
    type Input = upstream.Out
    given Type[Input] = upstream.outType

    outputType.asType match {
      case '[output] =>
        val function = functionTerm.asExprOf[Input => Stream[output]]
        /*
         * Logical binder representing the current element produced by the upstream stream.
         * The code generator later will emit a ValDef for this symbol, binding it to the actual current upstream element.
         * We use this trick in order to betareduce the expression thus parsing the inner stream definition
         */
        val flatMapBinder = createVairble[Input]("flatMapElem")
        val elemRef = Ref(flatMapBinder).asExprOf[Input]

        /*
         * Converts: x => from(source(x)).map(...)
         * into: from(source(elemRef)).map(...)
         * This way the inner stream can be parsed recursively.
         */
        val innerStreamExpr: Expr[Stream[output]] = Expr.betaReduce { '{ $function($elemRef) } }

        val parsedInner = parseTerm(innerStreamExpr.asTerm)

        // TODO: into a method
        type InnerOutput = parsedInner.Out
        given Type[InnerOutput] = parsedInner.outType

        val expectedType = TypeRepr.of[output]
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

        val flatMap = FlatMap[Input, output](
          upstream = upstream.current,
          innerTree = parsedInner.getCurrent[output](),
          inType = upstream.outType,
          outType = Type.of[output],
          elemSymbol = flatMapBinder,
          innerDeclarations = parsedInner.declarations
        )

        ParsedTreeImpl[output](current = flatMap, declarations = upstream.declarations)
    }
  }

  private def appendMap(upstream: ParsedTree, functionTerm: Term, outputType: TypeRepr): ParsedTree = {
    type Input = upstream.Out

    given Type[Input] = upstream.outType

    outputType.asType match {
      case '[output] =>
        val function = functionTerm.asExprOf[Input => output]
        val map = Map[Input, output](upstream.current, function, upstream.outType, Type.of[output])
        ParsedTreeImpl[output](map)
    }
  }

  private def extractCollectionStrategy[A: Type, Buf: Type, R: Type](collector: Expr[Collector[A, Buf, R]])(using Quotes): CollectionStrategy[A, Buf, R] = {

    // Check if we are treating the fake toArray collector
    val rawTpe = collector.asTerm.tpe
    val widenedTpe = rawTpe.widen
    val dealiasedTpe = widenedTpe.dealias

    val toArrayTpe = TypeRepr.of[Collector.ToArrayCollector[Any]]
    val toArraySymbol = toArrayTpe.typeSymbol
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

    println(
      s"""|
      |=== ToArrayCollector check ===
      |collector type:        ${widenedTpe.show}
      |collector symbol:      ${widenedTpe.typeSymbol.fullName}
      |target type:           ${toArrayTpe.show}
      |target symbol:         ${toArraySymbol.fullName}
      |same symbol:           ${widenedTpe.typeSymbol == toArraySymbol}
      |collector <:< target:  ${widenedTpe <:< toArrayTpe}
      |==============================
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

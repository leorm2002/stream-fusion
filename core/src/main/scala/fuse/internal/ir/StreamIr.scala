package fuse.internal.ir

import scala.quoted.Quotes
import scala.quoted.Expr
import scala.quoted.Type
import scala.collection.mutable.ArrayBuilder
import fuse.internal.{CollectionStrategy}
// Alias the StreamIr to a type combined with singleton. this will guarantee no problem with path deendant type
type AnyIR = StreamIr & Singleton

/** Represents the phase of the item of the tree (see Phase-indexed fields in Trees that Grow Simon - Shayan Najd/Peyton Jones)
  */
private[internal] sealed trait Phase
private[internal] object Phase {
  final class Raw extends Phase
  final class Enriched extends Phase
}

/** Represents the mode of execution of a stream: sequential or parallel
  */
private[internal] enum ExecutionMode {
  case Sequential
  case Parallel
}

/** This try to represent the tree of a stream after parsing and before generation, it also act as a container for itmes that uses the path dependent quotes making the usage easier
  * across all the compiler
  *
  * @param quotes
  */
private[internal] class StreamIr(using val quotes: Quotes) {
  import quotes.reflect.*

  final case class TypedSymbol[T](symbol: Symbol)(using val valueType: Type[T]) {

    def ref: Expr[T] =
      Ref(symbol).asExprOf[T]
  }
  object TypedSymbol {
    def fromExpr[T: Type](expr: Expr[T]): TypedSymbol[T] = {
      val symbol = expr.asTerm.symbol

      if (symbol == Symbol.noSymbol) {
        report.errorAndAbort("Expected a symbol-backed expression")
      }

      if (!(symbol.termRef.widen =:= TypeRepr.of[T])) {
        report.errorAndAbort(s"Internal symbol type mismatch: expected ${Type.show[T]}, found ${symbol.termRef.widen.show}")
      }

      new TypedSymbol[T](symbol)
    }
  }

  enum Cardinality {
    case Exact(size: Expr[Int])
    case UpperBound(size: Expr[Int])
    case Unknown
  }

  extension (c: Cardinality) {
    def asUpperBound: Cardinality = c match {
      case Cardinality.Exact(size)    => Cardinality.UpperBound(size)
      case ub: Cardinality.UpperBound => ub
      case Cardinality.Unknown        => Cardinality.Unknown
    }
  }

  /** Phase indexed AST, abtained by the parsing phase */
  case class Ast[P <: Phase, A, R](
      stream: StreamTree[P, A],
      collectionStrategy: CollectionStrategy[A, R],
      prefixStatements: List[Statement],
      executionMode: ExecutionMode,
      fields: AstFields[P]
  )

  type AstFields[P <: Phase] = P match {
    case Phase.Raw      => Unit
    case Phase.Enriched => EnrichedAstFields
  }

  final case class EnrichedAstFields(
      declarations: List[Declaration],
      hasAlignedIndexes: Boolean,
      cardinality: Cardinality,
      earlyExitVar: Option[Expr[Boolean]],
      ref: List[Expr[Boolean]]
  )

  /** When present indicates that the node has a predecessor, only the root nodes are not WithUpstream */
  sealed trait WithUpstream[P <: Phase, A] {
    def upstream: StreamTree[P, A]
  }

  /** Represents a node of abstract syntax tree */
  enum StreamTree[P <: Phase, A] {
    def outType: Type[A]

    /** Represent a (java) List structure used as the source of element for the stream computation */
    case JListSource[P <: Phase, A](term: Expr[java.util.List[A]], outType: Type[A], fields: JListFields[P]) extends StreamTree[P, A]

    /** Represent an array structure used as the source of element for the stream computation */
    case ArraySource[P <: Phase, A](term: Expr[Array[A]], outType: Type[A], fields: ArraySourceFields[P]) extends StreamTree[P, A]

    /** Represent an iterable data structure used as the source of element for the stream computation */
    case IterableSource[P <: Phase, A](term: Expr[Iterable[A]], outType: Type[A]) extends StreamTree[P, A]

    /** Represent a (java) iterable data structure used as the source of element for the stream computation */
    case JIterableSource[P <: Phase, A](term: Expr[java.lang.Iterable[A]], outType: Type[A]) extends StreamTree[P, A]

    /** Represent a filter operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
    case Filter[P <: Phase, A](upstream: StreamTree[P, A], predicate: Expr[A => Boolean], outType: Type[A]) extends StreamTree[P, A] with WithUpstream[P, A]

    /** Represents a map operation X -> T and one to one cardinality */
    case Map[P <: Phase, A, B](upstream: StreamTree[P, A], function: Expr[A => B], inType: Type[A], outType: Type[B]) extends StreamTree[P, B] with WithUpstream[P, A]

    /** Represent a slice operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
    case Slice[P <: Phase, A](upstream: StreamTree[P, A], from: Option[Expr[Int]], until: Option[Expr[Int]], outType: Type[A], fields: SliceFields[P])
        extends StreamTree[P, A]
        with WithUpstream[P, A]

    /** A flat map operation
      *
      * @param upstream
      *   the stepd who precedes the flat map operation
      * @param innerStream
      *   the stream tree "contained" by the flatmap which will end up injected in parent loop
      * @param inType
      *   that type of element flowing into
      * @param outType
      *   that type of element flowing out
      * @param binder
      *   this is a variable generated at compile time, it binds the input of the inner stream without having access to the actual result of the previous step, see the code
      *   generation phase to see how it work
      * @param innerDeclarations
      *   the inner stream mey need to declare variables for operations like limit/skip, this will be injected in the flatmap loop condition
      */
    case FlatMap[P <: Phase, A, B](
        upstream: StreamTree[P, A],
        innerStream: StreamTree[P, B],
        inType: Type[A],
        outType: Type[B],
        binder: TypedSymbol[A],
        innerDeclarations: List[Statement],
        fields: FlatMapFields[P]
    ) extends StreamTree[P, B] with WithUpstream[P, A]

  }

  type JListFields[P <: Phase] = P match {
    case Phase.Raw      => Unit
    case Phase.Enriched => EnrichedJListFields
  }
  final case class EnrichedJListFields(
      sizeRef: Expr[Int]
  )

  type ArraySourceFields[P <: Phase] = P match {
    case Phase.Raw      => Unit
    case Phase.Enriched => EnrichedArraySourceFields
  }
  final case class EnrichedArraySourceFields(
      sizeRef: Expr[Int]
  )

  type SliceFields[P <: Phase] = P match {
    case Phase.Raw      => Unit
    case Phase.Enriched => EnrichedSliceFields
  }
  final case class EnrichedSliceFields(
      counterRefSymbol: TypedSymbol[Int]
  )

  type FlatMapFields[P <: Phase] = P match {
    case Phase.Raw      => Unit
    case Phase.Enriched => EnrichedFlatMapFields
  }
  final case class EnrichedFlatMapFields(
      innerMaterialized: List[Declaration],
      predicates: List[Expr[Boolean]]
  )

  /** These are used in all the phases of the compiler, here to simplify invocation */

  /** Creates an immutable binding while preserving a type obtained through reflection. */
  def createConstant[T: Type](name: String): TypedSymbol[T] = {
    TypedSymbol[T](Symbol.newVal(parent = Symbol.spliceOwner, name = Symbol.freshName(name), tpe = TypeRepr.of[T], flags = Flags.EmptyFlags, privateWithin = Symbol.noSymbol))
  }
  def createVariable[T: Type](name: String): TypedSymbol[T] = {
    // Mutable 'var'
    TypedSymbol[T](Symbol.newVal(parent = Symbol.spliceOwner, name = Symbol.freshName(name), tpe = TypeRepr.of[T], flags = Flags.Mutable, privateWithin = Symbol.noSymbol))
  }

  sealed trait Declaration {
    type T

    def symbol: TypedSymbol[T]
    def expr: Expr[T]
    def valueType: Type[T]
  }

  object Declaration {

    final case class Impl[A](symbol: TypedSymbol[A], expr: Expr[A]) extends Declaration {
      type T = A
      override val valueType: Type[A] = symbol.valueType
    }
  }

}

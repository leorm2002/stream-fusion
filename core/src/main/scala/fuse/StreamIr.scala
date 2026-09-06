package fuse

import scala.quoted.Quotes
import scala.quoted.Expr
import scala.quoted.Type
import scala.collection.mutable.ArrayBuilder

// Alias the StreamIr to a type combined with singleton. this will guarantee no problem with path deendant type
type AnyIR = StreamIr & Singleton

class StreamIr(using val quotes: Quotes) {
  import quotes.reflect.*

  /** Represents a node of abstract syntax treetree */
  sealed trait StreamTree[A] {
    def outType: Type[A]
  }

  /** When present indicates that the node has a predecessor, only the root nodes are not WithUpstream */
  trait WithUpstream[A]() {
    def upstream: StreamTree[A]
  }

  /** Represent a (java) List structure used as the source of element for the stream computation */
  final case class JListSource[A](term: Expr[java.util.List[A]], outType: Type[A]) extends StreamTree[A]

  /** Represent an iterable data structure used as the source of element for the stream computation */
  final case class IterableSource[A](term: Expr[Iterable[A]], outType: Type[A]) extends StreamTree[A]

  /** Represent a (java) iterable data structure used as the source of element for the stream computation */
  final case class JIterableSource[A](term: Expr[java.lang.Iterable[A]], outType: Type[A]) extends StreamTree[A]

  /** Represent an array structure used as the source of element for the stream computation */
  final case class ArraySource[A](term: Expr[Array[A]], outType: Type[A]) extends StreamTree[A]

  /** Represent a filter operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
  final case class Filter[A](upstream: StreamTree[A], predicate: Expr[A => Boolean], outType: Type[A]) extends StreamTree[A] with WithUpstream[A]

  /** Represents a map operation X -> T and one to one cardinality */
  final case class Map[A, B](upstream: StreamTree[A], function: Expr[A => B], inType: Type[A], outType: Type[B]) extends StreamTree[B] with WithUpstream[A]

  /** Represent a slice operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
  case class Slice[A](upstream: StreamTree[A], from: Option[Expr[Int]], until: Option[Expr[Int]], outType: Type[A]) extends StreamTree[A] with WithUpstream[A]

  /** Sealed hierarchy with the possible collection mode */
  sealed trait CollectionStrategy[A, Buf, R]

  /** This mode is derived from the opaque toArray collector, it does'nt actually have an associated collector, it instead works directly on an array for maximum performance */
  final case class ToArray[A]() extends CollectionStrategy[A, ArrayBuilder[A], Array[A]]

  /** This mode is the standard coellection method wich is based on an istance of a collector, may suffer from dynamic dispatch overhead depending by the jit */
  final case class WithCollector[A, Buf, R](collector: Expr[Collector[A, Buf, R]]) extends CollectionStrategy[A, Buf, R]

  /** The base AST, abtained by the parsing phase */
  case class Ast[A, Buf, R](parsedStream: StreamTree[A], collectionStrategy: CollectionStrategy[A, Buf, R], prefixStatements: List[Statement])

  /** A flat map operation
    *
    * @param upstream
    *   the stepd who precedes the flat map operation
    * @param innerTree
    *   the stream tree "contained" by the flatmap which will end up injected in parent loop
    * @param inType
    *   that type of element flowing into
    * @param outType
    *   that type of element flowing out
    * @param elemSymbol
    *   this is a variable generated at compile time, it binds the input of the inner stream without having access to the actual result of the previous step, see the code
    *   generation phase to see how it work
    * @param innerDeclarations
    *   the inner stream mey need to declare variables for operations like limit/skip, this will be injected in the flatmap loop condition
    */
  case class FlatMap[A, B](
      upstream: StreamTree[A],
      innerTree: StreamTree[B],
      inType: Type[A],
      outType: Type[B],
      elemSymbol: Symbol,
      innerDeclarations: List[Statement]
  ) extends StreamTree[B]
      with WithUpstream[A]

// ========================================================================================================================================================================
// ==============================================================    Enriched versions   ==================================================================================
// ========================================================================================================================================================================

  /** Represents parsed optimized and enriched stream
    */
  case class AstExt[A, Buf, R](
      val enrichedStream: StreamTree[A],
      val declarations: List[Statement],
      val collectionStrategy: EnrichedCollectionStrategy[A, Buf, R],
      val hasAlignedIndexes: Boolean,
      val hasKnownSourceSize: Boolean
  )

  /** Represents a limit operation
    *
    * @param upstream
    *   nodes of the streams who preceed the skip
    * @param count
    *   number of elements to skip
    * @param until
    *   number of elements to limit
    * @param outType
    *   output type of the skip (it's the same as the input)
    * @param counterRef
    *   reference the boolean variable dynamically created at compile time which singal to stop take elements
    */
  class EnrichedSlice[A](upstream: StreamTree[A], from: Option[Expr[Int]], until: Option[Expr[Int]], outType: Type[A], val counterRef: Expr[Int])
      extends Slice[A](upstream, from, until, outType)

  /** Represents a collector enriched with:
    * @param earlyExitVar
    *   the (optional) that signals when the collector has enough elements (es. findFirst collector)
    * @param ref
    *   eventual other vairable wich may be added to signal to exti (es. a limit clause)
    */
  case class EnrichedCollectionStrategy[A, Buf, R](collectionStrategy: CollectionStrategy[A, Buf, R], earlyExitVar: Option[Expr[Boolean]], ref: List[Expr[Boolean]])

final case class EnrichedJListSource[A](term: Expr[java.util.List[A]], sizeRef: Expr[Int], outType: Type[A]) extends StreamTree[A]

final case class EnrichedArraySource[A](term: Expr[Array[A]], sizeRef: Expr[Int], outType: Type[A]) extends StreamTree[A]

  /** Represents a flatMap enriched with the list of predicates injected from the outer stream and extracted from the inner one
    */
  class EnrichedFlatMap[A, B](
      upstream: StreamTree[A],
      innerTree: StreamTree[B], // AST dell'inner stream già arricchito
      inType: Type[A],
      outType: Type[B],
      elemSymbol: Symbol,
      innerDeclarations: List[Statement],
      val predicates: List[Expr[Boolean]]
  ) extends FlatMap[A, B](upstream, innerTree, inType, outType, elemSymbol, innerDeclarations)

  def createDef[T](symbol: Symbol, value: Int) = {
    ValDef(symbol, Some(Literal(IntConstant(value))))
  }
  def createDef[T](symbol: Symbol, value: Boolean) = {
    ValDef(symbol, Some(Literal(BooleanConstant(value))))
  }

  def createConstant[T: Type](name: String) = {
    Symbol.newVal(
      parent = Symbol.spliceOwner,
      name = Symbol.freshName(name),
      tpe = TypeRepr.of[T],
      flags = Flags.EmptyFlags,
      privateWithin = Symbol.noSymbol
    )
  }

  def createVariable[T: Type](name: String) = {
    Symbol.newVal(
      parent = Symbol.spliceOwner,
      name = Symbol.freshName(name),
      tpe = TypeRepr.of[T],
      flags = Flags.Mutable, // Mutable 'var'
      privateWithin = Symbol.noSymbol
    )
  }
}

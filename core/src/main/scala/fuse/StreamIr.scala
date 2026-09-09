package fuse

import scala.quoted.Quotes
import scala.quoted.Expr
import scala.quoted.Type
import scala.collection.mutable.ArrayBuilder

// Alias the StreamIr to a type combined with singleton. this will guarantee no problem with path deendant type
type AnyIR = StreamIr & Singleton

/** This try to represent the tree of a stream after parsing and before generation, it also act as a container for itmes that uses the path dependent quotes making the usage easier
  * across all the compiler
  *
  * @param quotes
  */
class StreamIr(using val quotes: Quotes) {
  import quotes.reflect.*

  /** Represents the phase of the item of the tree (see Phase-indexed fields in Trees that Grow Simon - Shayan Najd/Peyton Jones)
    */
  sealed trait Phase
  object Phase {
    sealed trait Raw extends Phase
    sealed trait Enriched extends Phase
  }

  /** The base AST, abtained by the parsing phase */
  case class Ast[A, Buf, R](parsedStream: StreamTree[Phase.Raw, A], collectionStrategy: CollectionStrategy[A, Buf, R], prefixStatements: List[Statement])

  /** Represents parsed optimized and enriched stream
    */
  case class AstExt[A, Buf, R](
      val enrichedStream: StreamTree[Phase.Enriched, A],
      val declarations: List[Statement],
      val collectionStrategy: EnrichedCollectionStrategy[A, Buf, R],
      val hasAlignedIndexes: Boolean,
      val hasKnownSourceSize: Boolean
  )

  /** When present indicates that the node has a predecessor, only the root nodes are not WithUpstream */
  trait WithUpstream[P <: Phase, A] {
    def upstream: StreamTree[P, A]
  }

  /** Represents a node of abstract syntax treetree */
  enum StreamTree[P <: Phase, A] {
    def outType: Type[A]

    /** Represent a (java) List structure used as the source of element for the stream computation */
    case JListSource[A](term: Expr[java.util.List[A]], outType: Type[A]) extends StreamTree[Phase.Raw, A]

    case EnrichedJListSource[A](term: Expr[java.util.List[A]], sizeRef: Expr[Int], outType: Type[A]) extends StreamTree[Phase.Enriched, A]

      /** Represent an array structure used as the source of element for the stream computation */
    case ArraySource[A](term: Expr[Array[A]], outType: Type[A]) extends StreamTree[Phase.Raw, A]

    case EnrichedArraySource[A](term: Expr[Array[A]], sizeRef: Expr[Int], outType: Type[A]) extends StreamTree[Phase.Enriched, A]

      /** Represent an iterable data structure used as the source of element for the stream computation */
    case IterableSource[P <: Phase, A](term: Expr[Iterable[A]], outType: Type[A]) extends StreamTree[P, A]

    /** Represent a (java) iterable data structure used as the source of element for the stream computation */
    case JIterableSource[P <: Phase, A](term: Expr[java.lang.Iterable[A]], outType: Type[A]) extends StreamTree[P, A]

    /** Represent a filter operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
    case Filter[P <: Phase, A](upstream: StreamTree[P, A], predicate: Expr[A => Boolean], outType: Type[A]) extends StreamTree[P, A] with WithUpstream[P, A]

    /** Represents a map operation X -> T and one to one cardinality */
    case Map[P <: Phase, A, B](upstream: StreamTree[P, A], function: Expr[A => B], inType: Type[A], outType: Type[B]) extends StreamTree[P, B] with WithUpstream[P, A]

    /** Represent a slice operation namely an operation X -> X typewise and  one to one or zero regarding cardinality */
    case Slice[A](upstream: StreamTree[Phase.Raw, A], from: Option[Expr[Int]], until: Option[Expr[Int]], outType: Type[A])
        extends StreamTree[Phase.Raw, A]
        with WithUpstream[Phase.Raw, A]

    case EnrichedSlice[A](upstream: StreamTree[Phase.Enriched, A], from: Option[Expr[Int]], until: Option[Expr[Int]], outType: Type[A], val counterRef: Expr[Int])
        extends StreamTree[Phase.Enriched, A]
        with WithUpstream[Phase.Enriched, A]

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
    case FlatMap[A, B](
        upstream: StreamTree[Phase.Raw, A],
        innerTree: StreamTree[Phase.Raw, B],
        inType: Type[A],
        outType: Type[B],
        elemSymbol: Symbol,
        innerDeclarations: List[Statement]
    ) extends StreamTree[Phase.Raw, B] with WithUpstream[Phase.Raw, A]

    /** Represents a flatMap enriched with the list of predicates injected from the outer stream and extracted from the inner one
      */
    case EnrichedFlatMap[A, B](
        upstream: StreamTree[Phase.Enriched, A],
        innerTree: StreamTree[Phase.Enriched, B], // AST dell'inner stream già arricchito
        inType: Type[A],
        outType: Type[B],
        elemSymbol: Symbol,
        innerDeclarations: List[Statement],
        val predicates: List[Expr[Boolean]]
    ) extends StreamTree[Phase.Enriched, B] with WithUpstream[Phase.Enriched, A]
  }

  /** These are used in all the phases of the compiler, here to simplify invocation */
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

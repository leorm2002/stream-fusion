package fuse

  import scala.collection.mutable.ArrayBuilder
  import scala.quoted.Expr

  /** Sealed hierarchy with the possible collection mode */
  enum CollectionStrategy[A, Buf, R]{

    /** This mode is derived from the opaque toArray collector, it does'nt actually have an associated collector, it instead works directly on an array for maximum performance */
    case ToArray[A]() extends CollectionStrategy[A, ArrayBuilder[A], Array[A]]

    case Summing[A]() extends CollectionStrategy[A, Any, A]

    /** This mode is the standard coellection method wich is based on an istance of a collector, may suffer from dynamic dispatch overhead depending by the jit */
    case WithCollector[A, Buf, R](collector: Expr[Collector[A, Buf, R]]) extends CollectionStrategy[A, Buf, R]
}


  /** Represents a collector enriched with:
    * @param earlyExitVar
    *   the (optional) that signals when the collector has enough elements (es. findFirst collector)
    * @param ref
    *   eventual other vairable wich may be added to signal to exti (es. a limit clause)
    */
  // We do not use Phase-indexed fields here, this is an extension shared across all cases
  case class EnrichedCollectionStrategy[A, Buf, R](collectionStrategy: CollectionStrategy[A, Buf, R], earlyExitVar: Option[Expr[Boolean]], ref: List[Expr[Boolean]])

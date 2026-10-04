package fuse.internal

import scala.collection.mutable.ArrayBuilder
import scala.quoted.Expr
import fuse.Summable
import fuse.CollectorBase
import scala.quoted.Type

private sealed trait CollectionStrategy[A, R]

/** Sealed hierarchy of the possible collection modes */
private object CollectionStrategy {

  /** This mode is derived from the opaque toArray collector, it doesn't actually have an associated collector, it instead works directly on an array for maximum performance */
  final case class ToArray[A]() extends CollectionStrategy[A, Array[A]]

  final case class Summing[A <: Summable]() extends CollectionStrategy[A, A]

  /** This mode is the standard coellection method wich is based on an istance of a collector, may suffer from dynamic dispatch overhead depending by the jit */
  final case class WithCollector[A, Buf, R](collector: Expr[CollectorBase[A, Buf, R]], isEarlyStopping: Boolean)(using
      val bufType: Type[Buf] // We must mantain the evidence to use it when we generate the code
  ) extends CollectionStrategy[A, R]
}

/** Represents a collector enriched with:
  * @param earlyExitVar
  *   the (optional) that signals when the collector has enough elements (es. findFirst collector)
  * @param ref
  *   eventual other vairable wich may be added to signal to exti (es. a limit clause)
  */
// We do not use Phase-indexed fields here, this is an extension shared across all cases
private case class EnrichedCollectionStrategy[A, R](collectionStrategy: CollectionStrategy[A, R], earlyExitVar: Option[Expr[Boolean]], ref: List[Expr[Boolean]])

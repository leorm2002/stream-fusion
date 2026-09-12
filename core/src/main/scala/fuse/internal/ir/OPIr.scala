package fuse.internal.ir

import scala.quoted.*
import fuse.internal.ir.AnyIR
import fuse.CollectorBase
import fuse.Summable

// Preserve the identity of the operation IR across lowering and code generation.
type AnyOPIR = OPIr[?] & Singleton

/** Contains the operation data model, following the instance-bound types used by StreamIr. Sharing its Quotes instance keeps stream symbols and statements usable in both
  * generation phases.
  */
private[internal] class OPIr[IR <: AnyIR](val streamIr: IR) {
  val quotes: streamIr.quotes.type = streamIr.quotes
  private given macroQuotes: quotes.type = quotes

  import quotes.reflect.*

  enum Op {

    case ExternalStatement(statement: Statement)

    case CodeBlock(ops: List[Op])

    case Declare[T](symbol: Symbol, initialValue: Value[T])

    case AssignVal[T](symbol: Symbol, value: Value[T])

    case Inc(symbol: Symbol)

    case If(condition: Value[Boolean], thenBody: Op, elseBody: Option[Op] = None)

    case While(condition: Value[Boolean], body: Op)

    case Compute(value: Value[?])

    case ArrayWrite[T](array: Value[Array[T]], index: Value[Int], value: Value[T])(using val valueType: Type[T]) extends Op

    case DynamicArrayBuilderAdd[A](builder: Value[DynamicArrayBuilder[A]], elem: Value[A])(using val elemType: Type[A]) extends Op

  }

  sealed trait Value[T] {

    /** @return
      *   the type of the value
      */
    def valueType: Type[T]
  }

  final case class LessThan(left: Value[Int], right: Value[Int]) extends Value[Boolean] { override val valueType: Type[Boolean] = Type.of[Boolean] }

  final case class GreaterThanOrEqual(left: Value[Int], right: Value[Int]) extends Value[Boolean] { override val valueType: Type[Boolean] = Type.of[Boolean] }

  final case class Equal[T](left: Value[T], right: Value[T]) extends Value[Boolean] { override val valueType: Type[Boolean] = Type.of[Boolean] }

  final case class And(left: Value[Boolean], right: Value[Boolean]) extends Value[Boolean] { override val valueType: Type[Boolean] = Type.of[Boolean] }

  final case class IfValue[T](condition: Value[Boolean], thenValue: Value[T], elseValue: Value[T]) extends Value[T] {
    override val valueType: Type[T] = thenValue.valueType
  }

  final case class IsInstanceOf[A](value: Value[?])(using val testedType: Type[A]) extends Value[Boolean] {
    override val valueType: Type[Boolean] = Type.of[Boolean]
  }

  final case class AsInstanceOf[A](value: Value[?])(using val valueType: Type[A]) extends Value[A]

  final case class ScalaExpr[T](expr: Expr[T])(using val valueType: Type[T]) extends Value[T]

  final case class SymbolRef[T](symbol: Symbol)(using val valueType: Type[T]) extends Value[T]

  final case class ConstantVal[T] private (expr: Expr[T])(using val valueType: Type[T]) extends Value[T]

  object ConstantVal {
    def apply[T: Type: ToExpr](value: T): ConstantVal[T] = ConstantVal(Expr(value))
  }

  final case class ArrayDefine[T](size: Value[Int])(using val elemType: Type[T]) extends Value[Array[T]] {
    override val valueType: Type[Array[T]] = Type.of[Array[T]]
  }

  final case class ArrayRead[T](array: Value[Array[T]], index: Value[Int])(using val valueType: Type[T]) extends Value[T]

  object ArrayRead {
    def apply[T: Type](array: Expr[Array[T]], index: Value[Int]): ArrayRead[T] = new ArrayRead[T](ScalaExpr(array), index)
    def apply[T: Type](array: Expr[Array[T]], index: Symbol): ArrayRead[T] = new ArrayRead[T](ScalaExpr(array), SymbolRef[Int](index))
  }

  final case class ArrayLength[A](array: Value[Array[A]])(using val elemType: Type[A]) extends Value[Int] {
    override val valueType: Type[Int] = Type.of[Int]
  }

  final case class ArrayTake[A](array: Value[Array[A]], count: Value[Int])(using val elemType: Type[A]) extends Value[Array[A]] {
    override val valueType: Type[Array[A]] = Type.of[Array[A]]
  }

  final case class JListRead[A](list: Value[java.util.List[A]], index: Value[Int])(using val valueType: Type[A]) extends Value[A]

  final case class ArrayListRawArray[A](list: Value[java.util.List[A]])(using val elemType: Type[A]) extends Value[Array[AnyRef]] {
    override val valueType: Type[Array[AnyRef]] = Type.of[Array[AnyRef]]
  }

  // Keep native iterator types in the IR so Java sources need no Scala iterator adapter.
  final case class IteratorOf[A](source: Value[Iterable[A]])(using val elemType: Type[A]) extends Value[Iterator[A]] {
    override val valueType: Type[Iterator[A]] = Type.of[Iterator[A]]
  }

  final case class HasNext[A](iterator: Value[Iterator[A]])(using val elemType: Type[A]) extends Value[Boolean] {
    override val valueType: Type[Boolean] = Type.of[Boolean]
  }

  final case class Next[A](iterator: Value[Iterator[A]])(using val valueType: Type[A]) extends Value[A]

  final case class JIteratorOf[A](source: Value[java.lang.Iterable[A]])(using val elemType: Type[A]) extends Value[java.util.Iterator[A]] {
    override val valueType: Type[java.util.Iterator[A]] = Type.of[java.util.Iterator[A]]
  }

  final case class JHasNext[A](iterator: Value[java.util.Iterator[A]])(using val elemType: Type[A]) extends Value[Boolean] {
    override val valueType: Type[Boolean] = Type.of[Boolean]
  }

  final case class JNext[A](iterator: Value[java.util.Iterator[A]])(using val valueType: Type[A]) extends Value[A]

  type DynamicArrayBuilder[A] = scala.collection.mutable.ArrayBuffer[A]

  final case class DynamicArrayBuilderNew[A]()(using val elemType: Type[A]) extends Value[DynamicArrayBuilder[A]] {
    override val valueType: Type[DynamicArrayBuilder[A]] = Type.of[DynamicArrayBuilder[A]]
  }

  final case class DynamicArrayBuilderResult[A](builder: Value[DynamicArrayBuilder[A]])(using val elemType: Type[A]) extends Value[Array[A]] {
    override val valueType: Type[Array[A]] = Type.of[Array[A]]
  }

  final case class CollectorSupplier[A, Buf, R](
      collector: Value[CollectorBase[A, Buf, R]]
  )(using val elemType: Type[A], val valueType: Type[Buf], val resultType: Type[R])
      extends Value[Buf]

  final case class CollectorAccumulate[A, Buf, R](collector: Value[CollectorBase[A, Buf, R]], buffer: Value[Buf], elem: Value[A])(using
      val elemType: Type[A],
      val bufferType: Type[Buf],
      val resultType: Type[R]
  ) extends Value[Boolean] {
    override val valueType: Type[Boolean] =
      Type.of[Boolean]
  }
  final case class CollectorFinish[A, Buf, R](collector: Value[CollectorBase[A, Buf, R]], buffer: Value[Buf])(using
      val elemType: Type[A],
      val bufferType: Type[Buf],
      val valueType: Type[R]
  ) extends Value[R]

  final case class Add[T <: Summable](left: Value[T], right: Value[T])(using val valueType: Type[T]) extends Value[T]
  object Add {
    def apply[T <: Summable: Type](left: Symbol, right: Value[T]): Add[T] = new Add[T](SymbolRef[T](left), right)
  }

  final case class ApplyFun[IN, OUT](function: Expr[IN => OUT], argument: Value[IN])(using val inType: Type[IN], val valueType: Type[OUT]) extends Value[OUT]

  final case class Program[T](statements: List[Op], result: Value[T])
}

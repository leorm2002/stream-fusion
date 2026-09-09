package fuse
import java.lang.invoke.{MethodHandles, VarHandle}
import java.util.ArrayList

/**
  * Utility to access the backing array of an array list
  */
object ArrayListAccessor {
  // Get resolved once the jvm is started and have no overhead after that
  inline def getRawArray(list: ArrayList[?]): Array[AnyRef] = ElementDataHandle.get(list).asInstanceOf[Array[AnyRef]]

  private val ElementDataHandle: VarHandle = {
    val lookup = MethodHandles.privateLookupIn(classOf[ArrayList[?]], MethodHandles.lookup())
    lookup.findVarHandle(classOf[ArrayList[?]], "elementData", classOf[Array[AnyRef]])
  }
}
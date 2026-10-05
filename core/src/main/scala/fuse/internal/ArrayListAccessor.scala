package fuse.internal
import java.lang.invoke.{MethodHandles, VarHandle}
import java.util.ArrayList

/** Utility to access the backing array of an array list
  */
object ArrayListAccessor {
  // Get resolved once the jvm is started and have no overhead after that
  inline def getRawArray(list: ArrayList[?]): Array[AnyRef] = ElementDataHandle.get(list).asInstanceOf[Array[AnyRef]]

  private val ElementDataHandle: VarHandle = {
    try {
      val lookup = MethodHandles.privateLookupIn(classOf[ArrayList[?]], MethodHandles.lookup())
      lookup.findVarHandle(classOf[ArrayList[?]], "elementData", classOf[Array[AnyRef]])
    } catch {
      case ex: IllegalAccessException =>
        throw new IllegalStateException(
          "Accessing ArrayList's backing array with useUnsafe = true requires opening java.util to unnamed modules. " +
            "Please add the JVM option: --add-opens java.base/java.util=ALL-UNNAMED",
          ex
        )
      case ex: Throwable =>
        throw new IllegalStateException(
          "Failed to access ArrayList's backing array with useUnsafe = true. " +
            "Please ensure JVM option '--add-opens java.base/java.util=ALL-UNNAMED' is set.",
          ex
        )
    }
  }
}

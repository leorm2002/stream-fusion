package fuse
import java.lang.invoke.{MethodHandles, VarHandle}
import java.util.ArrayList

object ArrayListAccessor {
  // Risolto una volta sola all'avvio della JVM (zero overhead successivo)
  inline def getRawArray(list: ArrayList[?]): Array[AnyRef] = ElementDataHandle.get(list).asInstanceOf[Array[AnyRef]]
  
  private val ElementDataHandle: VarHandle = {
    val lookup = MethodHandles.privateLookupIn(classOf[ArrayList[?]], MethodHandles.lookup())
    lookup.findVarHandle(classOf[ArrayList[?]], "elementData", classOf[Array[AnyRef]])
  }
}
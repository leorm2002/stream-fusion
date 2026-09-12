package fuse
import munit.FunSuite
import scala.quoted.Type
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.*
import scala.quoted.staging.*
import fuse.{FusedStream, Collector}
import FusedStream.*
import scala.collection.mutable.ListBuffer

class ParserTest extends FunSuite {
  given Compiler = Compiler.make(getClass.getClassLoader)
  test("Parser analizza correttamente la catena stream.map(...).filter(...)") {
    withQuotes {
      val ir = new StreamIr()
      val parser = Parser(ir) // Assumendo che Parser accetti (ir)

      // 1. Creiamo un'espressione quote che simula la catena stream.map(...).filter(...)
      val mockStreamExpr: Expr[Stream[String]] = '{
        FusedStream
          .of(2)
          .map((x: Int) => x * 2) // Step 1: Map
          .filter((z: Int) => z > 5) // Step 2: Filter
          .map((z: Int) => s"Risultato: $z") // Step 3: Altra Map
          .map((z: String) => s"Risultato: $z") // Step 4: Altra Map
      }

      val mockCollector: Expr[Collector[String, ListBuffer[String], List[String], NoEarlyStopping]] = '{ Collector.toList[String] }
      // 2. Chiamata al Parser
      val astResult = parser.parseExpression[String, ListBuffer[String], List[String], NoEarlyStopping](mockStreamExpr, mockCollector, ExecutionMode.Sequential)
      def getPrevious(arg0: parser.ir.StreamTree[?, ?]) = {
        arg0 match {
          case p: parser.ir.WithUpstream[?, ?] => p.upstream
          case _                               => null
        }
      }
      assert(astResult != null)

      // Map
      assertEquals(astResult.parsedStream.getClass().getName(), "fuse.StreamIr$StreamTree$Map")

      // Map
      var prev = getPrevious(astResult.parsedStream)
      assertEquals(prev.getClass().getName(), "fuse.StreamIr$StreamTree$Map")

      // Filter
      prev = getPrevious(prev)
      assertEquals(prev.getClass().getName(), "fuse.StreamIr$StreamTree$Filter")

      // Map
      prev = getPrevious(prev)
      assertEquals(prev.getClass().getName(), "fuse.StreamIr$StreamTree$Map")
    }
  }

}

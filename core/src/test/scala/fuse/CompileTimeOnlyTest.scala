package fuse

import munit.FunSuite
import scala.quoted.staging.*

class CompileTimeOnlyTest extends FunSuite {
  given Compiler = Compiler.make(getClass.getClassLoader)

  private def messages(error: Throwable): List[String] = {
    Option(error.getMessage).toList ::: Option(error.getCause).toList.flatMap(messages)
  }

  test("Iterable source cannot escape an uncollected pipeline") {
    val error = intercept[Exception] {
      run { '{ FusedStream.from(List(1, 2, 3)).map(_ * 2).filter(_ > 2) } }
    }

    assert(messages(error).exists(_.contains("FusedStream.from can only be used in a pipeline terminated by .collect(...)")))
  }

  test("Array source cannot escape an uncollected pipeline") {
    val error = intercept[Exception] {
      run { '{ FusedStream.from(Array(1, 2, 3)) } }
    }

    assert(messages(error).exists(_.contains("FusedStream.from can only be used in a pipeline terminated by .collect(...)")))
  }

  test("Single-element source cannot escape an uncollected pipeline") {
    val error = intercept[Exception] {
      run { '{ FusedStream.of(1) } }
    }

    assert(messages(error).exists(_.contains("FusedStream.of can only be used in a pipeline terminated by .collect(...)")))
  }
}

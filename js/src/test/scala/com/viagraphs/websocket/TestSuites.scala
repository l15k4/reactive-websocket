package com.viagraphs.websocket

import utest.{DefaultFormatter, _}
import utest.framework.{Test, TestSuite}
import utest.util.Tree

import scala.scalajs.concurrent.JSExecutionContext
import scala.util.{Failure, Success}

abstract class TestSuites extends TestSuite {

  def print(tests: Tree[Test], results: Tree[utest.framework.Result]): Unit = {
    println(new DefaultFormatter().format(results))

    val testLeaves = tests.leaves.toList
    val resultLeaves = results.leaves.toList
    val testLeavesCount = testLeaves.length
    val resultLeavesCount = resultLeaves.length
    val successResults = resultLeaves.filter(_.value.isSuccess)
    val failedResults = resultLeaves.filter(_.value.isFailure)

    assert(testLeavesCount == resultLeavesCount)

    println("Total test count " + testLeavesCount)
    println("Total result count " + resultLeavesCount)
    println("Passed test count " + successResults.length)
    println("Failed test count " + failedResults.length)
  }

  def tests = TestSuite {

    "clientSuite" - {
      implicit val qex = JSExecutionContext.queue
      WebSocketClientSuite.integrationTests.runAsync().onComplete {
        case Failure(ex) =>
          println(ex)
          System.exit(0)
        case Success(results) =>
          print(WebSocketClientSuite.integrationTests, results)
          System.exit(0)
      }
    }
  }
}
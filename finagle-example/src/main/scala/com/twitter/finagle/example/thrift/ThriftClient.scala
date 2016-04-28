package com.twitter.finagle.example.thrift

import com.twitter.finagle.example.thriftscala.Hello
import com.twitter.finagle.Thrift

import scala.io.StdIn

object ThriftClient {
  def main(args: Array[String]) {
    //#thriftclientapi
    val client = Thrift.newIface[Hello.FutureIface]("localhost:7079")
    println("client main init - ")
    client.hi().onSuccess { response =>
      println("Received response: " + response)
    }

    val cmd = new Thread {
      while(true) {
        println(s"input message:")
        val line = StdIn.readLine()
        line match {
          case "quit" =>
            client.close()
            System.exit(0)
          case _ =>
            println("Unknow Cmd")
        }
      }
    }
    cmd.start()

    //#thriftclientapi
  }
}

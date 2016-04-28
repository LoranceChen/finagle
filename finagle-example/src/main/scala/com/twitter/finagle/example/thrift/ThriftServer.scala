package com.twitter.finagle.example.thrift

import com.twitter.finagle.example.thriftscala.Hello
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future}

import scala.io.StdIn

object ThriftServer {
  def main(args: Array[String]) {
    //#thriftserverapi
    println("main main init - ")
    val server = Thrift.serveIface("localhost:7079", new Hello[Future] {
      def hi() = {
        println("server rsp hi - ")
        Future.value("hi")
      }
    })

    val cmd = new Thread {
      while(true) {
        println(s"input message:")
        val line = StdIn.readLine()
        line match {
          case "quit" =>
            server.close()
            System.exit(0)
          case _ =>
            println("Unknow Cmd")
        }
      }
    }
    cmd.start()

    Await.ready(server)
    //#thriftserverapi

  }
}

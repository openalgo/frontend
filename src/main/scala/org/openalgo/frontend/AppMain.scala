package org.openalgo.frontend

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.openalgo.frontend.analysis.SimpleAnalysis._


object AppMain extends App {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val staticRoute =
    path("") {
      getFromResource("web/index.html")
    } ~ {
      getFromResourceDirectory("web")
    }

  val stockDataRoute =
    path("stockPrices") {
      get {
        parameterMap({ params =>
          complete {
            getMeCovariance(params.get("ticker1").get, params.get("ticker2").get)
          }
        })
      }
    }


  val routes = staticRoute ~ stockDataRoute

  val bindingFuture = Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

  println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
  Console.readLine() // for the future transformations
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ ⇒ system.terminate()) // and shutdown when done
}

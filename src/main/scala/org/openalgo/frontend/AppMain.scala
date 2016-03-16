package org.openalgo.frontend

import java.io.IOException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import argonaut.Argonaut._
import argonaut.{EncodeJson, Json, _}
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import org.openalgo.frontend.model.StockData

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object AppMain extends App with ArgonautSupport {
  implicit def StockDataJsonEncode: EncodeJson[StockData] =
    EncodeJson((p: StockData) => ("startDate" := p.startDate) ->: ("data" := p.data) ->: ("columnNames" := p.columnNames) ->: ("endDate" := p.endDate) ->: jEmptyObject)

  implicit def PersonDecodeJson: DecodeJson[StockData] =
    DecodeJson(c => for {
      startDate <- (c --\ "start_date").as[String]
      data <- (c --\ "data").as[mutable.Buffer[Json]]
      columnNames <- (c --\ "column_names").as[mutable.Buffer[String]]
      endDate <- (c --\ "end_date").as[String]
    } yield StockData(startDate, data, columnNames, endDate))


  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val config = ConfigFactory.load()

  lazy val historicalDataConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.historicalDataHost"), config.getInt("services.historicalDataPort"))

  def quandlRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(historicalDataConnectionFlow).runWith(Sink.head)


  def fetchStockData(ticker: String): Future[Either[String, StockData]] = {
    quandlRequest(RequestBuilding.Get(s"/stocks?ticker=$ticker")).flatMap { response =>
      response.status match {
        case OK => {
          Unmarshal(response.entity).to[StockData].map(Right(_))
        }
        case BadRequest => Future.successful(Left(s"$ticker: incorrect IP format"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Historical Data request failed with status code ${response.status} and entity $entity"
          Future.failed(new IOException(error))
        }
      }
    }
  }


  val staticRoute =
    path("") {
      getFromResource("web/index.html")
    } ~ {
      getFromResourceDirectory("web")
    }

  val stockDataRoute =
    path("stockPrices") {
      get {
        parameters('ticker) { (ticker) =>
          complete {
            val ret = Await.result(fetchStockData(ticker), 10000 millis)
            ret match {
              case Right(json) => {
                json
              }
              case Left(str) => {
                str
              }
            }
          }
        }
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

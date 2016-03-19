package org.openalgo.frontend.dataaccess

import java.io.IOException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import argonaut.Argonaut._
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import scala.util.{Success, Failure}


object QuandlDAO extends ArgonautSupport {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val config = ConfigFactory.load()

  private val DATE_FIELD = "date"
  private val HIGH_FIELD = "high"
  private val LOW_FIELD = "low"
  private val OPEN_FIELD = "open"
  private val CLOSE_FIELD = "close"
  private val PRICE_DATA_FIELD = "priceData"

  private val DATE_FIELD_CAP = "Date"
  private val HIGH_FIELD_CAP = "High"
  private val LOW_FIELD_CAP = "Low"
  private val OPEN_FIELD_CAP = "Open"
  private val CLOSE_FIELD_CAP = "Close"

  case class PriceData(date: String, high: Double, low: Double, open: Double, close: Double)

  case class QuandlData(priceData: List[PriceData])

  implicit def PriceDataCodecJson =
    casecodec5(PriceData.apply, PriceData.unapply)(DATE_FIELD, HIGH_FIELD, LOW_FIELD, OPEN_FIELD, CLOSE_FIELD)

  implicit def QuandlDataCodecJson =
    casecodec1(QuandlData.apply, QuandlData.unapply)(PRICE_DATA_FIELD)

  lazy val historicalDataConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.historicalDataHost"), config.getInt("services.historicalDataPort"))

  def quandlRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(historicalDataConnectionFlow).runWith(Sink.head)

  def fetchStockData(ticker: String): Future[QuandlData] = {
    quandlRequest(RequestBuilding.Get(s"/stocks?ticker=$ticker")).flatMap { response =>
      response.status match {
        case OK => {
          Unmarshal(response.entity).to[QuandlData]
        }
        case BadRequest => throw new Exception("test")
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Historical Data request failed with status code ${response.status} and entity $entity"
          Future.failed(new IOException(error))
        }
      }
    }
  }

  def pullTwoStockData(ticker1:String, ticker2:String): List[QuandlData] = {
    val ret1 = fetchStockData(ticker1)
    val ret2 = fetchStockData(ticker2)
    val comRet = for {
      ret1 <- ret1.mapTo[QuandlData]
      ret2 <- ret2.mapTo[QuandlData]
      ret3 <- Future(List(ret1, ret2))
    } yield ret3

    Await.result(comRet, 30000 millis)
  }
}

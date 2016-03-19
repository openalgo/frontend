package org.openalgo.frontend.analysis

import java.io.IOException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import argonaut.Argonaut._
import argonaut.{Json, Parse}
import com.typesafe.config.ConfigFactory
import org.openalgo.frontend.dataaccess.QuandlDAO.{PriceData, QuandlData}

import scala.collection.mutable
import scala.concurrent.Future

// Implicit imports double unmarshaller for transformation
import akka.http.scaladsl.unmarshalling.Unmarshaller._


object AnalysisConnect {
  case class CovarianceInput(series1: mutable.Buffer[Double], series2: mutable.Buffer[Double])
  implicit def CovarianceInputCodecJson =
    casecodec2(CovarianceInput.apply, CovarianceInput.unapply)("series1", "series2")

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val config = ConfigFactory.load()

  lazy val analyticsConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.analyticsHost"), config.getInt("services.analyticsPort"))

  def analyticsRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(analyticsConnectionFlow).runWith(Sink.head)

  def fetchCovariance(priceData: List[QuandlData], max:Int = 100): Future[String] = {
    val request = RequestBuilding.Post(s"/covariance")
      .withEntity(HttpEntity(ContentTypes.`application/json`, priceDataListtoBuf(priceData, max).asJson.toString())
      )

    analyticsRequest(request).flatMap { response =>
      response.status match {
        case OK => {
          Unmarshal(response.entity).to[String]
        }
        case BadRequest => throw new Exception("test")
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Historical Data request failed with status code ${response.status} and entity $entity"
          Future.failed(new IOException(error))
        }
      }
    }
  }

  def priceDataListtoBuf(priceData: List[QuandlData], max:Int): CovarianceInput = {
    val priceList1 = priceData.head.priceData
    val priceList2 = priceData.tail.head.priceData
    val listMax = Math.min(priceList1.size, priceList2.size)
    val mutableBuf1: mutable.Buffer[Double] = mutable.Buffer()
    val mutableBuf2: mutable.Buffer[Double] = mutable.Buffer()
    var realMax = max
    if(listMax < max) {
      realMax = listMax
    }
    for(priceData <- priceList1.slice(0, realMax)){
      mutableBuf1 += ((priceData.close / priceData.open) /priceData.open)
    }

    for(priceData <- priceList2.slice(0, realMax)){
      mutableBuf2 += ((priceData.close / priceData.open) /priceData.open)
    }

    CovarianceInput(mutableBuf1, mutableBuf2)
  }
}
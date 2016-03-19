package org.openalgo.frontend.analysis

import org.openalgo.frontend.dataaccess.QuandlDAO._
import org.openalgo.frontend.analysis.AnalysisConnect._


import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.Await

object SimpleAnalysis {
  def getMeCovariance(ticker1: String, ticker2:String):String = {
    Await.result(fetchCovariance(pullTwoStockData(ticker1, ticker2)), 30000 millis)
  }
}

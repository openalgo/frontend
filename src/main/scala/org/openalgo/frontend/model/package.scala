package org.openalgo.frontend

import argonaut.Json

import scala.collection.mutable

package object model {

  // stockPrices
  case class StockData(startDate: String, data: mutable.Buffer[Json],
                       columnNames: mutable.Buffer[String], endDate: String)

}

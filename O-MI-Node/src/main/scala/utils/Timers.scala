package utils

import collection.mutable.ArrayBuffer

case class LapTimer(logFun: String => Unit) {
  val s = System.currentTimeMillis()
  val laps = new ArrayBuffer[(String, Double)]()

  var m = s

  def realtimeStep(message: String) = {
    val e = System.currentTimeMillis()
    val time = (e - m).toDouble / 1000
    logFun(message + s": $time seconds")
    m = e
  }

  def step(message: String) = {
    val e = System.currentTimeMillis()
    val time = (e - m).toDouble / 1000
    logFun(message + s": $time seconds")
    laps += ((message, time))
    m = e
  }

  def total(message: String = "TOTAL") = {
    val e = System.currentTimeMillis()
    val total = (e - s).toDouble / 1000
    for ((m, t) <- laps)
      logFun(f"${t/total*100}%3.0f %% $t%10.5f seconds - " + m)
    logFun(message + s": $total seconds")
    m = e
  }
}

object SingleTimer {
  def apply[T](action: => T, logFun: String => Unit, message: String): T = {
    val s = System.currentTimeMillis()
    val r = action
    val e = System.currentTimeMillis()
    val time = (e - s).toDouble / 1000
    logFun(message + s": $time seconds")
    r
  }
}

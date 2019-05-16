package io.casperlabs.sim.simulation_framework

/**
  * Represents a point of the simulated time.
  * This is internally the number of (virtual) microseconds elapsed since the simulation started.
  */
case class Timepoint(micros: Long) extends AnyVal with Ordered[Timepoint] {

  override def compare(that: Timepoint): Int = math.signum(micros - that.micros).toInt

  def +(delta: TimeDelta): Timepoint = Timepoint(micros + delta)

  def -(other: Timepoint): TimeDelta = this.micros - other.micros

  override def toString: String = {
    var s = micros.toString
    if (s.length < 7)
      s = s.reverse.padTo(7, '0').reverse
    return s.dropRight(6) + "." + s.takeRight(6)
  }
}

object Timepoint {
  val zero: Timepoint = Timepoint(0L)
  def millis(n: Long): Timepoint = Timepoint(n * 1000)
  def seconds(n: Long): Timepoint = Timepoint(n * 1000000)
  def minutes(n: Long): Timepoint = Timepoint(n * 1000000 * 60)
  def hours(n: Long): Timepoint = Timepoint(n * 1000000 * 60 * 60)
  def days(n: Long): Timepoint = Timepoint(n * 1000000 * 60 * 60 * 24)
}

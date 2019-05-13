package io.casperlabs.sim.simulation_framework

/**
  * Represents a point of the simulated time.
  * This is internally the number of (virtual) microseconds elapsed since the simulation started.
  */
case class Timepoint(micros: Long) extends AnyVal with Ordered[Timepoint] {

  override def compare(that: Timepoint): Int = math.signum(micros - that.micros).toInt

  def +(delta: TimeDelta): Timepoint = Timepoint(micros + delta)

  def -(other: Timepoint): TimeDelta = this.micros - other.micros
}

object Timepoint {
  val zero: Timepoint = Timepoint(0L)
}

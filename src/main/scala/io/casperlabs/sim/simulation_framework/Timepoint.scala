package io.casperlabs.sim.simulation_framework

/**
  * Represents a point of the simulated time.
  * This is internally the number of (virtual) microseconds elapsed since the simulation started.
  */
case class Timepoint(micros: Long) extends Ordered[Timepoint] {

  override def compare(that: Timepoint): Int = math.signum(micros - that.micros).toInt

  def +(delta: TimeDelta): Timepoint = Timepoint(micros + delta)

  def -(other: Timepoint): TimeDelta = this.micros - other.micros
}

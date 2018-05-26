package net.kurobako.jmhv

import java.util.concurrent.TimeUnit

import upickle.default.{macroRW, readwriter, ReadWriter => RW}

import scala.collection.immutable
import scala.concurrent.duration.Duration

case class JMHReport(runs: Seq[JMHReport.Run])


object JMHReport {

	import enumeratum.values._

	def fqnLastFragment(s: String): String = s.substring(s.lastIndexOf('.') + 1)

	def fqnPathFragment(s: String): String = s.substring(0, s.lastIndexOf('.'))


	sealed abstract class Mode(val value: String) extends StringEnumEntry
	case object Mode extends StringEnum[Mode] {
		override def values: immutable.IndexedSeq[Mode] = findValues
		case object All extends Mode("all")
		case object Throughput extends Mode("thrpt")
		case object AverageTime extends Mode("avgt")
		case object SampleTime extends Mode("sample")
		case object SingleShotTime extends Mode("ss")
	}


	sealed trait Time
	case object SingleShot extends Time
	case class Scalar(duration: Duration) extends Time


	case class Metric(score: Double,
					  scoreError: Double,
					  scoreConfidence: Seq[Double],
					  scoreUnit: String,
					  scorePercentiles: Map[String, Double],
					  rawData: Seq[Seq[Double]],
					  // TODO histogram?
					  //rawDataHistogram: Seq[Seq[Double]]
					 )


	case class Run(benchmark: String,
				   mode: Mode,
				   threads: Int,
				   forks: Int,

				   warmupIterations: Int,
				   warmupTime: Time,
				   warmupBatchSize: Int,
				   measurementIterations: Int,
				   measurementTime: Time,
				   measurementBatchSize: Int,

				   params: Map[String, String] = Map(),

				   primaryMetric: Metric,
				   secondaryMetrics: Map[String, Metric] = Map(),
				  )
	implicit val metricRw   : RW[Metric]    = macroRW
	implicit val runRw      : RW[Run]       = macroRW
	implicit val jmhReportRw: RW[JMHReport] = macroRW
	implicit val modeRw     : RW[Mode]      = readwriter[String].bimap[Mode](
		x => x.value,
		s => Mode.withValue(s))

	//XXX this abomination is needed because of org.openjdk.jmh.runner.options.TimeValue
	// it uses non-standard time unit name that does not match Duration.toString
	implicit val timeRw: RW[Time] = readwriter[String].bimap[Time](
	{
		case SingleShot       => "single-shot"
		case Scalar(duration) => duration.length + " " + (duration.unit match {
			case TimeUnit.NANOSECONDS  => "ns"
			case TimeUnit.MICROSECONDS => "us"
			case TimeUnit.MILLISECONDS => "ms"
			case TimeUnit.SECONDS      => "s"
			case TimeUnit.MINUTES      => "min"
			case TimeUnit.HOURS        => "hr"
			case TimeUnit.DAYS         => "day"
		})
	},
	{
		case "single-shot" => SingleShot
		case scalar        => Scalar(scalar.trim.split("\\s+", 2).toList match {
			case amount :: unit :: Nil => Duration(amount.toLong, unit match {
				case "ns"  => TimeUnit.NANOSECONDS
				case "us"  => TimeUnit.MICROSECONDS
				case "ms"  => TimeUnit.MILLISECONDS
				case "s"   => TimeUnit.SECONDS
				case "min" => TimeUnit.MINUTES
				case "hr"  => TimeUnit.HOURS
				case "day" => TimeUnit.DAYS
				case x     => throw new Exception(s"Unknown time unit $x for value $amount")
			})
			case _                     =>
				throw new Exception(s"Invalid duration format: " + scalar)
		})
	})


}

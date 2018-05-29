package net.kurobako.jmhv

import java.util.concurrent.TimeUnit

import cats.implicits._
import net.kurobako.jmhv.JMHReport.PackageGroup
import shapeless.syntax.unwrapped._
import shapeless.tag.@@

import scala.collection.immutable
import scala.concurrent.duration.Duration


final case class JMHReport(packages: List[PackageGroup])


object JMHReport {

	// missing key should serialise to None, the default throws an exception
	final object OptionPickler extends upickle.AttributeTagged {
		override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
			implicitly[Writer[T]].comap[Option[T]] {
				case None    => null.asInstanceOf[T]
				case Some(x) => x
			}

		override implicit def OptionReader[T: Reader]: Reader[Option[T]] =
			implicitly[Reader[T]].mapNulls {
				case null => None
				case x    => Some(x)
			}
	}

	import OptionPickler.{macroRW, read, readwriter, ReadWriter => RW}
	import enumeratum.values._

	def apply(json: String): Either[String, JMHReport] = for {
		runs <- Either.catchNonFatal(read[Seq[Run]](json)).leftMap(_.getMessage)
		report <- JMHReport(runs)
	} yield report

	def apply(report: Seq[Run]): Either[String, JMHReport] = {

		def groupBy[A, B](xs: List[ClassMethod], f: ClassMethod => A)
						 (g: (A, List[ClassMethod]) => B): List[B] =
			xs.groupBy(f).map { case (a, cs) => g(a, cs) }.toList
		/*_*/
		val cms: Either[String, List[ClassMethod]] = report.toList.zipWithIndex.map {
			case (x, i) => x.benchmark.split('.').toSeq match {
				case pkg :+ cls :+ mtd =>
					Right(ClassMethod(
						pkg.mkString(".").wrap[Pkg],
						cls.wrap[Cls],
						mtd.wrap[Mtd], i, x))
				case e                 =>
					Left(s"Unable to parse fully qualified method name: <${x.benchmark}>, tokens: $e")
			}
		}.sequence
		/*_*/
		cms.map { xs =>
			JMHReport(groupBy(xs, _.pkg) { (pkg, cs) =>
				PackageGroup(pkg, groupBy(cs, _.cls)(ClassGroup))
			})
		}
	}

	trait _Cls
	type Cls = String @@ _Cls
	trait _Pkg
	type Pkg = String @@ _Pkg
	trait _Mtd
	type Mtd = String @@ _Mtd

	final case class Param(kvs: Map[String, String]) {
		lazy val stable   : List[String] = kvs.map { case (k, v) => k + "=" + v }.toList.sorted
		lazy val formatted: String       = if (stable.isEmpty) "" else stable.mkString("{", ",", "}")
	}

	final case class PackageGroup(pkg: Pkg, classes: List[ClassGroup])
	final case class ClassGroup(cls: Cls, methods: List[ClassMethod]) {
		def groupByParam: Map[Param, Map[Mtd, ClassMethod]] =
			methods.groupBy(_.params).mapValues {_.map { x => x.mtd -> x }.toMap}
		def groupByMethod: Map[Mtd, Map[Param, ClassMethod]] =
			methods.groupBy(_.mtd).mapValues {_.map { x => x.params -> x }.toMap}
	}
	final case class ClassMethod(pkg: Pkg, cls: Cls, mtd: Mtd,
								 order: Int, run: Run) {
		val params: Param = Param(run.params)
	}

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
	case object SingleShot extends Time {
		override def toString: String = "single-shot"
	}
	case class Scalar(duration: Duration) extends Time{
		override def toString: String = duration.toString
	}


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

				   jmhVersion: Option[String] = None,
				   jvm: Option[String] = None,
				   jvmArgs: Option[List[String]] = None,
				   jdkVersion: Option[String] = None,
				   vmVersion: Option[String] = None,

				   warmupIterations: Int,
				   warmupTime: Time,
				   warmupBatchSize: Int,
				   measurementIterations: Int,
				   measurementTime: Time,
				   measurementBatchSize: Int,

				   params: Map[String, String] = Map(),

				   primaryMetric: Metric,
				   secondaryMetrics: Map[String, Metric] = Map(),
				  ) {
		override def toString: String = s"Run($benchmark)"
	}

	implicit val metricRw: RW[Metric] = macroRW
	implicit val runRw   : RW[Run]    = macroRW
	implicit val modeRw  : RW[Mode]   = readwriter[String].bimap[Mode](
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

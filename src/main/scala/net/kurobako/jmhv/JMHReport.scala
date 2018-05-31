package net.kurobako.jmhv

import java.util.concurrent.TimeUnit

import cats.implicits._
import net.kurobako.jmhv.JMHReport.PackageGroup
import shapeless.syntax.unwrapped._
import shapeless.tag.@@

import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.scalajs.js


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

	import OptionPickler.{macroRW, read, readJs, readwriter, ReadWriter => RW, Reader}
	import enumeratum.values._

	def apply(json: js.Any): Either[String, JMHReport] = for {
		runs <- Either.catchNonFatal(readJs[Seq[Run]](upickle.WebJson.transform(json, implicitly[Reader[upickle.Js.Value]]))).leftMap(_.getMessage)
		report <- JMHReport(runs)
	} yield report

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
				PackageGroup(pkg, groupBy(cs, _.cls)((cls, ccs) =>
					ClassGroup(pkg, cls, ccs, ccs.groupBy(_.run.mode))))
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
	final case class ClassGroup(pkg: Pkg, cls: Cls,
								methods: List[ClassMethod],
								modes: Map[Mode, List[ClassMethod]]) {
		def groupByParam[A](mode: Mode, f: ClassMethod => Option[A]): Map[Param, Map[Mtd, A]] =
			modes.getOrElse(mode, Nil).groupBy(_.params).mapValues { xs =>
				(for {
					x <- xs
					a <- f(x).toIterable
				} yield x.mtd -> a).toMap
			}
		def groupByMethod[A](mode: Mode, f: ClassMethod => Option[A]): Map[Mtd, Map[Param, A]] =
			modes.getOrElse(mode, Nil).groupBy(_.mtd).mapValues { xs =>
				(for {
					x <- xs
					a <- f(x).toIterable
				} yield x.params -> a).toMap
			}
	}
	final case class ClassMethod(pkg: Pkg, cls: Cls, mtd: Mtd,
								 order: Int, run: Run) {
		val params: Param = Param(run.params)
	}


	sealed abstract class Mode(val value: String, val shortName: String) extends StringEnumEntry
	case object Mode extends StringEnum[Mode] {
		override def values: immutable.IndexedSeq[Mode] = findValues
		case object All extends Mode("all", "All modes")
		case object Throughput extends Mode("thrpt", "Throughput(ops/time)")
		case object AverageTime extends Mode("avgt", "Average time(time/op)")
		case object SampleTime extends Mode("sample", "Sampling time")
		case object SingleShotTime extends Mode("ss", "Single shot invocation time")
	}

	sealed trait Time
	case object SingleShot extends Time {
		override def toString: String = "single-shot"
	}
	case class Scalar(duration: Duration) extends Time {
		override def toString: String = duration.toString
	}


	case class Metric(score: Double,
					  // XXX the story with error and confidence is interesting
					  // documentation says error and CI is calculated assuming normal distribution
					  // but the actual implementation uses t-distribution due to the potentially
					  // small sample size
					  scoreError: Double,
					  scoreConfidence: Seq[Double],
					  scoreUnit: String,
					  // XXX the story with percentiles are even more interesting
					  // the percentile algorithm uses the one shipped with common-maths3-3.2
					  // and we are presented with a range of percentiles:
					  // 0.00, 0.50, 0.90, 0.95, 0.99, 0.999, 0.9999, 0.99999, 0.999999, 1.0
					  // I don't think these are meaningful and could be misleading when the sample
					  // size will usually be <= 50 with samples suggesting 5 being acceptable
					  scorePercentiles: Map[String, Double],
					  rawData: Seq[Seq[Double]] = Seq(),
					  // XXX not sure why the histogram looks like this
					  rawDataHistogram: Seq[Seq[Seq[Seq[Double]]]] = Seq()
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

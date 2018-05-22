package net.kurobako.jmhv

import com.highcharts.{CleanJsObject, Highcharts}
import com.highcharts.HighchartsAliases._
import com.highcharts.HighchartsUtils._
import com.highcharts.config._
import org.scalajs.dom
import org.scalajs.dom.raw.Element
import org.scalajs.dom.{console, document}

import scala.concurrent.duration.Duration
import scala.scalajs.js


object Application {


	case class JMHReport(runs: Seq[JMHReport.Run])


	object JMHReport {

		sealed trait Mode
		case object Throughput extends Mode
		case object AverageTime extends Mode
		case object SampleTime extends Mode
		case object SingleShotTime extends Mode
		case object All extends Mode


		sealed trait Time
		case object SingleShot extends Time
		case class Scalar(duration: Duration) extends Time


		case class Metric(score: Double,
						  scoreError: Double,
						  scoreConfidence: Double,
						  scoreUnit: String,
						  scorePercentiles: Map[String, Double],
						  rawData: Seq[Seq[Double]],
						  // TODO histogram?
						  //rawDataHistogram: Seq[Seq[Double]]
						 )

		case class Run(benchmark: String, mode: Mode,
					   threads: Int,
					   forks: Int,

					   warmupIterations: Int,
					   warmupTime: Time,
					   warmupBatchSize: Int,
					   measurementIterations: Int,
					   measurementTime: Time,
					   measurementBatchSize: Int,

					   parms: Option[Map[String, String]],

					   primaryMetric: Metric,
					   secondaryMetrics: Map[String, Metric],
					  )


	}


	def main(args: Array[String]): Unit = {


		val element: Element = document.getElementById("container")


		val config = new HighchartsConfig {
			// Chart config
			override val chart: Cfg[Chart] = Chart(`type` = "bar")

			// Chart title
			override val title: Cfg[Title] = Title(text = "Demo bar chart")

			// Y Axis settings
			override val yAxis: CfgArray[YAxis] = js.Array(YAxis(title = YAxisTitle(text = "Fruit eaten")))

			// X Axis settings
			override val xAxis: CfgArray[XAxis] = js.Array(XAxis(categories = js.Array("Apples", "Bananas", "Oranges")))

			// Series
			override val series: SeriesCfg = js.Array[AnySeries](
				SeriesBar(name = "Jane", data = js.Array[Double](1, 0, 4)),
				SeriesBar(name = "John", data = js.Array[Double](5, 7, 3))
			)
		}

		console.log(element, config)


		Highcharts.chart(element.asInstanceOf[CleanJsObject[Nothing]], CleanJsObject(config))

	}

}

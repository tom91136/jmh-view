package net.kurobako.jmhv


import com.thoughtworks.binding.Binding.{Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import enumeratum.values.{StringEnum, StringEnumEntry}
import net.kurobako.jmhv.JMHReport.Run
import org.scalajs.dom._
import org.scalajs.dom.experimental.{Fetch, Response}
import org.scalajs.dom.html.{Div, Select}

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|
import scala.util.{Failure, Success}


@JSExportTopLevel("JMHView")
object JMHView {


	@JSExport
	def setup(element: String | js.Object, jsonPath: String | js.Object): Unit = {


		(jsonPath: Any) match {
			case url: String       => console.log("str", url)
				fetchJSON(url).onComplete {
					case Success(r) =>

						dom.render(document.body, renderT(JMHViewState(Var(Right(r)))))


						if (true) return ();
						val xs: Map[String, List[Run]] = collectRuns(r.runs.toList)


						case class ParamGroup(clazz: String, params: Map[String, String], runs: List[Run]) {

							def stableParameters: List[String] =
								params.map { case (k, v) => k + "=" + v }.toList.sorted

							def mkId: String = s"$clazz-${stableParameters.mkString.hashCode}"
						}

						import scalatags.JsDom.all._

						def stableParams(params: Map[String, String]): List[String] =
							params.map { case (k, v) => k + "=" + v }.toList.sorted

						val classes = collectRuns(r.runs.toList).toList
						document.body.appendChild(div(classes.map(x => div(id := x._1))).render)


						classes.foreach { case (clazz, xs) =>

							val sorted = xs.sortBy { x => (x.primaryMetric.score) }


							//							sorted.foldLeft(Linked){case (acc, x) => acc.stableParams(x.params)}


							mkBarChart(elem = clazz,
								chartTitle = clazz,
								yAxisTitle = xs.head.primaryMetric.scoreUnit,
								xSeries = List(stableParams(xs.head.params).mkString("[", ",", "]") -> sorted.map {_.primaryMetric.score}),
								xLabels = sorted.map {_.benchmark})
						}


						val xxx: List[ParamGroup] = for {
							(clazz, rs) <- collectRuns(r.runs.toList).toList
							(param, runs) <- rs.groupBy {_.params}.toList
						} yield ParamGroup(clazz, param, runs)

						document.body.appendChild(div(xxx.map(pg => div(id := pg.mkId))).render)

						xxx.foreach { case g@ParamGroup(clazz, _, xs) =>
							val sorted = xs.sortBy {_.primaryMetric.score}
							mkBarChart(elem = g.mkId,
								chartTitle = clazz,
								yAxisTitle = xs.head.primaryMetric.scoreUnit,
								xSeries = List(g.stableParameters.mkString("[", ",", "]") -> sorted.map {_.primaryMetric.score}),
								xLabels = sorted.map {_.benchmark})
						}


					case Failure(e) => throw e
				}
			case struct: js.Object => console.log("obj", struct)
			case unexpected        =>
				throw new Exception(s"Expecting (String | JMHReport), got $unexpected")
		}


	}


	sealed abstract class ParamMode(val value: String) extends StringEnumEntry
	case object ParamMode extends StringEnum[ParamMode] {
		override def values = findValues
		case object Separate extends ParamMode("Separate")
		case object Combined extends ParamMode("Combined")
	}

	sealed abstract class SortMode(val value: String) extends StringEnumEntry
	case object SortMode extends StringEnum[SortMode] {
		override def values = findValues
		case object Ascending extends SortMode("Ascending")
		case object Descending extends SortMode("Descending")
		case object Natural extends SortMode("Natural")
	}


	case class JMHConfig(compactSize: Double = Double.MaxValue,
						 sortMode: SortMode = SortMode.Ascending,
						 paramMode: ParamMode = ParamMode.Separate,
						)

	case class JMHViewState(report: Var[Either[String, JMHReport]],
							config: Var[JMHConfig] = Var(JMHConfig()),
							selected: Var[Option[String]] = Var(None),
							sortMode: SortMode = SortMode.Ascending,
							paramMode: ParamMode = ParamMode.Separate)


	def renderT(state: JMHViewState) = state.report.value match {
		case Left(value)  =>
			@dom val error: Binding[Div] = {<div>{value}</div>}
			error
		case Right(value) =>



			val grouped: TreeMap[String, List[Run]] = TreeMap(collectRuns(value.runs.toList).toArray: _*)
			val classes: List[String] = grouped.keys.toList.sorted

			println(s"Classes : $classes")

			@dom def renderClassNavs: Binding[Node] = {
				val as = Constants(classes: _*).map { name =>
					<li><a href="#"
					   class={s"${if (state.selected.value.contains(name)) "selected" else ""}"}
					   onclick={_: Event => state.selected.value = Some(name)}>
					{name}
					</a></li>
				}
				if (classes.size > 1) {
					<aside><nav class="class-nav"><ol>{as}</ol></nav></aside>
				} else  <!-- Single class only  -->
			}

			@dom def renderDropDown[A](xs: Seq[A], x: Option[A], f: A => String): Binding[Select] = {
				val os = Constants(xs.map(f): _*).map { name =>
					<option value={name}
							selected={x.contains(name)}>
						{name}
					</option>
				}

				// TODO select default
				<select onchange={ e: Event => ??? }
				>{os}</select>
			}


			val gs: List[(String, Map[String, String], List[Run])] = for {
				(clazz, rs) <- grouped.toList
				(param, xs) <- rs.groupBy {_.params}.toList
			} yield (clazz, param, xs)


			@dom def mkResults = Constants(gs: _ *).map { case (clazz, param, xs) =>

				val sorted = xs.sortBy {_.primaryMetric.score}
				val paramName = param.map { case (k, v) => k + "=" + v }.mkString(",")
				val _id = s"$clazz-${paramName.hashCode.toHexString}"

				@dom val surface: Binding[Div] = <div class="result-block"><div id={_id} class="chart" ></div></div>

				val x = surface.bind

				println(s"Loading graph for ${_id}->${document.getElementById(_id)} -> $x")

				mkBarChart(elem = x,
					chartTitle = clazz,
					yAxisTitle = xs.head.primaryMetric.scoreUnit,
					xSeries = List(paramName -> sorted.map {_.primaryMetric.score}),
					xLabels = sorted.map {_.benchmark})

				x
			}


			@dom def renderResult(x: Node): Binding[Div] = {
				<div class="result-block">{x}</div>
			}

			@dom val view: Binding[Div] = {
				<div id="container" class="jmh-view">
					{renderClassNavs.bind}
					<article>
						<header>
							{if (classes.size == 1) {
								<span>{classes.mkString}</span>
							} else {
								<span class="alt-class-nav">
									Classes:{renderDropDown[String](classes, state.selected.value, identity).bind}
								</span>
							}}
							<span>
								Parameters:{renderDropDown[ParamMode](ParamMode.values, Some(state.paramMode), _.value).bind}
							</span>
							<span>
								Sort:{renderDropDown[SortMode](SortMode.values, Some(state.sortMode), _.value).bind}
							</span>
						</header>
						<section class="class-result">
							{mkResults.bind}
						</section>
					</article>
				</div>
			}
			view
	}


	def mkBarChart(elem: String | js.Object, chartTitle: String, yAxisTitle: String,
				   xSeries: List[(String, List[Double])],
				   xLabels: List[String]) = {

		import com.highcharts.HighchartsAliases.{AnySeries, SeriesCfg, _}
		import com.highcharts.HighchartsUtils.{Cfg, CfgArray, _}
		import com.highcharts.config._
		import com.highcharts.{CleanJsObject, Highcharts}

		import js.JSConverters._

		Highcharts.chart(elem, CleanJsObject(new HighchartsConfig {
			override val chart : Cfg[Chart]      = Chart(`type` = "bar")
			override val title : Cfg[Title]      = Title(text = chartTitle)
			override val yAxis : CfgArray[YAxis] = js.Array(YAxis(title = YAxisTitle(text = yAxisTitle)))
			override val xAxis : CfgArray[XAxis] = js.Array(XAxis(categories = xLabels.toJSArray))
			override val series: SeriesCfg       =
				xSeries.map {
					case (name, xs) => SeriesBar(name = name, data = xs.toJSArray): AnySeries
				}.toJSArray
		}))
	}

	def collectRuns(xs: List[Run]): Map[String, List[Run]] = {

		def extractFqcn(fqmn: String): String = fqmn.substring(0, fqmn.lastIndexOf('.'))
		xs.foldLeft(Map.empty[String, List[Run]]) {
			case (acc, x) =>
				val fqcn = extractFqcn(x.benchmark)
				acc + (fqcn -> (x :: acc.getOrElse(fqcn, Nil)))
		}
	}

	def readJMHReport(json: String): Future[JMHReport] = {
		import JMHReport._
		import upickle.default._
		Future {
			time("Parse JSON", JMHReport(readJs[Seq[Run]](upickle.json.read(json))))
		}
	}

	def fetchJSON(url: String): Future[JMHReport] = for {
		response <- fetch(url)
		json <- response.text().toFuture
		report <- readJMHReport(json)
	} yield report

	def fetch(url: String): Future[Response] = Fetch.fetch(url).toFuture.flatMap { r =>
		if (!r.ok) {
			Future.failed(new Exception(s"Fetch error: ${r.status} ${r.statusText}(${r.url})"))
		} else Future.successful(r)
	}

	def time[R](name : String, block: => R): R = {
		val t0 = System.nanoTime()
		val result = block // call-by-name
		val t1 = System.nanoTime()
		println(s"<$name> elapsed time: " + ((t1 - t0).toFloat / 1000000.0) + "ms")
		result
	}

}
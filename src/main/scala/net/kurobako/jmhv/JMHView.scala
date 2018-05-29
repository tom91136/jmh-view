package net.kurobako.jmhv


import com.thoughtworks.binding.Binding.{BindingSeq, Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import enumeratum.values.{StringEnum, StringEnumEntry}
import net.kurobako.jmhv.DomBindings.renderDropDown
import net.kurobako.jmhv.JMHReport.{ClassGroup, ClassMethod, Cls, Mtd, PackageGroup, Param, Run}
import net.kurobako.jmhv.JMHView.GroupMode.{Method, Parameter}
import net.kurobako.jmhv.JMHView.ScaleMode.{Linear, Logarithmic}
import net.kurobako.jmhv.JMHView.SortMode.{Ascending, Descending, Natural}
import org.scalajs.dom._
import org.scalajs.dom.experimental.{Fetch, Response}
import org.scalajs.dom.html.{Div, Table}
import shapeless.{Nat, Sized}

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
					// TODO replace with FutureBinding
					case Success(r) => dom.render(document.body, renderT(JMHViewState(Var(Right(r)))))
					case Failure(e) => throw e
				}
			case struct: js.Object => console.log("obj", struct)
			case unexpected        =>
				throw new Exception(s"Expecting (String | JMHReport), got $unexpected")
		}

	}


	sealed abstract class GroupMode(val value: String) extends StringEnumEntry
	case object GroupMode extends StringEnum[GroupMode] {
		override def values = findValues
		case object Parameter extends GroupMode("Parameter")
		case object Method extends GroupMode("Method")
	}

	sealed abstract class SortMode(val value: String) extends StringEnumEntry
	case object SortMode extends StringEnum[SortMode] {
		override def values = findValues
		case object Ascending extends SortMode("Ascending")
		case object Descending extends SortMode("Descending")
		case object Natural extends SortMode("Natural")
	}

	sealed abstract class ScaleMode(val value: String) extends StringEnumEntry
	case object ScaleMode extends StringEnum[ScaleMode] {
		override def values = findValues
		case object Linear extends ScaleMode("Linear")
		case object Logarithmic extends ScaleMode("Logarithmic")
	}

	implicit def stringEnumEntryShow(e: StringEnumEntry): String = e.value
	implicit def stringEnumEntryRead[A <: StringEnumEntry](s: String)(implicit ev: StringEnum[A]): A = ev.withValue(s)

	implicit val sortModeRead : String => SortMode  = stringEnumEntryRead[SortMode]
	implicit val groupModeRead: String => GroupMode = stringEnumEntryRead[GroupMode]
	implicit val scaleModeRead: String => ScaleMode = stringEnumEntryRead[ScaleMode]

	case class JMHConfig(compactSize: Double = Double.MaxValue,
						 sortMode: SortMode = SortMode.Ascending,
						 paramMode: GroupMode = GroupMode.Parameter,
						)

	case class JMHViewState(report: Var[Either[String, JMHReport]],
							config: Var[JMHConfig] = Var(JMHConfig()),

							sortMode: Var[SortMode] = Var(SortMode.Ascending),
							groupMode: Var[GroupMode] = Var(GroupMode.Parameter),
							scaleMode: Var[ScaleMode] = Var(ScaleMode.Linear),

							selectedClass: Var[Option[ClassGroup]] = Var(None),
							selectedMetric: Var[Option[String]] = Var(None),
							showDetails: Var[Boolean] = Var(true),
						   )

	def renderT(state: JMHViewState) = state.report.value match {
		case Left(value)   =>
			@dom val error: Binding[Div] = {
				<div>
					{value}
				</div>
			}
			error
		case Right(report) =>


			val totalClassGroups = report.packages.map {_.classes.size}.sum

			@dom def renderClassNavs: Binding[Node] = {
				val lis = Constants(report.packages: _*).map { case PackageGroup(pkg, xs) =>
					<ol>
						<li>
							{pkg}
						</li>{Constants(xs: _*).map { group =>
						<li>
							<a href="#"
							   class={s"${if (state.selectedClass.bind.contains(group)) "active" else ""}"}
							   onclick={_: Event => state.selectedClass.value = Some(group)}>
								{group.cls}
							</a>
						</li>
					}}
					</ol>
				}
				if (totalClassGroups > 1) {
					<aside>
						<nav class="class-nav">
							{lis}
						</nav>
					</aside>
				} else <!-- Single class only  -->
			}


			@dom def mkChart[A](cls: Cls, group: String,
								xs: Map[A, ClassMethod], range: (Double, Double))
							   (f: A => String): Binding[Div] = {

				// XXX f should be an instance of Show but that's too much work

				val _id = s"$cls-${group.hashCode.toHexString}"

				@dom val surface: Binding[Div] = <div id={_id} class="chart"></div>

				val chart = surface.bind


				val sorted = state.sortMode.bind match {
					case Ascending  => xs.toList.sortBy(_._2.run.primaryMetric.score)
					case Descending => xs.toList.sortBy(_._2.run.primaryMetric.score)(Ordering[Double].reverse)
					case Natural    => xs.toList.sortBy(_._2.order)
				}

				val labels: List[String] = sorted.map(_._1).map(f)
				val data: List[Double] = sorted.map(_._2.run.primaryMetric.score)

				bindBarChart(elem = chart.asInstanceOf[Div],
					chartTitle = cls + ": " + group,
					yAxisTitle = cls + ": " + group,
					xSeries = List(group -> data),
					xLabels = labels,
					range = range,
					scale = state.scaleMode.bind
				)
				chart
			}

			@dom def renderTable(group: ClassGroup): Binding[Div] = {
				def withUnitS(n: Int, unit: String) = s"$n $unit${if (n == 1) "" else "s"}"
				val x = group.methods.head.run
				val kvs = Vector(
					Sized("Mode", x.mode.value),
					Sized("Metrics", s"1 primary + ${x.secondaryMetrics.size.toString} secondary"),
					Sized("Fixtures",
						s"${withUnitS(group.methods.size, "method")} × " +
						s"${withUnitS(group.methods.map {_.params}.distinct.size, "param")}"),
					Sized("Threading",
						s"${withUnitS(x.forks, "fork")} × " +
						s"${withUnitS(x.threads, "thread")} "),
					Sized("Warm-up time", x.warmupTime.toString),
					Sized("Warm-up iter.", s"${x.warmupIterations}(batch=${x.warmupBatchSize})"),
					Sized("Measure time", x.measurementTime.toString),
					Sized("Measure iter.", s"${x.measurementIterations}(batch=${x.measurementBatchSize})"),

					Sized("JVM binary", x.jvm.getOrElse("???")),
					Sized("VM version", x.vmVersion.getOrElse("???")),
					Sized("JDK version", x.jdkVersion.getOrElse("???")),
					Sized("JMH version", x.jmhVersion.getOrElse("???")),
				)
				// TODO JVM args
				//				@dom val jvmArgs = Constants(x.jvmArgs.getOrElse(List("???")).map {x => <p>{x}</p> }:_*)
				if (state.showDetails.bind) {
					<div class="result-block">
						{DomBindings.renderTable(Sized("Key", "Value"), kvs, 4)(identity).bind}
					</div>
				} else {
					<div class="result-block">
					</div>
				}
			}

			@dom def mkResults = state.selectedClass.bind match {
				case Some(group) =>
					val scores = group.methods.map {_.run.primaryMetric.score}
					val range = (scores.min, scores.max)
					val charts: BindingSeq[Div] = state.groupMode.bind match {
						case Parameter =>
							Constants(group.groupByParam.toSeq: _*).mapBinding { case (param, xs) =>
								mkChart(group.cls, param.formatted, xs, range)(identity)
							}
						case Method    =>
							Constants(group.groupByMethod.toSeq: _*).mapBinding { case (mtd, xs) =>
								mkChart(group.cls, mtd, xs, range)(_.formatted)
							}
					}

					<section class="class-result">
						{renderTable(group).bind}<div class="result-block">
						{charts}
					</div>
					</section>
				case None        => <section class="class-result"></section>
			}

			@dom val view: Binding[Div] = {
				<div id="container" class="jmh-view">
					{renderClassNavs.bind}<article>
					<header>
						{if (totalClassGroups == 1) {
						<span>
							{(for {
							pkg <- report.packages
							cls <- pkg.classes
						} yield pkg.pkg + cls.cls).mkString}
						</span>
					} else {
						<span class="alt-class-nav">
							Classes:
							{//							renderDropDown[Option[String]](classes.map {Some(_)}, state.selected)(s => Some(s), _.getOrElse("???")).bind
							"???"}
						</span>
					}}<span>
						Group by:
						{renderDropDown[GroupMode](GroupMode.values, state.groupMode).bind}
					</span>
						<span>
							Sort:
							{renderDropDown[SortMode](SortMode.values, state.sortMode).bind}
						</span>
						<span>
							Scale:
							{renderDropDown[ScaleMode](ScaleMode.values, state.scaleMode).bind}
						</span>


						<span>
							Metric:
							{val metrics = state.selectedClass.bind match {
							case Some(value) => value.methods.flatMap {_.run.secondaryMetrics.keys}.distinct
							case None        => Nil
						}


						renderDropDown[Option[String]](metrics.map {Some(_)}, state.selectedMetric)(Some(_), {_.getOrElse("Score")}).bind}
						</span>
						<span>
							Show details:
							<input type="checkbox"
								   checked={state.showDetails.value}
								   onclick={e: Event =>
									   state.showDetails.value =
										   e.currentTarget.asInstanceOf[html.Input].checked}>
							</input>
						</span>

					</header>{mkResults.bind}
				</article>
				</div>
			}
			view
	}

	def bindBarChart(elem: String | js.Object, chartTitle: String, yAxisTitle: String,
					 xSeries: List[(String, List[Double])],
					 xLabels: List[String],
					 range: (Double, Double),
					 scale: ScaleMode): Unit = {

		import com.highcharts.HighchartsAliases.{AnySeries, SeriesCfg, _}
		import com.highcharts.HighchartsUtils.{Cfg, CfgArray, _}
		import com.highcharts.config._
		import com.highcharts.{CleanJsObject, Highcharts}

		import js.JSConverters._

		val (min, max) = range

		val chart = Highcharts.chart(elem, CleanJsObject(new HighchartsConfig {

			val xa = XAxis(categories = xLabels.toJSArray)

			val ya = YAxis(
				labels = YAxisLabels(enabled = false),
				max = max, min = min,
				title = YAxisTitle(text = yAxisTitle),
				`type` = scale match {
					case Linear      => "linear"
					case Logarithmic => "logarithmic"
				}
			)


			override val legend     : Cfg[Legend]      = Legend(enabled = false)
			override val plotOptions: Cfg[PlotOptions] = PlotOptions(bar = PlotOptionsBar(
				pointWidth = 15,
				pointPadding = 5,
				dataLabels = PlotOptionsBarDataLabels(enabled = true)))
			override val credits    : Cfg[Credits]     = Credits(enabled = false)
			override val chart      : Cfg[Chart]       = Chart(`type` = "bar", height = ((xLabels.size + 1) * 20 + 20))
			override val title      : Cfg[Title]       = Title(text = null)
			override val xAxis      : CfgArray[XAxis]  = js.Array(xa)
			override val yAxis      : CfgArray[YAxis]  = js.Array(ya)
			override val series     : SeriesCfg        =
				xSeries.map {
					case (name, xs) => SeriesBar(name = name, data = xs.toJSArray): AnySeries
				}.toJSArray
		}))
		// to fix issues with binding to a detached DOM element
		// XXX breaks initial animation
		window.requestAnimationFrame { _ => chart.reflow() }
	}


	def readJMHReport(json: String): Future[JMHReport] = {
		Future {
			// TODO this is stupid
			time("Parse JSON", JMHReport(json).fold(x => throw new Exception(x), identity))
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

	def time[R](name: String, block: => R): R = {
		val t0 = System.nanoTime()
		val result = block // call-by-name
		val t1 = System.nanoTime()
		println(s"<$name> elapsed time: " + ((t1 - t0).toFloat / 1000000.0) + "ms")
		result
	}

}
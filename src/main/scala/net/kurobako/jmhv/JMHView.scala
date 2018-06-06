package net.kurobako.jmhv

import cats.implicits._
import com.thoughtworks.binding.Binding.{BindingSeq, Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import enumeratum.values.{StringEnum, StringEnumEntry}
import net.kurobako.jmhv.DomBindings.{dropDownVar, highchartsFixedErrorBarChart, renderTable}
import net.kurobako.jmhv.JMHReport.{ClassGroup, ClassMethod, Metric, Mode, PackageGroup}
import net.kurobako.jmhv.JMHView.GroupMode.{Method, Parameter}
import net.kurobako.jmhv.JMHView.ScaleMode.Logarithmic
import net.kurobako.jmhv.JMHView.SortMode.{Ascending, Descending, Natural}
import org.scalajs.dom._
import org.scalajs.dom.experimental.{Fetch, Response}
import org.scalajs.dom.html.Div
import shapeless.{Sized, nat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|

@JSExportTopLevel("JMHView")
object JMHView {


	@JSExport
	def setup(element: String | js.Object, jsonPath: String | js.Object): Unit = {
		val node = (element: Any) match {
			case id: String =>
				val selected = document.getElementById(id)
				if (selected == null) throw new Exception(s"No element with #$id found")
				selected
			case node: Node => node
			case unexpected =>
				throw new Exception(s"Expecting (ID | Dom.Node), got $unexpected")
		}

		(jsonPath: Any) match {
			case url: String       =>
				fetchJSON(url).onComplete { t =>
					val state = JMHViewState(Var(t.toEither.leftMap(x => x.toString)))
					dom.render(node, renderState(state))
				}
			case struct: js.Object =>
				dom.render(node, renderState(JMHViewState(Var(JMHReport(struct)))))
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

	implicit val modeRead     : String => JMHReport.Mode = stringEnumEntryRead[JMHReport.Mode]
	implicit val sortModeRead : String => SortMode       = stringEnumEntryRead[SortMode]
	implicit val groupModeRead: String => GroupMode      = stringEnumEntryRead[GroupMode]
	implicit val scaleModeRead: String => ScaleMode      = stringEnumEntryRead[ScaleMode]

	case class JMHConfig(compactSize: Double = Double.MaxValue,
						 sortMode: SortMode = SortMode.Ascending,
						 paramMode: GroupMode = GroupMode.Parameter)

	case class JMHViewState(report: Var[Either[String, JMHReport]],
							config: Var[JMHConfig] = Var(JMHConfig()),

							sortMode: Var[SortMode] = Var(SortMode.Ascending),
							groupMode: Var[GroupMode] = Var(GroupMode.Parameter),
							scaleMode: Var[ScaleMode] = Var(ScaleMode.Linear),

							selectedGroup: Var[Option[ClassGroup]] = Var(None),
							selectedMetric: Var[Option[String]] = Var(None),
							selectedMode: Var[Option[Mode]] = Var(None),

							showDetails: Var[Boolean] = Var(true))

	// XXX effectful
	def syncOpt(state: JMHViewState, group: ClassGroup): Unit = {
		state.selectedMetric.value.filterNot { x =>
			group.methods
				.filter { y => state.selectedMode.value.contains(y.run.mode) }
				.exists(_.run.secondaryMetrics.contains(x))
		}.foreach { _ => state.selectedMetric.value = None }
		state.selectedMode.value.filterNot { x =>
			group.modes.contains(x)
		}.foreach { _ => state.selectedMode.value = group.modes.keys.headOption }
	}

	@dom def mkResultOptions(state: JMHViewState, group: ClassGroup): Binding[Node] = {

		val metrics = (for {
			mode <- state.selectedMode.bind.toIterable
			group <- group.modes(mode)
			metric <- group.run.secondaryMetrics.keys
		} yield metric).toSeq

		// reset back to none if the new mode does not have the previous metric
		syncOpt(state, group)

		// sum of all secondary metric + 1 for primary
		def countMetric(xs: Seq[ClassMethod]) = xs.map(1 + _.run.secondaryMetrics.size).sum

		val methods = group.methods.toVector

		// sun of all run (1 primary + n secondary )
		val classFrag = s"${group.cls}(${countMetric(methods)})"
		val breadcrumb = state.selectedMode.bind match {
			case Some(mode) =>
				val modeFiltered = methods.filter(_.run.mode == mode)
				s" $classFrag / ${mode.value}(${countMetric(modeFiltered)}) / ${
					state.selectedMetric.bind match {
						case Some(metric) =>
							s"$metric(${modeFiltered.count(_.run.secondaryMetrics.contains(metric))})"
						case None         => s"Score(${group.methods.size})"
					}
				}"
			case None       => classFrag
		}

		<section class="result-block result-header bar">
			<span>
				Mode:
				{dropDownVar[Mode](group.modes.keys.toSeq, state.selectedMode, "None") {_.shortName}.bind}
			</span>
			<span>
				Metric:
				{dropDownVar[String](metrics.distinct.sorted, state.selectedMetric, "Score(primary)").bind}
			</span>
			<span>
				{breadcrumb}
			</span>
		</section>
	}

	@dom def mkResultTable(state: JMHViewState, group: ClassGroup): Binding[Div] = if (!state.showDetails.bind) {
		<div class="result-block"></div>
	} else group.methods match {
		case method :: _ =>

			def withUnitS(n: Int, unit: String) = s"$n $unit${if (n == 1) "" else "s"}"

			val run = method.run

			// TODO JVM args
			//	@dom val jvmArgs = Constants(x.jvmArgs.getOrElse(List("???")).map {x => <p>{x}</p> }:_*)

			val kvs: IndexedSeq[Sized[IndexedSeq[String], nat._2]] = Vector(
				Sized("Mode", run.mode.value),
				Sized("Metrics", s"1 primary + ${run.secondaryMetrics.size.toString} secondary"),
				Sized("Fixtures",
					s"${withUnitS(group.methods.size, "method")} × " +
					s"${withUnitS(group.methods.map {_.params}.distinct.size, "param")}"),
				Sized("Threading",
					s"${withUnitS(run.forks, "fork")} × " +
					s"${withUnitS(run.threads, "thread")} "),
				Sized("Warm-up time", run.warmupTime.toString),
				Sized("Warm-up iter.", s"${run.warmupIterations}(batch=${run.warmupBatchSize})"),
				Sized("Measure time", run.measurementTime.toString),
				Sized("Measure iter.", s"${run.measurementIterations}(batch=${run.measurementBatchSize})"),
				Sized("JVM binary", run.jvm.getOrElse("Unknown")),
				Sized("VM version", run.vmVersion.getOrElse("Unknown")),
				Sized("JDK version", run.jdkVersion.getOrElse("Unknown")),
				Sized("JMH version", run.jmhVersion.getOrElse("Unknown")),
			)

			<div class="result-block">
				{renderTable[String, nat._2](Sized("Key", "Value"), kvs, 4)(identity).bind}
			</div>
		case Nil         => <div class="result-block">No methods available</div>
	}


	@dom def mkResultCharts(state: JMHViewState, group: ClassGroup): Binding[Div] = state.selectedMode.bind match {
		case Some(mode) =>

			val metric: ClassMethod => Option[Metric] = state.selectedMetric.bind match {
				case Some(value) => _.run.secondaryMetrics.get(value)
				case None        => x => Some(x.run.primaryMetric)
			}

			val scores =
				for {
					mtd <- group.methods
					if mtd.run.mode == mode
					metric <- metric(mtd).toIterable
				} yield metric.score


			@dom def mkChart[A](suffix: String, xs: Map[A, (ClassMethod, Metric)])(f: A => String): Binding[Div] = {

				// XXX f should be an instance of Show but that's too much work

				val _id = s"${group.cls}-${suffix.hashCode.toHexString}"

				@dom val surface: Binding[Div] = <div id={_id} class="chart"></div>

				val chart = surface.bind


				val sorted = state.sortMode.bind match {
					case Ascending  => xs.toList.sortBy(_._2._2.score)
					case Descending => xs.toList.sortBy(_._2._2.score)(Ordering[Double].reverse)
					case Natural    => xs.toList.sortBy(_._2._1.order)
				}

				val labels: List[String] = sorted.map(_._1).map(f)
				val data: List[(Double, Double)] = sorted.map { case (_, (_, m)) => m.score -> m.scoreError }
				val units = sorted.map {_._2._2.scoreUnit}.distinct.mkString(" | ")

				highchartsFixedErrorBarChart(elem = chart.asInstanceOf[Div],
					yAxisTitle = s"${group.cls}: $suffix ($units)",
					xSeries = List(suffix -> data),
					xLabels = labels,
					range = scores.min -> scores.max,
					logScale = state.scaleMode.bind == Logarithmic
				)
				chart
			}

			val pairWithMetric = { x: ClassMethod => metric(x).map {x -> _} }

			val charts: BindingSeq[Div] = state.groupMode.bind match {
				case Parameter =>
					Constants(group.groupByParam(mode, pairWithMetric).toSeq: _*)
						.mapBinding { case (param, xs) =>
							mkChart(param.formatted, xs)(identity)
						}
				case Method    =>
					Constants(group.groupByMethod(mode, pairWithMetric).toSeq: _*)
						.mapBinding { case (mtd, xs) =>
							mkChart(reflect.NameTransformer.decode(mtd), xs)(_.formatted)
						}
			}
			<div class="result-block">
				{charts}
			</div>
		case None       => <div class="result-block">
			No mode selected, use the drop down menu to select one; available modes for this class are:
			{group.modes.keys.map {_.shortName}.mkString(",")}
		</div>

	}


	@dom def renderReport(state: JMHViewState, report: JMHReport): Binding[Div] = {

		val classGroups = report.packages.flatMap {_.classes}

		classGroups match {
			case x :: Nil => state.selectedGroup.value = Some(x)
			case _        => // do not select a default one
		}

		@dom def mkResultClass = state.selectedGroup.bind match {
			case Some(group) =>

				syncOpt(state, group)

				<section class="class-result">
					{mkResultOptions(state, group).bind}{mkResultTable(state, group).bind}{mkResultCharts(state, group).bind}
				</section>
			case _           => <section class="class-result"></section>
		}

		val sideClassNav = classGroups match {
			case _ :: Nil => <!-- Single class only  -->
			case _        =>
				val ols = Constants(report.packages: _*).map { case PackageGroup(pkg, xs) =>
					val lis = Constants(xs: _*).map { group =>
						<li>
							<a href="#"
							   title={group.cls}
							   class={s"${if (state.selectedGroup.bind.contains(group)) "active" else ""}"}
							   onclick={_: Event => state.selectedGroup.value = Some(group)}>
								{group.cls}
							</a>
						</li>
					}
					<ol>
						<li title={pkg}>
							{pkg}
						</li>{lis}
					</ol>
				}
				<aside>
					<nav class="class-nav">
						{ols}
					</nav>
				</aside>
		}

		val compactClassNav = classGroups match {
			case x :: Nil =>
				<span>
					{s"${x.pkg}.${x.cls}"}
				</span>
			case xs       =>
				<div class="alt-class-nav">
					<span>
						Classes:
						{dropDownVar[ClassGroup](xs, state.selectedGroup, "None") { x => s"${x.pkg}.${x.cls}" }.bind}
					</span>
				</div>
		}

		<div id="container" class="jmh-view">
			{sideClassNav}<article>
			<header class="bar">
				{compactClassNav}<span>
				Grouping:
				{dropDownVar[GroupMode](GroupMode.values, state.groupMode).bind}
			</span>

				<span>
					Sort:
					{dropDownVar[SortMode](SortMode.values, state.sortMode).bind}
				</span>
				<span>
					Scale:
					{dropDownVar[ScaleMode](ScaleMode.values, state.scaleMode).bind}
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
				<p>

				</p>
			</header>{mkResultClass.bind}
		</article>
		</div>
	}

	@dom def renderState(state: JMHViewState): Binding[Div] = state.report.value match {
		case Left(value)   =>
			<div>
				<p>JMHView failed to instantiate</p>
				<p>
					Error:
					{value}
				</p>
			</div>
		case Right(report) => renderReport(state, report).bind
	}


	def readJMHReport(json: String): Future[JMHReport] = {
		Future {
			// TODO this is stupid
			JMHReport(json).fold(x => throw new Exception(x), identity)
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


}

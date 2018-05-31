package net.kurobako.jmhv


import com.thoughtworks.binding.Binding.{Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Select, Table}
import shapeless.{Nat, Sized}

import scala.scalajs.js
import scala.scalajs.js.{ThisFunction0, |}

object DomBindings {

	/*_*/
	@dom def dropDownVar[A](xs: Seq[A], x: Var[Option[A]], default: String)
						   (implicit show: A => String): Binding[Select] = {
		dropDown(xs, x.bind, x.value = _: Option[A], default).bind
	}
	/*_*/

	/*_*/
	@dom def dropDownVar[A](xs: Seq[A], x: Var[A])
						   (implicit show: A => String): Binding[Select] =
		dropDown(xs, x.bind, {x.value = _: A}).bind
	/*_*/

	/*_*/
	@dom def dropDown[A](xs: Seq[A], x: Option[A], f: Option[A] => Unit, default: String)
						(implicit show: A => String): Binding[Select] = {
		dropDown[Option[A]](None +: xs.map {Some(_)}, x, f) {_.map(show).getOrElse(default)}.bind
	}
	/*_*/

	/*_*/
	@dom def dropDown[A](xs: Seq[A], x: A, f: A => Unit)
						(implicit show: A => String): Binding[Select] = {

		val ixs = xs.toIndexedSeq

		val os = Constants(ixs: _*).map { that =>
			<option selected={x == that}>
				{show(that)}
			</option>
		}
		val select: Select = {
			<select>
				{os}
			</select>
		}
		select.onchange = { _: Event =>
			ixs.lift(select.selectedIndex).foreach(f)
		}
		select
	}
	/*_*/

	@dom def renderTable[A, N <: Nat](header: Sized[IndexedSeq[String], N],
									  xs: IndexedSeq[Sized[IndexedSeq[A], N]],
									  cutoff: Int)(f: A => String): Binding[Table] = {

		val headers = Constants(Seq.fill(xs.size / cutoff)(header).flatten: _*)
			.map { h =>
				<th>
					{h}
				</th>
			}

		//FIXME uneven transpose throws
		val rows = xs.map {_.unsized}
			.grouped(cutoff)
			.toIndexedSeq
			.transpose.map { rs =>
			for {
				r <- rs
				c <- r
			} yield f(c)
		}

		@dom def mkRow(cs: IndexedSeq[String]) = {
			val xs = Constants(cs: _*).map { c =>
				<td>
					{c}
				</td>
			}
			<tr>
				{xs}
			</tr>
		}

		/*_*/
		<table>
			<tr>
				{headers}
			</tr>{Constants(rows: _*).mapBinding {mkRow}}
		</table>
		/*_*/
	}


	// XXX this whole thing is stupid, need to write my own SVG chart at some point
	def highchartsFixedErrorBarChart(elem: String | js.Object,
									 yAxisTitle: String,
									 xSeries: List[(String, List[(Double, Double)])],
									 xLabels: List[String],
									 range: (Double, Double),
									 logScale: Boolean): Unit = {

		import com.highcharts.{CleanJsObject, Highcharts}
		import com.highcharts.HighchartsAliases.{AnySeries, SeriesCfg, _}
		import com.highcharts.HighchartsUtils.{Cfg, CfgArray, _}
		import com.highcharts.config.{Lang, SeriesErrorbarData, _}
		import org.scalajs.dom._

		import js.JSConverters._
		val (min, max) = range

		val xa = XAxis(categories = xLabels.toJSArray, labels = XAxisLabels(style = js.Dynamic.literal(
			fontSize = "1.05em"
		)))

		val ya = YAxis(
			labels = YAxisLabels(enabled = false),
			max = max, min = min,
			title = YAxisTitle(text = yAxisTitle),
			`type` = if (!logScale) "linear" else "logarithmic"
		)

		trait ErrorBarData extends SeriesErrorbarData {
			val range : Double
			val actual: Double
		}

		Highcharts.setOptions(js.Dynamic.literal(lang = Lang(thousandsSep = ""): Cfg[Lang]))

		val chart = Highcharts.chart(elem, CleanJsObject(new HighchartsConfig {


			val width   = 20.0
			val padding = 2.0

			override val legend     : Cfg[Legend]      = Legend(enabled = false)
			override val plotOptions: Cfg[PlotOptions] = PlotOptions(bar = PlotOptionsBar(
				pointWidth = width,
				pointPadding = padding,
				dataLabels = PlotOptionsBarDataLabels(
					style = js.Dynamic.literal(fontSize = "0.8em"),
					padding = 0,
					verticalAlign = "bottom",
					enabled = true,
					inside = false,
					overflow = "none",
					format = "{y:.2f}")))
			override val credits    : Cfg[Credits]     = Credits(enabled = false)
			override val chart      : Cfg[Chart]       = Chart(
				`type` = "bar",
				height = (xLabels.size + 1) * (width + padding) + 30
			)
			override val title      : Cfg[Title]       = Title(text = null)

			/*_*/
			override val xAxis  : CfgArray[XAxis] = js.Array(xa)
			override val yAxis  : CfgArray[YAxis] = js.Array(ya)
			/*_*/
			override val tooltip: Cfg[Tooltip]    = Tooltip(shared = true)
			/*_*/
			override val series : SeriesCfg       = (for {
				(name, xs) <- xSeries
				series <- List(
					SeriesBar(
						name = name,
						data = xs.map(_._1).toJSArray): AnySeries,
					SeriesErrorbar(
						name = s"$name error",
						stemWidth = 2.0,
						whiskerLength = width / 3,
						data = xs.map { case (value, error) => new ErrorBarData {
							// XXX negative or 0 in log scale is an error so we clamp to value
							override val low    =
								if (logScale && (error >= value)) Double.MinPositiveValue
								else value - error
							override val high   = value + error
							override val range  = value
							override val actual = error
						}
						}.toJSArray,

						tooltip = SeriesErrorbarTooltip(
							pointFormatter = { x => s"±${x.actual} (${(x.actual / x.range) * 100}%)" }: ThisFunction0[ErrorBarData, String]
						)
					): AnySeries
				)
			} yield series).toJSArray
			/*_*/
		}))
		// to fix issues with binding to a detached DOM element
		// XXX breaks initial animation
		window.requestAnimationFrame { _ => chart.reflow() }
		()
	}

}
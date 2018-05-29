package net.kurobako.jmhv

import com.thoughtworks.binding.Binding.{Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Select, Table}
import shapeless.{Nat, Sized}

object DomBindings {


	/*_*/
	@dom def dropDown[A](xs: Seq[A], v: Var[Option[A]], default: String)
						(implicit show: A => String): Binding[Select] = {
		dropDown[Option[A]](None +: xs.map {Some(_)}, v) { x => x.map(show).getOrElse(default) }.bind
	}
	/*_*/


	/*_*/
	@dom def dropDown[A](xs: Seq[A], v: Var[A])
						(implicit show: A => String): Binding[Select] = {
		dropDownF[A](xs, v.bind, {v.value = _}).bind
	}
	/*_*/

	/*_*/
	@dom def dropDownF[A](xs: Seq[A], x: A, f: A => Unit)
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

}
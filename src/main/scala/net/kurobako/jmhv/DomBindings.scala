package net.kurobako.jmhv

import com.thoughtworks.binding.Binding.{Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Select, Table}
import shapeless.{Nat, Sized}

object DomBindings {

	@dom def renderDropDown[A](xs: Seq[A], x: Var[A])
							  (implicit read: String => A, show: A => String): Binding[Select] = {
		val os = Constants(xs.map(show): _*).map { name =>
			<option value={name} selected={x.value == name}>
				{name}
			</option>
		}
		val select: Select = <select>
			{os}
		</select>
		select.onchange = { _: Event => x.value = read(select.options(select.selectedIndex).text) }
		select
	}

	@dom def renderTable[A, N <: Nat](header: Sized[IndexedSeq[String], N],
									  xs: IndexedSeq[Sized[IndexedSeq[A], N]], cutoff: Int)(f: A => String): Binding[Table] = {


		val headers = Constants(Seq.fill(xs.size / cutoff)(header).flatten: _*)
			.map { h =>
				<th>
					{h}
				</th>
			} // .flatMap { e => Constants(Seq.fill(grouped.size)(e): _*) }

		val grouped = xs.map {_.unsized}.grouped(cutoff).toIndexedSeq.transpose
		val xxs = grouped.map { rows =>
			for {
				row <- rows
				cell <- row
			} yield f(cell)
		}

		@dom def mkRow(cs: IndexedSeq[String]) = {


			<tr>
				{Constants(cs: _*).map { c =>
				<td>
					{c}
				</td>
			}}
			</tr>
		}

		<table>
			<tr>
				{headers}
			</tr>{Constants(xxs: _*).mapBinding {mkRow}}
		</table>
	}

}

package net.kurobako.jmhv

import com.thoughtworks.binding.Binding.{Constants, Var}
import com.thoughtworks.binding.{Binding, dom}
import org.scalajs.dom.Event
import org.scalajs.dom.html.Select

object DomBindings {

	@dom def renderDropDown[A](xs: Seq[A], x: Var[A])
							  (implicit read: String => A, show: A => String): Binding[Select] = {
		val os = Constants(xs.map(show): _*).map { name =>
			<option value={name} selected={x.value == name }>{name}</option>
		}
		val select: Select = <select>{os}</select>
		select.onchange = { _: Event => x.value = read(select.options(select.selectedIndex).text) }
		select
	}

}

package net.kurobako

import com.thoughtworks.binding.Binding

package object jmhv {

	// https://stackoverflow.com/a/42617445/896997
	implicit def makeIntellijHappy[T<:org.scalajs.dom.raw.Node](x: scala.xml.Node): Binding[T] =
		throw new AssertionError("This should never execute.")
}

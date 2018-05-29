jmh-view
========

Embeddable JMH report view written in ScalaJS

## How to use

Create a `<div>` and initialise it:

```html
<div id="container"></div>
<script>
    JMHView.setup("container", "data.json")
</script>
```


### Project highlight

This project attempts to bring functional programming and static type checking to web development 
by doing the following:

 * Typesafe 
 	* Type-checked HTML tags
 	* Table creation via `shapeless.Sized`
 	* JSON to model mapping with `shapeless.tag`
 * Performance
    * Precise data binding with Binding.scala
 
Essentially, if it compiles, it probably works.



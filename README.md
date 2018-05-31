jmh-view
========

Embeddable JMH report view written in ScalaJS

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

## How to use

First, clone build the project, see the build section for more information.

Create a `<div>` and initialise it:

```html
<div id="container"></div>
<script>
    JMHView.setup("container", "data.json")
    // alternatively: 
    JMHView.setup("container", "http://url/to/my/data.json")
	// JS objects are supported too  
	JMHView.setup("container", [ /*..*/ ] )
</script>
```

The obtain the JSON formatted result in JMH, add the `-rf json` flag:

    java -jar benchmarks.jar -rf json
    
Alternatively, configure your options like so:

```scala
new OptionsBuilder()
    // ...
    .resultFormat(ResultFormatType.JSON)
    .result("docs/data.json")

```

## How to build

The project uses sbt; to create a minified bundle do the following:

    sbt fullOptJS
    
The following files will be generated:

     src/main/resources/jmv-view.css
     target/scala-2.12/jmh-view-opt.js
     target/scala-2.12/jmh-view-jsdeps.js
     
If you are not sure how to use these, take a look at `src/main/resources/index-prod.html`.

Be sure to modify paths so that it works in your hosting environment.



## Licence

    Copyright 2018 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



lazy val commonSettings = Seq(
	scalaVersion := "2.12.6",
	scalacOptions ++= Seq(
		"-target:jvm-1.8",
		"-encoding", "UTF-8",
		"-unchecked",
		"-deprecation",
		"-explaintypes",
		"-feature",
		"-Xfuture",

		"-language:existentials",
		"-language:experimental.macros",
		"-language:higherKinds",
		"-language:postfixOps",
		"-language:implicitConversions",

		"-Xlint:adapted-args",
		"-Xlint:by-name-right-associative",
		"-Xlint:constant",
		"-Xlint:delayedinit-select",
		"-Xlint:doc-detached",
		"-Xlint:inaccessible",
		"-Xlint:infer-any",
		"-Xlint:missing-interpolator",
		"-Xlint:nullary-override",
		"-Xlint:nullary-unit",
		"-Xlint:option-implicit",
		"-Xlint:package-object-classes",
		"-Xlint:poly-implicit-overload",
		"-Xlint:private-shadow",
		"-Xlint:stars-align",
		"-Xlint:type-parameter-shadow",
		"-Xlint:unsound-match",

		"-Yno-adapted-args",
		// "-Ywarn-dead-code", // does not work well with macros
		"-Ywarn-extra-implicit",
		"-Ywarn-inaccessible",
		"-Ywarn-infer-any",
		"-Ywarn-nullary-override",
		"-Ywarn-nullary-unit",
		"-Ywarn-numeric-widen",
		"-Ywarn-unused:implicits",
		"-Ywarn-unused:imports",
		"-Ywarn-unused:locals",
		"-Ywarn-unused:params",
		"-Ywarn-unused:patvars",
		"-Ywarn-unused:privates",
		"-Ywarn-value-discard",
		"-Ypartial-unification",
	),
)

lazy val `jmh-view` = project.in(file("."))
	.enablePlugins(WorkbenchPlugin)
	.enablePlugins(SbtWeb)
	.enablePlugins(ScalaJSPlugin)
	.settings(
		addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
		addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
		commonSettings,
		scalaJSUseMainModuleInitializer := true,
		skip in packageJSDependencies := false,
		workbenchDefaultRootObject := Some(("classes/index-dev.html", "target/scala-2.12")),
		libraryDependencies ++= Seq(
			"com.chuusai" %%% "shapeless" % "2.3.3",
			"org.typelevel" %%% "cats-core" % "1.1.0",
			"com.lihaoyi" %%% "upickle" % "0.6.6",
			"com.lihaoyi" %%% "pprint" % "0.5.3",
			"com.beachape" %%% "enumeratum" % "1.5.13",

			"com.thoughtworks.binding" %%% "dom" % "11.0.1",

			// facade
			"org.scala-js" %%% "scalajs-dom" % "0.9.5",
			"com.github.karasiq" %%% "scalajs-highcharts" % "1.2.1",

			"org.scalatest" %% "scalatest" % "3.0.1" % Test,
		),
		jsDependencies ++= Seq(
			//			"org.webjars" % "jquery" % "2.2.1" / "jquery.min.js", // minified "jquery.min.js",
			"org.webjars" % "highcharts" % "5.0.14" / "5.0.14/highcharts.js" //dependsOn "jquery.min.js"
		)
	)




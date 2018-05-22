


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
		"-Ywarn-dead-code",
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
	.enablePlugins(SbtWeb)
	.enablePlugins(ScalaJSPlugin)
	.settings(
		commonSettings,
		scalaJSUseMainModuleInitializer := true,
		skip in packageJSDependencies := false,
		libraryDependencies ++= Seq(
			"org.scala-js" %%% "scalajs-dom" % "0.9.5",
//			"org.querki" %%% "jquery-facade" % "1.2",
			"com.github.karasiq" %%% "scalajs-highcharts" % "1.2.1",
		),
		jsDependencies ++= Seq(
			"org.webjars" % "jquery" % "2.2.1" / "jquery.min.js", // minified "jquery.min.js",
			"org.webjars" % "highcharts" % "5.0.14" / "5.0.14/highcharts.js" dependsOn "jquery.min.js"
		)
		// TODO add dependencies for web module
	)




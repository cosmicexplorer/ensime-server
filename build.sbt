lazy val api = project
  .dependsOn(json, `s-express`)
  .settings(
    licenses := Seq(LGPL3),
    resourcesOnCompilerCp(Compile),
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-ioeffect" % "2.5.0",
    )
  )

lazy val json = project.settings(
  licenses := Seq(LGPL3),
  libraryDependencies ++= Seq(
    "com.chuusai"    %% "shapeless"  % shapelessVersion,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
    // TODO: is this necessary? This is the version used in the containing pants project.
    "io.spray" %% "spray-json" % "1.3.4"
  )
)

lazy val `s-express` = project
  .settings(
    licenses := Seq(LGPL3),
    libraryDependencies ++= Seq(
      "com.chuusai"    %% "shapeless"  % shapelessVersion,
      "com.lihaoyi"    %% "fastparse"  % "0.4.4",
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    )
  )

lazy val monkeys = project
  .disablePlugins(ScalafixPlugin)

lazy val util = project
  .dependsOn(api, monkeys)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"        %% "akka-actor"    % akkaVersion,
      "org.scala-lang"           % "scala-compiler" % scalaVersion.value,
      "com.google.code.findbugs" % "jsr305"         % "3.0.2" % "provided"
    ) ++ logback
  )

lazy val testutil = project
  .dependsOn(util)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"   % akkaVersion
    ) ++ sensibleTestLibs(Compile)
  )

lazy val core = project
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    api,
    `s-express`,
    monkeys,
    util,
    api      % "test->test", // for the interpolator
    testutil % "test,it",
    // depend on "it" dependencies in Test or sbt adds them to the release deps!
    // https://github.com/sbt/sbt/issues/1888
    testingEmpty  % "test,it",
    testingSimple % "test,it",
    // test config needed to get the test jar
    testingSimpleJar % "test,it->test",
    testingTiming    % "test,it",
    testingMacros    % "test,it",
    testingShapeless % "test,it",
    testingJava      % "test,it"
  )
  .enableIntegrationTests
  .settings(
    libraryDependencies ++= Seq(
      "com.orientechnologies" % "orientdb-graphdb" % orientVersion
        exclude ("commons-collections", "commons-collections")
        exclude ("commons-beanutils", "commons-beanutils"),
      "org.apache.lucene" % "lucene-core"             % luceneVersion,
      "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
      "org.ow2.asm"       % "asm-commons"             % "5.2",
      "org.ow2.asm"       % "asm-util"                % "5.2",
      "org.scala-lang"    % "scalap"                  % scalaVersion.value,
      "com.typesafe.akka" %% "akka-actor"             % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion, {
        // see notes in https://github.com/ensime/ensime-server/pull/1446
        val suffix = CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 11)) => "2.11.8"
          case _             => "2.12.2"
        }
        "org.scala-refactoring" % s"org.scala-refactoring.library_${suffix}" % "0.13.0"
      },
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
    )
  )

lazy val server = project
  .dependsOn(
    core,
    lsp,
    `s-express` % "test->test",
    // depend on "it" dependencies in Test or sbt adds them to the release deps!
    // https://github.com/sbt/sbt/issues/1888
    core        % "test->test",
    core        % "it->it",
    testingDocs % "test,it"
  )
  .enableIntegrationTests
  .settings(
    libraryDependencies ++= Seq(
      "io.netty" % "netty-transport"  % nettyVersion,
      "io.netty" % "netty-handler"    % nettyVersion,
      "io.netty" % "netty-codec-http" % nettyVersion
    )
  )

lazy val lsp = project
  .dependsOn(core, json)

// the projects used in integration tests
lazy val testingEmpty = testingProject("testing/empty")
lazy val testingSimple = testingProject("testing/simple") settings (
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test intransitive ()
)
lazy val testingSimpleJar = testingProject("testing/simpleJar").settings(
  exportJars := true,
  ensimeUseTarget in Compile := Some(
    (artifactPath in (Compile, packageBin)).value
  ),
  ensimeUseTarget in Test := Some((artifactPath in (Test, packageBin)).value)
)
lazy val testingImplicits = testingProject("testing/implicits")
lazy val testingTiming    = testingProject("testing/timing")
lazy val testingMacros = testingProject("testing/macros") settings (
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)
lazy val testingShapeless = testingProject("testing/shapeless").settings(
  libraryDependencies += "com.chuusai" %% "shapeless" % shapelessVersion
)
lazy val testingDocs = testingProject("testing/docs").settings(
  dependencyOverrides ++= Seq("com.google.guava" % "guava" % "18.0"),
  libraryDependencies ++= Seq(
    "com.github.dvdme" % "ForecastIOLib" % "1.5.1" intransitive (),
    "com.google.guava" % "guava"         % "18.0"
  )
)
lazy val testingJava = testingProject("testing/java").settings(
  crossPaths := false,
  autoScalaLibrary := false,
  libraryDependencies := Nil
)

// root project
name := "ensime"
dependsOn(server)
publishLocal := {}
publish := {}
test in assembly := {}
aggregate in assembly := false
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "semanticdb.semanticidx") => MergeStrategy.discard
  case PathList("META-INF", "semanticdb", _*)         => MergeStrategy.discard
  case PathList("org", "apache", "commons", "vfs2", xs @ _*) =>
    MergeStrategy.first
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.concat
  case PathList("LICENSE")         => MergeStrategy.concat
  case PathList("LICENSE.apache2") => MergeStrategy.first
  case PathList("NOTICE")          => MergeStrategy.concat
  case PathList("deriving.conf")   => MergeStrategy.concat
  case other                       => MergeStrategy.defaultMergeStrategy(other)
}
assemblyExcludedJars in assembly := {
  val everything = (fullClasspath in assembly).value
  everything.filter { attr =>
    val n = attr.data.getName
    n.startsWith("scala-library") | n.startsWith("scala-compiler") |
      n.startsWith("scala-reflect") | n.startsWith("scalap")
  }
}
assemblyJarName in assembly := s"ensime_${scalaBinaryVersion.value}-${version.value}-assembly.jar"

TaskKey[Unit](
  "prewarm",
  "Uses this build to create a cache, speeding up integration tests"
) := {
  // would be good to be able to do this without exiting the JVM...
  val sv = scalaVersion.value
  val cmd =
    if (sys.env.contains("APPVEYOR")) """C:\sbt\bin\sbt.bat"""
    else "sbt"
  sys.process
    .Process(
      Seq(cmd, s"++$sv!", "ensimeConfig", "ensimeServerIndex"),
      file("testing/cache")
    )
    .!
}

addCommandAlias("fmt",
                "all scalafmtSbt compile:scalafmt test:scalafmt it:scalafmt")
addCommandAlias("lint", "all compile:scalafixCli test:scalafixCli")

addCommandAlias(
  "check",
  ";scalafmtSbtCheck ;compile:scalafmtCheck ;test:scalafmtCheck ;it:scalafmtCheck"
    + " ;compile:scalafixCli --test ;test:scalafixCli --test"
)
addCommandAlias("prep", ";ensimeConfig ;assembly ;prewarm")
addCommandAlias("cpl", "all compile test:compile it:compile")
addCommandAlias(
  "check",
  "all scalafmtSbtCheck compile:scalafmtCheck test:scalafmtCheck it:scalafmtCheck"
)
addCommandAlias("lint", ";compile:scalafixTest ;test:scalafixTest")
addCommandAlias("fix", "all compile:scalafixCli test:scalafixCli")
addCommandAlias("tests", "all test it:test")
// not really what is used in CI, but close enough...
addCommandAlias("ci", ";check ;prep ;cpl ;doc ;tests")

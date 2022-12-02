scalaVersion := "2.13.6"
resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/sunpj/sharest"

val CatsVersion = "2.9.0"
val AnormVersion = "2.6.10"
val PlayVersion = "2.8.18"

lazy val root = (project in file("."))
  .aggregate(playModule)

lazy val playModule = (project in file("play"))
  .settings(
    name := "sharest",
    organization := "com.github.sunpj",
    version := "0.0.1",
    scalaVersion := "2.13.6",
    routesImport += "com.github.sunpj.sharest.controllers.CustomBinders._",
    libraryDependencies ++= Seq(
      jdbc,
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.playframework.anorm" %% "anorm-akka" % AnormVersion,
    ),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at "https://maven.pkg.github.com/sunpj/sharest")
      else
        Some("releases" at "https://maven.pkg.github.com/sunpj/sharest")
    }
  )
  .enablePlugins(PlayScala)

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.6"
resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/sunpj/shaytan"
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

val CatsVersion = "2.9.0"
val AnormVersion = "2.6.10"
val PlayVersion = "2.8.18"

lazy val root = (project in file("."))
  .aggregate(playModule)

lazy val playModule = (project in file("play"))
  .settings(
    name := "shaytan",
    scalaVersion := "2.13.6",
    routesImport += "com.github.sunpj.shaytan.controllers.CustomBinders._",
    libraryDependencies ++= Seq(
      jdbc,
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.playframework.anorm" %% "anorm-akka" % AnormVersion,
    )
  )
  .enablePlugins(PlayScala)

publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at "https://maven.pkg.github.com/sunpj/shaytan")
  else
    Some("releases" at "https://maven.pkg.github.com/sunpj/shaytan")
}

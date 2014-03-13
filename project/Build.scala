import sbt._
import sbt.Keys._
import xerial.sbt.Sonatype.SonatypeKeys._

object BuildSettings{

  val buildVersion = scala.io.Source.fromFile("version").mkString.trim

  val buildSettings = Defaults.defaultSettings ++ Seq(
    version := buildVersion,
    scalaVersion := "2.10.2",
    scalacOptions ++= Seq("-unchecked", "-deprecation" /*, "-Xlog-implicits", "-Yinfer-debug", "-Xprint:typer", "-Yinfer-debug", "-Xlog-implicits", "-Xprint:typer"*/ ),
    scalacOptions in (Compile, doc) ++= Seq("-unchecked", "-deprecation", "-implicits"),
    shellPrompt := ShellPrompt.buildShellPrompt)
}

object Dependencies{
  val liftCommon = "net.liftweb" % "lift-common_2.10" % "2.5.1"

  val dependencySettings = Seq(
    libraryDependencies := Seq(liftCommon)
  )
}

object Colors {

  import scala.Console._

  lazy val isANSISupported = {
    Option(System.getProperty("sbt.log.noformat")).map(_ != "true").orElse {
      Option(System.getProperty("os.name"))
        .map(_.toLowerCase)
        .filter(_.contains("windows"))
        .map(_ => false)
    }.getOrElse(true)
  }

  def red(str: String): String = if (isANSISupported) (RED + str + RESET) else str
  def blue(str: String): String = if (isANSISupported) (BLUE + str + RESET) else str
  def cyan(str: String): String = if (isANSISupported) (CYAN + str + RESET) else str
  def green(str: String): String = if (isANSISupported) (GREEN + str + RESET) else str
  def magenta(str: String): String = if (isANSISupported) (MAGENTA + str + RESET) else str
  def white(str: String): String = if (isANSISupported) (WHITE + str + RESET) else str
  def black(str: String): String = if (isANSISupported) (BLACK + str + RESET) else str
  def yellow(str: String): String = if (isANSISupported) (YELLOW + str + RESET) else str

}

// Shell prompt which show the current project,
// git branch and build version
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info(s: => String) {}

    def error(s: => String) {}

    def buffer[T](f: => T): T = f
  }

  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
    getOrElse "-" stripPrefix "## ")

  val buildShellPrompt = {
    (state: State) =>
      {
        val currProject = Project.extract(state).currentProject.id
        ("%s "+ Colors.green("(%s)") + ": %s> ").format(
          currProject, currBranch, BuildSettings.buildVersion)
      }
  }
}

object Publish {
  lazy val settings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    organization := "com.scalableminds",
    organizationName := "scalable minds UG (haftungsbeschränkt) & Co. KG",
    organizationHomepage := Some(url("http://scalableminds.com")),
    startYear := Some(2014),
    profileName := "com.scalableminds",
    description := "Play framework 2.x module to execute mongo DB evolutions via comand line",
    licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/sclableminds/play-mongev")),
    scmInfo := Some(ScmInfo(url("https://github.com/sclableminds/play-mongev"), "https://github.com/scalableminds/play-mongev.git")),
    pomExtra := (
      <developers>
        <developer>
          <id>tmbo</id>
          <name>Tom Bocklisch</name>
          <email>tom.bocklisch@scalableminds.com</email>
          <url>http://github.com/tmbo</url>
        </developer>
      </developers>
    )
  )
}

object ApplicationBuild extends Build {
  val utilFox = Project("util-fox", file("."), settings = 
    BuildSettings.buildSettings ++ 
    xerial.sbt.Sonatype.sonatypeSettings ++ 
    Dependencies.dependencySettings ++
    Publish.settings)
}

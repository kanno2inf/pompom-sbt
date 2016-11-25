package info.kion2k.pom2sbt

import java.io.File

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConversions._
import scala.util.matching.Regex

object PomLoader {
  def parse(file: File): PomData = {
    val xml = Jsoup.parse(file, "UTF-8")
    PomData(parseProfiles(xml), parseProperties(xml), parseDependencies(xml))
  }

  def shrinkElementString(e: Element): String = {
    {
      Seq(
        s"${e.tagName()}",
        s"${e.ownText()}"
      ) ++ e.children().map(shrinkElementString)
    }.filter(_.nonEmpty).mkString(":")
  }

  // parse profiles
  def parseProfiles(xml: Document): Map[String, Profile] = {
    val elProfiles = xml.select("profile")
    elProfiles.map { p =>
      val id = p.select("id").head.ownText()

      val elActivations = p.select("activation").flatMap(_.children())
      val activations = elActivations.map(p => (p.tagName(), shrinkElementString(p))).toMap

      val elProperties = p.select("properties").flatMap(_.children())
      val properties = elProperties.map(p => (p.tagName(), p.ownText())).toMap
      (id, Profile(id, activations, properties))
    }.toMap
  }

  // parse properties
  def parseProperties(xml: Document): Map[String, String] = {
    val elProperties = xml.select(":not(profile) > properties").flatMap(_.children())
    elProperties.map(p => (p.tagName(), p.ownText())).toMap
  }

  // parse dependencies
  def parseDependencies(xml: Document): Seq[Seq[String]] = {
    val elDependencies = xml.select("dependency")
    val dependencyTags = Seq("groupId", "artifactId", "version", "scope")
    elDependencies.map { d =>
      dependencyTags.flatMap { tag =>
        val t = d.select(tag)
        if (t.isEmpty) None
        else Some(t.head.ownText())
      }
    }
  }
}

case class Profile(id: String, activations: Map[String, String], properties: Map[String, String])

case class PomData(profiles: Map[String, Profile], properties: Map[String, String], dependencies: Seq[Seq[String]])

object SbtConverter {
  def convert(pom: PomData): String = {
    Seq(
      convertProfiles(pom.profiles),
      convertProperties(pom.properties),
      convertDependencies(pom.dependencies)
    ).mkString("\n")
  }

  // convert to java option style
  def convertProfiles(profiles: Map[String, Profile]): String = {
    def formatSystemProp(id: String, value: String): String = s"-D$id=$value"

    def formatSystemProps(props: Map[String, String]): String = props.map { case (k, v) => formatSystemProp(k, v) }.mkString(" ")

    def formatActivations(activation: Map[String, String]): String = activation.map { case (_, v) => v }.mkString(", ")

    val sbtProfiles = profiles.flatMap { case (id, profile) =>
      Seq(
        s"$id",
        s"\t${formatActivations(profile.activations)}",
        s"\t${formatSystemProps(profile.properties)}"
      )
    }
    Seq(
      "/*  system properties option",
      s"${sbtProfiles.mkString("\n")}",
      "*/"
    ).mkString("\n")
  }

  def escapePropertyName(name: String): String = {
    val words = name.split('.')
    words.head + words.tail.map(_.capitalize).mkString
  }

  // convert to sbt style
  def convertProperties(properties: Map[String, String]): String = {
    def formatValState(name: String, value: String): String = "val %s = \"%s\"".format(escapePropertyName(name), value)

    val sbtProperties = properties.map { case (k, v) => formatValState(k, v) }
    sbtProperties.mkString("\n")
  }

  // convert to sbt style
  val variableRegexText = "\\$\\{(.+)\\}"
  val variableRegex: Regex = variableRegexText.r

  def convertDependencies(dependencies: Seq[Seq[String]]): String = {
    def formatPropertyState(partOfState: String): String = {
      if (!partOfState.contains("$")) return "\"%s\"".format(partOfState)
      val isMatches = partOfState.matches(variableRegexText)
      variableRegex.replaceAllIn(partOfState, m => {
        val escaped = escapePropertyName(m.group(1))
        if (isMatches) escaped
        else "s\"\\${%s}\"".format(escaped)
      })
    }

    def formatDependencyState(dependency: Seq[String]): String = dependency.map(formatPropertyState).mkString(" % ")

    val sbtDependencies = dependencies.map(d => s"    ${formatDependencyState(d)}")
    Seq(
      "libraryDependencies ++= Seq(",
      s"${sbtDependencies.mkString(",\n")}",
      s")"
    ).mkString("\n")
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println(s"[Usage]: java -jar *.jar POM_XML_PATH")
      sys.exit(1)
    }

    val pom = PomLoader.parse(new File(args.head))
    println(SbtConverter.convert(pom))
  }
}

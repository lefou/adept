import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

trait WithScala210 {
  def adeptVersion = "0.0.2013.08.29"
  def scalaVersion = "2.10.2"
  def scalaBinVersion = "2.10"
  def json4sVersion = "3.2.5"
  def akkaVersion = "2.0.5"
  def sprayVersion = "1.2-M8"

  def akkaActor = s"mvn:com.typesafe.akka:akka-actor_${scalaBinVersion}:2.1.4"
}

trait WithScala29 extends WithScala210 {
  override def adeptVersion = "0.0.2013.08.29"
  override def scalaVersion = "2.9.2"
  override def scalaBinVersion = "2.9.2"
  override def json4sVersion = "3.2.4"
  override def akkaVersion = "2.0.5"
  override def sprayVersion = "1.2-M8"

  override def akkaActor = s"akka:com.typesafe.akka:akka-actor:${akkaVersion}"
}

class Adept(implicit _project: Project) extends WithScala210 {

  import addons.scala._

  val scalaCompiler = Scalac.compilerClasspath(scalaVersion)

  def schemeHandler {
    // SchemeHandler("aether", AetherSchemeHandler.resolveAndCreate())
    SchemeHandler("akka", new MvnSchemeHandler(repos = Seq("http://repo.typesafe.com/typesafe/releases")))
    SchemeHandler("spray", new MvnSchemeHandler(repos = Seq("http://repo.spray.io")))
  }

  val coreDeps =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    "mvn:org.apache.ivy:ivy:2.3.0-rc1" ~
    "mvn:org.eclipse.jgit:org.eclipse.jgit:2.3.1.201302201838-r" ~
    "mvn:com.jcraft:jsch:0.1.50" ~
    "mvn:org.slf4j:slf4j-api:1.7.5" ~
    "mvn:ch.qos.logback:logback-core:1.0.9" ~
    "mvn:ch.qos.logback:logback-classic:1.0.9" ~
    "mvn:com.typesafe:config:1.0.2" ~
    s"mvn:org.json4s:json4s-native_${scalaBinVersion}:${json4sVersion}" ~
    s"mvn:org.json4s:json4s-core_${scalaBinVersion}:${json4sVersion}" ~
    s"mvn:org.json4s:json4s-ast_${scalaBinVersion}:${json4sVersion}" ~
    akkaActor ~
    s"spray:io.spray:spray-http:${sprayVersion}" ~
    s"spray:io.spray:spray-util:${sprayVersion}" ~
    s"spray:io.spray:spray-can:${sprayVersion}"

  val cliDeps =
    coreDeps ~
    Path[Adept](s"adept-core/target/adept-core-${scalaBinVersion}-${adeptVersion}.jar")

    // spray: 2.9.3 ==1.0x 2.10 == 1.1x currently

  def clean() {
    Target("phony:clean").evictCache exec {
      AntDelete(dir = Path("target"))
    }
  }

  def compile(compileCp: TargetRefs = "compileCp", sources: TargetRefs = "scan:src/main/scala") {

    Target("phony:compile" + scalaBinVersion).cacheable dependsOn scalaCompiler ~ compileCp ~ sources exec {
      Scalac(compilerClasspath = scalaCompiler.files,
        classpath = compileCp.files,
        sources = sources.files,
        debugInfo = "vars",
        fork = true,
        destDir = Path("target/classes-" + scalaBinVersion)
      )
    }
  }

  class Pack(name: String, version: String) {
    val jar = Path(s"target/${name}-${scalaBinVersion}-${version}.jar")
  }

  def pack(name: String, version: String = adeptVersion, dependsOn: TargetRefs = "compile" + scalaBinVersion): Pack = {
    val pack = new Pack(name, version)
    val resources = "scan:src/main/resources"
    val classes = s"target/classes-${scalaBinVersion}"
    val tJar = Target(pack.jar) dependsOn dependsOn ~ resources ~ s"scan:${classes}" exec { ctx: TargetContext =>
      AntJar(destFile = ctx.targetFile.get,
        baseDir = Path(classes),
        fileSets =
          if(resources.files.isEmpty) Seq()
          else Seq(AntFileSet(dir = Path("src/main/resources")))
      )
    }
    Target("phony:jar") dependsOn tJar
    pack
  }

  def repl(classpath: TargetRefs = "runtimeCp") {
    Target("phony:repl" + scalaBinVersion) dependsOn classpath ~ scalaCompiler exec {
      ScalaRepl(replClasspath = scalaCompiler.files,
        classpath = classpath.files
      )
    }
  }

}

import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

class Adept(implicit _project: Project) {

  import addons.scala._

  val adeptVersion = "0.0.2013.08.29"
  val scalaVersion = "2.10.2"
  val scalaBinVersion = "2.10"
  val json4sVersion = "3.2.5"
  val akkaVersion = "2.0.5"
  val sprayVersion = "1.2-M8"

  val scalaCompiler = Scalac.compilerClasspath(scalaVersion)

  // SchemeHandler("aether", AetherSchemeHandler.resolveAndCreate())
  SchemeHandler("akka", new MvnSchemeHandler(repos = Seq("http://repo.typesafe.com/typesafe/releases")))
  SchemeHandler("spray", new MvnSchemeHandler(repos = Seq("http://repo.spray.io")))

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
    // s"akka:com.typesafe.akka:akka-actor:${akkaVersion}" ~
    s"mvn:com.typesafe.akka:akka-actor_${scalaBinVersion}:2.1.4" ~
    s"spray:io.spray:spray-http:${sprayVersion}" ~
    s"spray:io.spray:spray-util:${sprayVersion}" ~
    s"spray:io.spray:spray-can:${sprayVersion}"

  val cliDeps =
    coreDeps ~
    Path[Adept](s"adept-core/target/adept-core-${adeptVersion}.jar")

    // spray: 2.9.3 ==1.0x 2.10 == 1.1x currently

  def clean() {
    Target("phony:clean").evictCache exec {
      AntDelete(dir = Path("target"))
    }
  }

  def compile(compileCp: String = "compileCp", sources: String = "scan:src/main/scala") {

    Target("phony:compile").cacheable dependsOn scalaCompiler ~ compileCp ~ sources exec {
      Scalac(compilerClasspath = scalaCompiler.files,
        classpath = compileCp.files,
        sources = sources.files,
        debugInfo = "vars",
        destDir = Path("target/classes")
      )
    }
  }

  class Pack(name: String, version: String) {
    val jar = Path(s"target/${name}-${version}.jar")
  }

  def pack(name: String, version: String = adeptVersion, dependsOn: Seq[String] = Seq("compile")): Pack = {
    val pack = new Pack(name, version)
    val resources = "scan:src/main/resources"
    val tJar = Target(pack.jar) dependsOn dependsOn.map(TargetRef(_)) ~ resources exec { ctx: TargetContext =>
      AntJar(destFile = ctx.targetFile.get,
        baseDir = Path("target/classes"),
        fileSets =
          if(resources.files.isEmpty) Seq()
          else Seq(AntFileSet(dir = Path("src/main/resources")))
      )
    }
    Target("phony:jar") dependsOn tJar
    pack
  }

  def repl(cp: String = "runtimeCp") {
    Target("phony:repl") dependsOn cp ~ scalaCompiler exec {
      ScalaRepl(replClasspath = scalaCompiler.files,
        classpath = cp.files
      )
    }
  }

}

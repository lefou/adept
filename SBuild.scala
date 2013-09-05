import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0.9001")
@include("Adept.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val adept = new Adept()

  val mods = Modules("adept-core", "adept-cli")

  Target("phony:clean") dependsOn mods.map{ m => m("clean") }
  Target("phony:jar") dependsOn mods.map{ m => m("jar") }

}

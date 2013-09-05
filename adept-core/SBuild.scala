import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0.9001")
@include("../Adept.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "adept-core"
  val adept = new Adept()

  adept.clean()
  adept.compile()
  val pack = adept.pack(name = namespace)
  adept.repl()

  Target("phony:compileCp") dependsOn adept.coreDeps
  
  ExportDependencies("eclipse.classpath", "compileCp")

  Target("phony:runtimeCp") dependsOn "compileCp" ~ pack.jar

}

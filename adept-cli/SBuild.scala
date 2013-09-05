import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0.9001")
@include("../Adept.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "adept-cli"
  val adept = new Adept()

  Target("phony:compileCp") dependsOn adept.cliDeps

  adept.clean()
  adept.compile()
  adept.pack(name = namespace)

  Target("phony:runCli") dependsOn "jar" ~ "compileCp" exec {
    addons.support.ForkSupport.runJavaAndWait(
      classpath = "compileCp".files ++ "jar".files,
      arguments = Array("adept.cli.Main") ++ Prop("adeptArgs").split(" "),
      interactive = true)
  }

}

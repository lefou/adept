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

  adept.schemeHandler
  adept.clean()
  adept.compile(compileCp = adept.cliDeps)
  val pack = adept.pack(name = namespace)

  Target("phony:runCli-" + adept.scalaBinVersion) dependsOn pack.jar ~ adept.cliDeps exec {
    addons.support.ForkSupport.runJavaAndWait(
      classpath = adept.cliDeps.files ++ pack.jar.files,
      arguments = Array("adept.cli.Main") ++ Prop("adeptArgs").split(" "),
      interactive = true)
  }

}

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

  adept.schemeHandler
  adept.clean()
  adept.compile(compileCp = adept.coreDeps)
  val pack = adept.pack(name = namespace)
  adept.repl(classpath = adept.coreDeps ~ pack.jar)

  ExportDependencies("eclipse.classpath", adept.coreDeps)

  // val adept29 = new Adept() with WithScala29
  // adept29.clean()
  // adept29.compile(compileCp = adept29.coreDeps)
  // val pack29 = adept29.pack(name = namespace)
  // adept29.repl(classpath = adept29.coreDeps ~ pack29.jar)


}

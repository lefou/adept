package adept.core

import java.io._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import adept.core.operations._
import adept.core.models._
import akka.util.FiniteDuration
import adept.utils._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import com.jcraft.jsch.JSch

object Adept {
  def dir(baseDir: File, name: String) = new File(baseDir, name)

  def open(baseDir: File, name: String): Either[String, Adept] = {
    if (exists(baseDir)) {
      Right(new Adept(dir(baseDir, name), name))
    } else {
      Left("no adept directory here: " + baseDir)
    }
  }

  def exists(baseDir: File): Boolean = {
    baseDir.exists && baseDir.isDirectory
  }

  def exists(baseDir: File, name: String): Boolean = {
    exists(baseDir) && {
      repositories(baseDir).find(_.name == name).isDefined
    }
  }

  def repositories(baseDir: File): List[Adept] = {
    if (baseDir.exists && baseDir.isDirectory) {
      baseDir.listFiles().toList
        .filter(d => d != null && d.isDirectory)
        .map(d => new Adept(d, d.getName))
    } else {
      List.empty
    }
  }

  def clone(baseDir: File, name: String, uri: String): Either[String, Adept] = {
    val adeptDir = dir(baseDir, name)
    if (adeptDir.mkdirs()) {
      Git.cloneRepository()
         .setProgressMonitor(new TextProgressMonitor())
         .setURI(uri)
         .setDirectory(adeptDir)
         .call()
      Right(new Adept(adeptDir, name))
    } else {
      Left("could not create directory when cloning: " + adeptDir)
    }
  }

  def init(baseDir: File, name: String): Either[String, Adept] = {
    val adeptDir = dir(baseDir, name)
    if (adeptDir.mkdirs()) {
      val initCommand = Git.init()
       .setDirectory(adeptDir)
       initCommand.call()
       Right(new Adept(adeptDir, name))
    } else {
      Left("could not create directory when initing: " + adeptDir)
    }
  }


  val aritifactPath = "artifacts"

  def artifact(baseDir: File, info: Seq[((Hash, Coordinates, Set[String]), Option[File])], timeout: FiniteDuration) = { //TODO: Either[Seq[File], Seq[File]]  (left is failed, right is successful)
    val hashFiles = info.map{ case ((hash, coords, locations), dest) =>
      (hash, coords, locations, dest.getOrElse{
        val artifactDir = new File(baseDir, aritifactPath)
        artifactDir.mkdirs
        val currentArtifactDir = ModuleFiles.getModuleDir(artifactDir, coords)
        ModuleFiles.createDir(currentArtifactDir)
        new File(currentArtifactDir , hash.value+".jar") //TODO: need a smarter way to store artifacts (imagine 50K jars in one dir!)
      })
    }
    val (existing, nonExisting) = hashFiles.partition{ case (hash, coords, locations, file) =>
      file.exists && Hash.calculate(file) == hash
    }
    for {
      existingFiles <- EitherUtils.reduce[String, File](existing.map{ case (_,_, _, file ) => Right(file) }).right
      downloadedFiles <- EitherUtils.reduce[String, File](adept.download.Download(nonExisting, timeout)).right
    } yield {
      existingFiles ++ downloadedFiles
    }
  }
  def resolveConflicts(modules: Seq[Module]): Seq[Module] = {
    ConflictResolver.prune(modules)
  }
  
  def resolveArtifacts(artifacts: Set[Artifact], configurations: Set[Configuration], confsExpr: String): Seq[Artifact] = {
    //Resolve.modules(dependencies, confsExpr, findModule)._1
    val (allArtifacts, evicted) = Resolve.artifacts(artifacts, configurations, confsExpr) //TODO: handle evicted
    allArtifacts
  }
  
  
  def resolveConfigurations(confsExpr: String, configurations: Set[Configuration]): Set[Configuration] ={
    Resolve.splitConfs(confsExpr).flatMap{ confExpr =>
      Resolve.findMatchingConfs(confExpr, configurations)
    }.toSet
  }
}

class Adept private[adept](val dir: File, val name: String) extends Logging {

  override def toString = {
    "Adept("+name+","+dir.getAbsolutePath+","+ lastCommit+")"
  }

  private lazy val git = Git.open(dir)

  /* add module to adept. return right with file containing module, left on file that could not be created*/
  def add(module: Module): Either[File, File] = {
    Add(dir, module)
  }


  def findModule(coords: Coordinates, hash: Option[Hash] = None): Option[Module] = {
    val file = new File(ModuleFiles.getModuleDir(dir, coords), ModuleFiles.modulesFilename)


    if (file.exists && file.isFile) {
      import org.json4s.native.JsonMethods._
      val maybeModules = Module.readSameCoordinates(parse(file))
      maybeModules.fold(
          error => throw new Exception(error),
          modules => hash.map{ hash =>
            val filtered = modules.filter(_.hash == hash)
            assert(filtered.size < 2, "found more than 1 module with hash: " + hash + " in " + file + " found: " + filtered)
            filtered.headOption
          }.getOrElse{
            if (modules.size > 1) throw new Exception("found more than 1 module: " + hash + " in " + file + " found: " + modules) //TODO: either instead of option as return type and return all the failed modules
            modules.headOption
          }
      )
    } else {
      None
    }
  }

  def resolveModules(dependencies: Set[Dependency], configurations: Set[Configuration], confsExpr: String): Seq[(Module, Set[Configuration])] = {
    val matchedConfs = Resolve.findMatchingConfs(confsExpr, configurations)
    val allModules = Resolve.allModules(dependencies, matchedConfs, findModule) //TODO: handle evicted
    allModules
  }

  def dependencies(module: Module): Set[Module] = {
    Set(module) ++ module.dependencies.par.flatMap{ case Dependency(coords, hash, _)  => //TODO: check if par gives us anything!
      findModule(coords, Some(hash)).toSet.flatMap{ m: Module =>
        dependencies(m)
      }
    }
  }

  private def repo = git.getRepository()

  def lastCommit(allCoords: Set[Coordinates]): Option[Hash] = {
    val paths = allCoords.map{ coords =>
      new File(ModuleFiles.getModuleDir(dir, coords), ModuleFiles.modulesFilename)
    }.filter(f => f.exists && f.isFile).map{ file =>
      file.getAbsolutePath.replace(dir.getAbsolutePath + File.separatorChar, "")
    }.mkString(" ")

    val logIt = git.log()
                   .addPath(paths)
                   .call()
                   .iterator()
    if (logIt.hasNext()) Some(Hash(logIt.next.getName))
    else None
  }
  def lastCommit: Option[Hash] = {
    try {
      val logIt = git.log()
                     .call()
                     .iterator()
      Some(Hash(logIt.next.getName))
    } catch {
      case e: org.eclipse.jgit.api.errors.NoHeadException => None
    }
  }

  lazy val branchName = "master"

  def isLocal: Boolean = {
    try {
      repo.getConfig().getString(
        ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
        ConfigConstants.CONFIG_KEY_REMOTE)
      true
    } catch {
      case e: org.eclipse.jgit.api.errors.InvalidConfigurationException => false
    }
  }

  def pull(): Boolean = {
    val result = git
      .pull()
      .call()
    result.isSuccessful
  }

  def push(repo: String) = {
    val config = git.getRepository.getConfig
    val remote = new RemoteConfig(config, "central")
    val uri = new URIish(repo)
    remote.addURI(uri)
    remote.update(config)
    config.save()
    try {
      SshSessionFactory.setInstance(GitHelpers.sshFactory)
      git.push.setRemote("central").call
    } catch {
      case x: TransportException => {
        println("ssh password required ...")
        SshSessionFactory.setInstance(GitHelpers.interactiveSshFactory)
        git.push.setRemote("central").call
      }
    }
  }

  def commit(msg: String) = {
    val status = git.status()
       .call()
    val noDiff = status.getChanged.isEmpty && status.getAdded.isEmpty
    if (noDiff) {
      Left("nothing to commit")
    } else {
    val revcommit = git
       .commit()
       .setMessage(msg)
       .call()

    Right(Hash(revcommit.name))
    }
  }

}
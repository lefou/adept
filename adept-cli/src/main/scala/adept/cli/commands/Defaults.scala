package adept.cli.commands

import java.io.File
import scala.concurrent.duration._

object Defaults {
  def dir = new File(".adept").getAbsoluteFile()
  val name = "local"
  val conf = "default"
  val timeout = {
    5.minutes
  }
}

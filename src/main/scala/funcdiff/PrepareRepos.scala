package funcdiff

import ammonite.ops._
import lambdanet.ProjectPath
import lambdanet.translation.ImportsResolution.PathMapping
import lambdanet.translation.{
  IRTranslation,
  ImportsResolution,
  PLangTranslation,
  PredicateGraphTranslation,
  QLangTranslation
}
import lambdanet.translation.PredicateGraph.PNodeAllocator
import lambdanet.utils.ProgramParsing
import lambdanet.utils.ProgramParsing.GProject

object PrepareRepos {
  val declarationsDir = pwd / up / "lambda-repos" / "declarations"

  val (baseCtx, libAllocator, _) = QLangTranslation.parseDefaultModule()
  val libExports = {
//    val files = ls(declarationsDir).filter(_.last.endsWith(".d.ts"))
    println("parsing GModules...")
    val GProject(_, modules, mapping0) = ProgramParsing
      .parseGProjectFromRoot(declarationsDir, declarationFileMod = true)
    val nodeFieMap = {
      val nodeFiles = ls(declarationsDir / "node")
        .filter(_.last.endsWith(".d.ts"))
        .map { f =>
          RelPath(f.last.replace(".d.ts", ""))
        }
      nodeFiles.map { f =>
        f -> RelPath("node") / f
      }.toMap
    }
    val mapping = new PathMapping {
      def map(
          currentPath: ProjectPath,
          pathToResolve: ProjectPath
      ): ProjectPath = {
        nodeFieMap.getOrElse(
          pathToResolve,
          mapping0.map(currentPath, pathToResolve)
        )
      }
    }

    println("parsing PModules...")
    val pModules = modules
      .map(m => PLangTranslation.fromGModule(m, libAllocator))
      .map(m => m.path -> m)
      .toMap

    println("imports resolution...")
    ImportsResolution.resolveExports(
      pModules,
      Map(),
      mapping,
      maxIterations = 5
    )
  }
  println("Declaration files parsed.")

  def prepareProject(root: Path) = {
    val libMapping = {
      libExports.map {
        case (p, _) => p.last -> (declarationsDir / p).relativeTo(root)
      }
    }

    def filterTests(path: Path): Boolean = {
      !path.segments.contains("__tests__")
    }

    val p =
      ProgramParsing.parseGProjectFromRoot(root, filter = filterTests)
    val allocator = new PNodeAllocator(forLib = false)
    val irTranslator = new IRTranslation(allocator)
    val irModules = QLangTranslation
      .fromProject(
        p.modules,
        baseCtx,
        Map(),
        allocator,
        p.pathMapping
      )
      .values
      .map(irTranslator.fromQModule)
      .toVector
    val graph = PredicateGraphTranslation.fromIRModules(irModules)
  }

  def main(args: Array[String]): Unit = {
    val projectsDir = pwd / up / "lambda-repos" / "projects"

    (ls ! projectsDir).foreach(f => if (f.isDir) prepareProject(f))
  }

}

package lambdanet.train

import lambdanet._
import lambdanet.translation.PredicateGraph._
import NeuralInference._
import lambdanet.PrepareRepos.ParsedRepos
import lambdanet.SequenceModel.SeqPredictor
import lambdanet.architecture.LabelEncoder.{
  ConstantLabelEncoder,
  RandomLabelEncoder,
  TrainableLabelEncoder
}
import lambdanet.architecture.{NNArchitecture}
import lambdanet.translation.QLang.QModule
import lambdanet.utils.QLangAccuracy.FseAccuracy

import scala.collection.parallel.ForkJoinTaskSupport

case class DataSet(
    nodeForAny: LibTypeNode,
    trainSet: Vector[Datum],
    testSet: Vector[Datum],
) {
  override def toString: String =
    s"train set size: ${trainSet.size}, " +
      s"test set size: ${testSet.size}"
}

object DataSet {
  def loadDataSet(
      taskSupport: Option[ForkJoinTaskSupport],
      architecture: NNArchitecture,
  ): DataSet =
    announced("loadDataSet") {
      import PrepareRepos._
      import ammonite.ops._
      import TrainingLoop.toyMod

      printResult(s"Is toy data set? : $toyMod")

      val repos @ ParsedRepos(libDefs, trainSet, devSet) =
        if (toyMod)
          parseRepos(
            pwd / RelPath("data/toy/trainSet"),
            pwd / RelPath("data/toy/testSet"),
          )
        else
          announced(s"read data set from: $parsedRepoPath") {
            SM.readObjectFromFile[ParsedRepos](parsedRepoPath.toIO)
          }

      val libTypesToPredict: Set[LibTypeNode] =
        selectLibTypes(
          libDefs,
          repos.trainSet.map { _.userAnnots },
          coverageGoal = 0.95
        )

      val data = (trainSet ++ devSet).toVector
        .map {
          case ParsedProject(path, g, qModules, irModules, annotations) =>
            val predictor =
              Predictor(
                g,
                libTypesToPredict,
                libDefs.libNodeType,
                taskSupport
              )
            val nonGenerifyIt = nonGenerify(libDefs)
            val annots1 = annotations
              .mapValuesNow { nonGenerifyIt }
            val libPredSpace = PredictionSpace(
              libTypesToPredict.map(n => PTyVar(n.n.n)) ++ Set(PAny)
            )

//            val seqPredictor = SeqPredictor(
//              irModules,
//              libDefs,
//              libPredSpace,
//              nameEncoder.encode,
//              taskSupport,
//            )
            Datum(path, annots1, qModules, predictor, null)
              .tap(printResult)
        }

      (data.map { d =>
        d.annotations.size * d.inPSpaceRatio
      }.sum / data.map(_.annotations.size.toDouble).sum)
        .tap { r =>
          printResult(s"overall InSpaceRatio = $r")
        }
      //fixme: figure out why some projects have very low inSpaceRatio

      val libAnnots = data.map(_.libAnnots).sum
      val projAnnots = data.map(_.projAnnots).sum
      printResult(
        s"Train set size: ${trainSet.size}, Dev set size: ${devSet.size}"
      )
      printResult(s"$libAnnots library targets, $projAnnots project targets.")

      DataSet(
        LibTypeNode(LibNode(libDefs.nodeForAny)),
        data.take(trainSet.length),
        data.drop(trainSet.length)
      ).tap(printResult)
    }

  def selectLibTypes(
      libDefs: LibDefs,
      annotations: Seq[Map[ProjNode, PType]],
      coverageGoal: Double,
  ): Set[LibTypeNode] = {
    import cats.implicits._

    val usages: Map[PNode, Int] = annotations.par
      .map { p =>
        p.collect { case (_, PTyVar(v)) => v -> 1 }
      }
      .fold(Map[PNode, Int]())(_ |+| _)

    /** sort lib types by their usages */
    val typeFreqs = libDefs.nodeMapping.keys.toVector
      .collect {
        case n if n.isType =>
          (LibTypeNode(LibNode(n)), usages.getOrElse(n, 0))
      }

    val (libTypes, achieved) =
      SM.selectBasedOnFrequency(typeFreqs, coverageGoal)

    printResult(s"Lib types coverage achieved: $achieved")
    printResult(s"Lib types selected (${libTypes.length}): $libTypes")

    libTypes.map(_._1).toSet
  }

  def nonGenerify(libDefs: LibDefs): PType => PType = {
    val funcTypeNode = libDefs.baseCtx.internalSymbols('Function).ty.get
    val objTypeNode = libDefs.baseCtx.internalSymbols('Object).ty.get
    def f(ty: PType): PType = ty match {
      case _: PFuncType   => PTyVar(funcTypeNode)
      case _: PObjectType => PTyVar(objTypeNode)
      case _              => ty
    }
    f
  }

}

case class Datum(
    projectName: ProjectPath,
    annotations: Map[ProjNode, PType],
    qModules: Vector[QModule],
    predictor: Predictor,
    seqPredictor: SeqPredictor,
) {
  val inPSpaceRatio: Double =
    annotations
      .count(
        _._2.pipe(predictor.predictionSpace.allTypes.contains),
      )
      .toDouble / annotations.size

  val distanceToConsts: PNode => Int = {
    Analysis.analyzeGraph(predictor.graph).distanceToConstNode
  }

  def libAnnots: Int = annotations.count(_._2.madeFromLibTypes)
  def projAnnots: Int = annotations.count(!_._2.madeFromLibTypes)

  def showInline: String = {
    val info = s"{name: $projectName, " +
      s"annotations: ${annotations.size}(L:$libAnnots/P:$projAnnots), " +
      s"predicates: ${predictor.graph.predicates.size}, " +
      s"predictionSpace: ${predictor.predictionSpace.size}, " +
      s"inPSpaceRatio: $inPSpaceRatio"
    val outOfSpaceTypes =
      annotations.values.toSet.diff(predictor.predictionSpace.allTypes)
    val extra = if (inPSpaceRatio < 0.5) {
      s", outOfSpace: $outOfSpaceTypes }"
    } else "}"
    info + extra
  }

  override def toString: String = {
    showInline
  }

  def showDetail: String = {
    s"""$showInline
       |${predictor.predictionSpace}
       |""".stripMargin
  }

  val fseAcc: FseAccuracy = FseAccuracy(qModules)
}

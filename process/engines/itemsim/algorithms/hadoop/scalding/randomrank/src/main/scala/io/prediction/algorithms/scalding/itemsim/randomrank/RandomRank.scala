package io.prediction.algorithms.scalding.itemsim.randomrank

import com.twitter.scalding._

import io.prediction.commons.scalding.appdata.{Items, Users}
import io.prediction.commons.scalding.modeldata.ItemSimScores
import io.prediction.commons.filepath.{AlgoFile}

/**
 * Source:
 *
 * Sink:
 *
 * Description:
 *
 * Args:
 * --training_dbType: <string> training_appdata DB type
 * --training_dbName: <string>
 * --training_dbHost: <string> optional
 * --training_dbPort: <int> optional
 *
 * --modeldata_dbType: <string> modeldata DB type
 * --modeldata_dbName: <string>
 * --modeldata_dbHost: <string> optional
 * --modeldata_dbPort <int> optional
 *
 * --hdfsRoot: <string>. Root directory of the HDFS
 *
 * --appid: <int>
 * --engineid: <int>
 * --algoid: <int>
 * --evalid: <int>. optional. Offline Evaluation if evalid is specified
 *
 * --itypes: <string separated by white space>. optional. eg "--itypes type1 type2". If no --itypes specified, then ALL itypes will be used.
 * --numSimilarItems: <int>. number of similar items to be generated
 *
 * --modelSet: <boolean> (true/false). flag to indicate which set
 *
 * Example:
 * hadoop jar PredictionIO-Process-Hadoop-Scala-assembly-0.1.jar io.prediction.algorithms.scalding.itemsim.randomrank.RandomRank --hdfs --training_dbType mongodb --training_dbName predictionio_appdata --training_dbHost localhost --training_dbPort 27017 --modeldata_dbType mongodb --modeldata_dbName predictionio_modeldata --modeldata_dbHost localhost --modeldata_dbPort 27017 --hdfsRoot predictionio/ --appid 1 --engineid 1 --algoid 18 --modelSet true
 */
class RandomRank(args: Args) extends Job(args) {
  /**
   * parse args
   */
  val training_dbTypeArg = args("training_dbType")
  val training_dbNameArg = args("training_dbName")
  val training_dbHostArg = args.optional("training_dbHost")
  val training_dbPortArg = args.optional("training_dbPort") map (x => x.toInt)

  val modeldata_dbTypeArg = args("modeldata_dbType")
  val modeldata_dbNameArg = args("modeldata_dbName")
  val modeldata_dbHostArg = args.optional("modeldata_dbHost")
  val modeldata_dbPortArg = args.optional("modeldata_dbPort") map (x => x.toInt)

  val hdfsRootArg = args("hdfsRoot")

  val appidArg = args("appid").toInt
  val engineidArg = args("engineid").toInt
  val algoidArg = args("algoid").toInt
  val evalidArg = args.optional("evalid") map (x => x.toInt)
  val OFFLINE_EVAL = (evalidArg != None) // offline eval mode

  val preItypesArg = args.list("itypes")
  val itypesArg: Option[List[String]] = if (preItypesArg.mkString(",").length == 0) None else Option(preItypesArg)

  val numSimilarItemsArg = args("numSimilarItems").toInt

  val modelSetArg = args("modelSet").toBoolean

  /**
   * source
   */

  // get appdata
  // NOTE: if OFFLINE_EVAL, read from training set, and use evalid as appid when read Items and U2iActions
  val trainingAppid = if (OFFLINE_EVAL) evalidArg.get else appidArg

  // get items data
  val items2 = Items(
    appId=trainingAppid,
    itypes=itypesArg,
    dbType=training_dbTypeArg,
    dbName=training_dbNameArg,
    dbHost=training_dbHostArg,
    dbPort=training_dbPortArg).readData('iidx, 'itypes)

  val items = Items(
    appId=trainingAppid,
    itypes=itypesArg,
    dbType=training_dbTypeArg,
    dbName=training_dbNameArg,
    dbHost=training_dbHostArg,
    dbPort=training_dbPortArg).readData('iid, 'itypesx)

  /**
   * sink
   */
  val itemSimScores = ItemSimScores(
    dbType=modeldata_dbTypeArg,
    dbName=modeldata_dbNameArg,
    dbHost=modeldata_dbHostArg,
    dbPort=modeldata_dbPortArg)

  /**
   * computation
   */
  val scores = items.crossWithTiny(items2)
    .filter('iid, 'iidx) { fields: (String, String) => fields._1 != fields._2 }
    .map(() -> 'score) { u: Unit => scala.util.Random.nextDouble() }
    .groupBy('iid) { _.sortBy('score).reverse.take(numSimilarItemsArg) }

  // write modeldata
  scores.then( itemSimScores.writeData('iid, 'iidx, 'score, 'itypes, algoidArg, modelSetArg) _ )
}

package fi.vm.sade.omatsivut.hakemus

import fi.vm.sade.omatsivut._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.novus.salat._
import com.novus.salat.global._
import org.joda.time.DateTime

object HakemusRepository extends Logging {

  RegisterJodaTimeConversionHelpers()

  private val settings = AppConfig.loadSettings
  private val hakemukset = settings.hakuAppMongoDb("application")
  private val lomakkeet = settings.hakuAppMongoDb("applicationSystem")

  def getDelimiter(s: String) = if(s.contains("_")) "_" else "-"

  def updateHakemus(hakemus: Hakemus) {
    def clearPrevValues(hakemus: Hakemus) = {
      val query = MongoDBObject("oid" -> hakemus.oid)
      hakemukset.findOne(query).toList.map((hakemus: DBObject) => {
        val toiveet = hakemus.expand[Map[String, String]]("answers.hakutoiveet").getOrElse(Map()).toList
        toiveet.map { case (key, value) => ("answers.hakutoiveet." + key, "") }
      }).head
    }

    def getUpdates(hakemus: Hakemus) = {
      hakemus.hakutoiveet.zipWithIndex.flatMap {
        (t) => t._1.map {
          (elem) => ("answers.hakutoiveet.preference" + (t._2 + 1) + getDelimiter(elem._1) + elem._1, elem._2)
        }
      }.toMap[String, String]
    }

    def updateValues(hakemus: Hakemus, newData: List[(String, String)]) = {
      // TODO validation and identity check
      val query = MongoDBObject("oid" -> hakemus.oid)
      val update = $set(newData:_*)
      hakemukset.update(query, update)
    }

    val clearedValues = clearPrevValues(hakemus)
    val updates = getUpdates(hakemus)
    val combined = clearedValues ++ updates
    updateValues(hakemus, combined.toList)
  }

  def fetchHakemukset(oid: String): List[Hakemus] = {
    ApplicationDaoWrapper.findByPersonOid(oid)
  }

  def fetchHakemuksetOLD(oid: String): List[Hakemus] = {

    val query = MongoDBObject("personOid" -> oid)

    hakemukset.find(query).toList.map((hakemus: DBObject) => {
      val haku = getHaku(hakemus)
      (hakemus, haku)
    }).map((tuple: (DBObject, DBObject)) => {
      val hakem = grater[Hakemus].asObject(tuple._1)
      val toiveet = tuple._1.getAs[Map[String, String]]("answers").get.asDBObject.getAs[Map[String, String]]("hakutoiveet").get
      hakem.hakutoiveet = HakutoiveetConverter.convert(toiveet)
      if (!tuple._2.isEmpty) {
        hakem.haku = Some(grater[Haku].asObject(tuple._2))
      }
      hakem
    })
  }

  private def getHaku(hakemus: DBObject): DBObject = {
    val hakuOid = hakemus.getAs[String]("applicationSystemId")
    val res = lomakkeet.findOne(MongoDBObject("_id" -> hakuOid), MongoDBObject("name" -> 1, "applicationPeriods" -> 1))
    res match {
      case Some(x) => x.asDBObject
      case None => DBObject.empty
    }
  }
}
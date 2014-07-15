package fi.vm.sade.omatsivut.hakemus

import fi.vm.sade.haku.oppija.hakemus.domain.Application
import fi.vm.sade.omatsivut.domain.Hakemus
import scala.collection.JavaConversions._

trait HakemusMerging {
  def mergeWithApplication(hakemus: Hakemus, application: Application) {
    val hakutoiveet: Map[String, String] = application.getPhaseAnswers("hakutoiveet").toMap
    val hakuToiveetWithEmptyValues = hakutoiveet.filterKeys(s => s.startsWith("preference")).mapValues(s => "")
    val hakutoiveetWithoutOldPreferences = hakutoiveet.filterKeys(s => !s.startsWith("preference"))
    val updatedHakutoiveet = hakutoiveetWithoutOldPreferences ++ hakuToiveetWithEmptyValues ++ getUpdates(hakemus)
    application.addVaiheenVastaukset("hakutoiveet", updatedHakutoiveet)
  }

  private def getUpdates(hakemus: Hakemus): Map[String, String] = {
    hakemus.hakutoiveet.zipWithIndex.flatMap {
      (t) => t._1.map {
        (elem) => ("preference" + (t._2 + 1) + getDelimiter(elem._1) + elem._1, elem._2)
      }
    }.toMap[String, String]
  }

  private def getDelimiter(s: String) = if(s.contains("_")) "_" else "-"
}

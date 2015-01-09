package fi.vm.sade.omatsivut.muistilista

import fi.vm.sade.omatsivut.domain.Language
import fi.vm.sade.omatsivut.http.UrlValueCompressor
import fi.vm.sade.omatsivut.json.JsonFormats
import fi.vm.sade.omatsivut.koulutusinformaatio.KoulutusInformaatioComponent
import fi.vm.sade.omatsivut.koulutusinformaatio.domain.Koulutus
import fi.vm.sade.omatsivut.localization.Translations
import fi.vm.sade.omatsivut.tarjonta.TarjontaComponent
import fi.vm.sade.utils.slf4j.Logging
import org.json4s.jackson.Serialization.write

trait MuistilistaServiceComponent {
  this: KoulutusInformaatioComponent with TarjontaComponent =>

  def muistilistaService(language: Language.Language): MuistilistaService

  class MuistilistaService(language: Language.Language) extends JsonFormats with Logging {
    private implicit val lang = language

    def buildMail(muistiLista: Muistilista, url: StringBuffer): String = {
      url + "/muistilista/" + buildUlrEncodedOidString(muistiLista.koids)
      buildMessage(getKoulutuksetWithMuistiLista(muistiLista))
    }

    private def getKoulutuksetWithMuistiLista(muistiLista: Muistilista) = {
      val kieli = muistiLista.kieli
      val oids = muistiLista.koids.toList

      oids.map(k =>
        koulutusInformaatioService.koulutus(k, kieli) match {
          case Some(x) => x
          case _ => None
        }
      ).asInstanceOf[List[Koulutus]]
    }

    private def buildMessage(koulutukset: List[Koulutus]): String = {
      koulutukset.map(k =>
        s"${Translations.getTranslation("emailNote", "note")}:\n ${k.name}, ${getHaku(k)}, ${getOpetusPiste(k)} - ${k.educationDegree}\n" +
          getSoraDescription(k)+"\n"+
          Translations.getTranslation("emailNote", "openLink"))
        .mkString(",")
    }

    private def getOpetusPiste(koulutus: Koulutus): String = {
      koulutus.provider match {
        case Some(provider) => provider.name
        case _ => throw new IllegalStateException("Koulutus name not found")
      }
    }

    private def getSoraDescription(koulutus: Koulutus): String = {
      koulutus.soraDescription match {
        case Some(desc) => desc
        case _ => throw new IllegalStateException("Koulutus description not found")
      }
    }

    private def getHaku(koulutus: Koulutus): String = {
      tarjontaService.haku("1.2.246.562.5.2014020613412490531399", lang) match {
        case Some(haku) => haku.name
        case _ => throw new IllegalStateException("Haku not found")
      }
    }

    private def buildUlrEncodedOidString(oids: List[String]): String = {
      UrlValueCompressor.compress(write(oids))
    }

  }

}
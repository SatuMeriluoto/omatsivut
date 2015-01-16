package fi.vm.sade.omatsivut.hakemus

import java.util

import fi.vm.sade.haku.virkailija.lomakkeenhallinta.util.OppijaConstants
import fi.vm.sade.omatsivut.domain.Language
import fi.vm.sade.omatsivut.domain.Language
import fi.vm.sade.omatsivut.domain.Language.Language
import fi.vm.sade.omatsivut.hakemus.domain.Hakemus._
import fi.vm.sade.omatsivut.hakemus.domain._
import fi.vm.sade.omatsivut.koodisto.KoodistoComponent
import fi.vm.sade.omatsivut.koulutusinformaatio.KoulutusInformaatioComponent
import fi.vm.sade.omatsivut.lomake.domain.Lomake
import fi.vm.sade.omatsivut.tarjonta.domain.Haku
import fi.vm.sade.omatsivut.tarjonta.{TarjontaComponent, TarjontaService}
import fi.vm.sade.omatsivut.tulokset._
import fi.vm.sade.omatsivut.valintatulokset.domain.{HakutoiveenValintatulos, Valintatulos, Vastaanottoaikataulu}
import org.joda.time.LocalDateTime

import scala.collection.JavaConversions._
import scala.util.Try

trait HakemusConverterComponent {
  this: KoodistoComponent with TarjontaComponent with KoulutusInformaatioComponent =>

  val hakemusConverter: HakemusConverter
  val tarjontaService: TarjontaService

  class HakemusConverter {
    val educationPhaseKey = OppijaConstants.PHASE_EDUCATION
    val baseEducationKey = OppijaConstants.ELEMENT_ID_BASE_EDUCATION
    val preferencePhaseKey = OppijaConstants.PHASE_APPLICATION_OPTIONS

    def convertToHakemus(lomake: Option[Lomake], haku: Haku, application: ImmutableLegacyApplicationWrapper)(implicit lang: Language.Language) : Hakemus = {
      convertToHakemus(lomake, haku, application, None)
    }

    def convertToHakemus(lomake: Option[Lomake], haku: Haku, application: ImmutableLegacyApplicationWrapper, valintatulos: Option[Valintatulos])(implicit lang: Language.Language) : Hakemus = {
      val koulutusTaustaAnswers: util.Map[String, String] = application.phaseAnswers(educationPhaseKey)
      val receivedTime =  application.received.map(_.getTime)
      val answers = application.answers
      val hakutoiveet = convertHakuToiveet(application)

      Hakemus(
        application.oid,
        receivedTime,
        application.updated.map(_.getTime).orElse(receivedTime),
        tila(haku, application, hakutoiveet, valintatulos),
        hakutoiveet,
        haku,
        EducationBackground(koulutusTaustaAnswers.get(baseEducationKey), !Try {koulutusTaustaAnswers.get("ammatillinenTutkintoSuoritettu").toBoolean}.getOrElse(false)),
        answers,
        answers.get("henkilotiedot")
          .flatMap(_.get("Postinumero"))
          .flatMap(koodistoService.postOffice)
          .flatMap((translations: Map[String,String]) => translations.get(lang.toString)),
        lomake.map(_.requiresAdditionalInfo(application)).getOrElse(false),
        lomake.isDefined
      )
    }

    def tila(haku: Haku, application: ImmutableLegacyApplicationWrapper, hakutoiveet: List[Hakutoive], valintatulos: Option[Valintatulos])(implicit lang: Language.Language): HakemuksenTila = {
      if (application.isPostProcessing) {
        PostProcessing()
      } else {
        if (anyApplicationPeriodEnded(haku, hakutoiveet)) {
          val tulos = convertValintatulos(valintatulos, hakutoiveet)
          val now = new LocalDateTime().toDate.getTime // Use LocalDateTime so that we can use TimeWarp in tests
          if (haku.aikataulu.flatMap(_.hakukierrosPaattyy.map(_ < now)).getOrElse(false)) {
            HakukierrosPaattynyt(valintatulos = tulos)
          }
          else if (!haku.active) {
            HakukausiPaattynyt(valintatulos = tulos)
          } else {
            Active(valintatulos = tulos)
          }

        } else {
          application.state match {
            case "ACTIVE" => Active()
            case "PASSIVE" => Passive()
            case "INCOMPLETE" => Incomplete()
            case "SUBMITTED" => Submitted()
            case x => {
              throw new RuntimeException("Unexpected state for application " + application.oid + ": " + x)
            }
          }
        }
      }
    }

    def anyApplicationPeriodEnded(haku: Haku, application: ImmutableLegacyApplicationWrapper)(implicit lang: Language): Boolean = {
      anyApplicationPeriodEnded(haku, convertHakuToiveet(application))
    }

    private def anyApplicationPeriodEnded(haku: Haku, hakutoiveet: List[Hakutoive]): Boolean = {
      val now = new LocalDateTime().toDate.getTime // Use LocalDateTime so that we can use TimeWarp in tests
      haku.applicationPeriods.exists(_.end < now) || hakutoiveet.exists { hakutoive =>
        hakutoive.kohdekohtainenHakuaika.map(_.end < now).getOrElse(false)
      }
    }

    private def isKesken(hakutoiveenValintatulos: ToiveenValintatulos) = {
      hakutoiveenValintatulos.tila  == HakutoiveenValintatulosTila.KESKEN ||
      hakutoiveenValintatulos.tila == HakutoiveenValintatulosTila.VARALLA
    }

    private def isHyvaksytty(hakutoiveenValintatulos: ToiveenValintatulos) = {
      hakutoiveenValintatulos.tila  == HakutoiveenValintatulosTila.HYVAKSYTTY ||
      hakutoiveenValintatulos.tila == HakutoiveenValintatulosTila.HARKINNANVARAISESTI_HYVAKSYTTY ||
      hakutoiveenValintatulos.tila == HakutoiveenValintatulosTila.VARASIJALTA_HYVAKSYTTY
    }

    private def vastaanottotieto(valintatulokset: List[ToiveenValintatulos]) = {
      val valmisIndex = valintatulokset.indexWhere(_.vastaanottotila != ResultState.KESKEN)
      if (valmisIndex >= 0) {
        if (valintatulokset(valmisIndex).isPeruuntunut) {
          valintatulokset.slice(0, valmisIndex).find(isKesken(_)) match {
            case Some(kesken) => None // jos jokin yläpuolella on varalla
            case _ => Some(valintatulokset(valmisIndex))
          }
        } else {
          Some(valintatulokset(valmisIndex))
        }
      } else {
        None
      }
    }

    private def isVastaanotettavissa(hakutoiveenValintatulos: ToiveenValintatulos) = {
      hakutoiveenValintatulos.vastaanotettavuustila == VastaanotettavuusTila.VASTAANOTETTAVISSA_EHDOLLISESTI ||
      hakutoiveenValintatulos.vastaanotettavuustila == VastaanotettavuusTila.VASTAANOTETTAVISSA_SITOVASTI
    }

    private def convertValintatulos(valintatulos: Option[Valintatulos], hakutoiveet: List[Hakutoive])(implicit lang: Language.Language): Option[HakemuksenValintatulos] = {
      def findKoulutus(oid: String): Koulutus = {
        val koulutus = (for {hakemusData <- hakutoiveet.flatMap(_.hakemusData)
                             koulutusId <- hakemusData.get("Koulutus-id") if koulutusId == oid} yield {
          Koulutus(oid, hakemusData.getOrElse("Koulutus", oid))
        }).headOption
        koulutus.getOrElse(Koulutus(oid, oid))
      }

      def findOpetuspiste(oid: String): Opetuspiste = {
        val opetuspiste = (for {hakemusData <- hakutoiveet.flatMap(_.hakemusData)
                                opetuspisteId <- hakemusData.get("Opetuspiste-id") if opetuspisteId == oid} yield {
          Opetuspiste(oid, hakemusData.getOrElse("Opetuspiste", oid))
        }).headOption
        opetuspiste.getOrElse(Opetuspiste(oid, oid))
      }

      valintatulos.map { valintaTulos =>
        HakemuksenValintatulos(valintaTulos.hakutoiveet.map { hakutoiveenTulos =>
          ToiveenValintatulos(
            findKoulutus(hakutoiveenTulos.hakukohdeOid),
            findOpetuspiste(hakutoiveenTulos.tarjoajaOid),
            HakutoiveenValintatulosTila.withName(hakutoiveenTulos.valintatila),
            convertToResultsState(hakutoiveenTulos),
            VastaanotettavuusTila.withName(hakutoiveenTulos.vastaanotettavuustila),
            hakutoiveenTulos.vastaanottoDeadline.map(_.getTime),
            hakutoiveenTulos.ilmoittautumistila,
            hakutoiveenTulos.jonosija,
            hakutoiveenTulos.varasijojaTaytetaanAsti.map(_.getTime),
            hakutoiveenTulos.varasijanumero,
            hakutoiveenTulos.tilanKuvaukset.get(lang.toString.toUpperCase)
          )
        })
      }
    }

    private def convertToResultsState(hakutoiveenTulos: HakutoiveenValintatulos) = {
      hakutoiveenTulos.vastaanottotila  match {
        case None => ResultState.KESKEN
        case Some(tulos) => {
          ResultState.withName(tulos)
        }
      }
    }

    private def convertHakuToiveet(application: ImmutableLegacyApplicationWrapper)(implicit lang: Language): List[Hakutoive] = {
      def hakutoiveDataToHakutoive(data: HakutoiveData): Hakutoive = {
        data.isEmpty match {
          case true =>
            Hakutoive.empty
          case _ =>
            val tarjonnanHakukohde = tarjontaService.hakukohde(data("Koulutus-id"))
            val amendedData = amendWithKoulutusInformaatio(lang, data)

            Hakutoive(Some(amendedData), tarjonnanHakukohde.flatMap(_.hakuaikaId), tarjonnanHakukohde.flatMap(_.kohteenHakuaika))
        }
      }
      HakutoiveetConverter.convertFromAnswers(application.answers).map(hakutoiveDataToHakutoive)
    }

    private def amendWithKoulutusInformaatio(lang: Language, data: HakutoiveData): HakutoiveData = {
      val koulutus = data.get("Koulutus").orElse(koulutusInformaatioService.koulutus(data("Koulutus-id"), lang).map(_.name))
      val opetuspiste = data.get("Opetuspiste").orElse(koulutusInformaatioService.opetuspiste(data("Opetuspiste-id"), lang).map(_.name))

      val amendedData = data ++ koulutus.map("Koulutus" -> _) ++ opetuspiste.map("Opetuspiste" -> _)
      amendedData
    }
  }
}

package fi.vm.sade.omatsivut.koulutusinformaatio

import fi.vm.sade.omatsivut.config.AppConfig.AppConfig
import fi.vm.sade.omatsivut.fixtures.JsonFixtureMaps
import fi.vm.sade.omatsivut.json.JsonFormats
import fi.vm.sade.omatsivut.koulutusinformaatio.domain.{Koulutus, Opetuspiste}
import fi.vm.sade.omatsivut.memoize.TTLOptionalMemoize
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging

import scalaj.http.Http

trait KoulutusInformaatioComponent {
  val koulutusInformaatioService: KoulutusInformaatioService

  class StubbedKoulutusInformaatioService extends KoulutusInformaatioService {
    def opetuspisteet(asId: String, query: String, lang: String) = {
      JsonFixtureMaps.findByKey[List[Opetuspiste]]("/mockdata/opetuspisteet.json", query.substring(0, 1).toLowerCase)
    }
    def koulutukset(asId: String, opetuspisteId: String, baseEducation: Option[String], vocational: String, uiLang: String) = {
      JsonFixtureMaps.findByKey[List[Koulutus]]("/mockdata/koulutukset.json", opetuspisteId)
    }
    def koulutus(aoId: String, lang: String) = {
      JsonFixtureMaps.findByFieldValue[List[Koulutus]]("/mockdata/koulutukset.json", "id", aoId).getOrElse(List()).headOption
    }
  }

  object CachedKoulutusInformaatioService {
    def apply(implicit appConfig: AppConfig): KoulutusInformaatioService = {
      val service = new RemoteKoulutusService()
      val cacheTimeSec = 60*15
      val opetuspisteetMemo = TTLOptionalMemoize.memoize(service.opetuspisteet _, "koulutusinformaatio opetuspisteet", cacheTimeSec, 32)
      val koulutusMemo = TTLOptionalMemoize.memoize(service.koulutus _, "koulutusinformaatio koulutus", cacheTimeSec, 32)
      val koulutuksetMemo = TTLOptionalMemoize.memoize(service.koulutukset _, "koulutusinformaatio koulutukset", cacheTimeSec, 32)

      new KoulutusInformaatioService {
        def opetuspisteet(asId: String, query: String, lang: String): Option[List[Opetuspiste]] = opetuspisteetMemo(asId, query, lang)
        def koulutus(aoId: String, lang: String): Option[Koulutus] = koulutusMemo(aoId, lang)
        def koulutukset(asId: String, opetuspisteId: String, baseEducation: Option[String], vocational: String, uiLang: String): Option[List[Koulutus]] = koulutuksetMemo(asId, opetuspisteId, baseEducation, vocational, uiLang)
      }
    }
  }

  class RemoteKoulutusService(implicit appConfig: AppConfig) extends KoulutusInformaatioService with JsonFormats with Logging {
    import org.json4s.jackson.JsonMethods._

    private def wrapAsOption[A](l: List[A]): Option[List[A]] = if (!l.isEmpty) Some(l) else None

    def opetuspisteet(asId: String, query: String, lang: String): Option[List[Opetuspiste]] = {
      val (responseCode, headersMap, resultString) = DefaultHttpClient.httpGet(appConfig.settings.koulutusinformaatioLopUrl + "/search/" + Http.urlEncode(query, "UTF-8"))
        .param("asId", asId)
        .param("lang", lang)
        .responseWithHeaders

      wrapAsOption(parse(resultString).extract[List[Opetuspiste]])
    }

    def koulutukset(asId: String, opetuspisteId: String, baseEducation: Option[String], vocational: String, uiLang: String): Option[List[Koulutus]] = {
      var request = DefaultHttpClient.httpGet(appConfig.settings.koulutusinformaatioAoUrl + "/search/" + asId + "/" + opetuspisteId)
        .param("vocational", vocational)
        .param("uiLang", uiLang)
      if(baseEducation.isDefined) {
        request = request.param("baseEducation", baseEducation.get)
      }
      val (responseCode, headersMap, resultString) = request.responseWithHeaders

      wrapAsOption(parse(resultString).extract[List[Koulutus]])
    }

    def koulutus(aoId: String, lang: String): Option[Koulutus] = {
      val (responseCode, headersMap, resultString) = DefaultHttpClient.httpGet(appConfig.settings.koulutusinformaatioAoUrl + "/" + aoId)
        .param("lang", lang)
        .param("uiLang", lang)
        .responseWithHeaders
      withWarnLogging{
        parse(resultString).extract[Option[Koulutus]]
      }("Parsing response failed:\n" + resultString, None)
    }
  }
}


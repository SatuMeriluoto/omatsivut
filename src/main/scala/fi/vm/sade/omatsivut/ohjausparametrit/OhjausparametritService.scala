package fi.vm.sade.omatsivut.ohjausparametrit

import fi.vm.sade.omatsivut.config.AppConfig.{AppConfig, StubbedExternalDeps}
import fi.vm.sade.omatsivut.fixtures.JsonFixtureMaps
import fi.vm.sade.omatsivut.haku.domain.Tulokset
import fi.vm.sade.omatsivut.http.DefaultHttpClient
import fi.vm.sade.omatsivut.json.JsonFormats
import fi.vm.sade.omatsivut.memoize.TTLOptionalMemoize
import org.json4s.JsonAST.JValue


trait OhjausparametritService {
  def valintatulokset(asId: String): Option[Tulokset]
}

private object OhjausparametritParser extends JsonFormats {
  def parseValintatulokset(json: JValue) = {
    for {
      obj <- (json \ "PH_VTJH").toOption
      start <- (obj \ "dateStart").extractOpt[Long]
      end <- (obj \ "dateEnd").extractOpt[Long]
    } yield Tulokset(start, end)
  }
}

object OhjausparametritService {
  def apply(implicit appConfig: AppConfig): OhjausparametritService = appConfig match {
    case _ : StubbedExternalDeps => new StubbedOhjausparametritService()
    case _ => CachedRemoteOhjausparametritService(appConfig)
  }
}

class StubbedOhjausparametritService extends OhjausparametritService with JsonFormats {
  def valintatulokset(asId: String) = {
    JsonFixtureMaps.findByKey[JValue]("/mockdata/ohjausparametrit.json", asId).flatMap(OhjausparametritParser.parseValintatulokset(_))
  }
}

class RemoteOhjausparametritService(implicit appConfig: AppConfig) extends OhjausparametritService with JsonFormats {
  import org.json4s.jackson.JsonMethods._

  def valintatulokset(asId: String) = {
    val (responseCode, _, resultString) = DefaultHttpClient.httpGet(appConfig.settings.ohjausparametritUrl + "/" + asId)
      .responseWithHeaders

    responseCode match {
      case 200 =>
        parse(resultString).extractOpt[JValue].flatMap(OhjausparametritParser.parseValintatulokset(_))
      case _ => None
    }
  }
}

private object CachedRemoteOhjausparametritService {
  def apply(implicit appConfig: AppConfig): OhjausparametritService = {
    val service = new RemoteOhjausparametritService()
    val valintatuloksetMemo = TTLOptionalMemoize.memoize(service.valintatulokset _, 60 * 60)

    new OhjausparametritService() {
      override def valintatulokset(asId: String) = valintatuloksetMemo(asId)
    }
  }
}
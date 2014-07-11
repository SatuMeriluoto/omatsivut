package fi.vm.sade.omatsivut.servlet

import fi.vm.sade.omatsivut.domain.Hakemus
import fi.vm.sade.omatsivut.http.HttpClient
import org.scalatra.json._
import org.scalatra.swagger._
import org.json4s.jackson.Serialization
import fi.vm.sade.omatsivut.security.Authentication
import fi.vm.sade.omatsivut.json.JsonFormats
import fi.vm.sade.omatsivut.hakemus.{HakemusValidator, HakemusRepository}

class ApplicationsServlet(implicit val swagger: Swagger) extends OmatSivutServletBase with HttpClient with JacksonJsonSupport with JsonFormats with SwaggerSupport with Authentication {
  override def applicationName = Some("api")

  protected val applicationDescription = "Oppijan henkilökohtaisen palvelun REST API, jolla voi hakea ja muokata hakemuksia ja omia tietoja"

  val getApplicationsSwagger = (apiOperation[List[Hakemus]]("getApplications")
    summary "Hae oppijan hakemukset"
    parameters pathParam[String]("hetu").description("Käyttäjän henkilötunnus, jonka hakemukset listataan")
    )

  val putApplicationsSwagger = (apiOperation[Unit]("putApplication")
    summary "Tallenna hakemus"
    )

  val validateApplicationsSwagger = (apiOperation[Unit]("validateApplication")
    summary "Tarkista hakemus ja palauta virheet"
    )

  val findUnansweredQuestionsFromApplicationSwagger = (apiOperation[Unit]("findUnansweredQuestionsFromApplication")
    summary "Tarkista hakemus ja palauta kysymykset joihin ei ole vastattu"
    )

  before() {
    contentType = formats("json")
  }

  get("/applications", operation(getApplicationsSwagger)) {
    HakemusRepository.fetchHakemukset(oid())
  }

  put("/applications/:oid", operation(putApplicationsSwagger)) {
    val updated = Serialization.read[Hakemus](request.body)
    HakemusRepository.updateHakemus(updated)
  }

  post("/applications/validate/:oid", operation(validateApplicationsSwagger)) {
    val validate = Serialization.read[Hakemus](request.body)
    HakemusValidator.validate(validate)
  }


  post("/applications/unanswered/:oid", operation(findUnansweredQuestionsFromApplicationSwagger)) {
    val hakemus = Serialization.read[Hakemus](request.body)
    HakemusValidator.findMissingQuestions(hakemus)
  }
}

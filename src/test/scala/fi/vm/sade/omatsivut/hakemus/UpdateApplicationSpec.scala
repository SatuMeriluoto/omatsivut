package fi.vm.sade.omatsivut.hakemus

import fi.vm.sade.omatsivut.config.AppConfig
import fi.vm.sade.omatsivut.fixtures.TestFixture._
import fi.vm.sade.omatsivut.hakemus.domain.Hakemus
import fi.vm.sade.omatsivut.servlet.ApplicationsServlet
import org.json4s._
import org.json4s.jackson.JsonMethods

class UpdateApplicationSpec extends HakemusApiSpecification {
  override implicit lazy val appConfig = new AppConfig.IT
  sequential

  addServlet(new ApplicationsServlet(), "/*")

  "PUT /application/:oid" should {
    "reject application with empty hakutoiveet" in {
      modifyHakemus(hakemus1) { hakemus => hakemus.copy(hakutoiveet = Nil) } { _ =>
        status must_== 400
      }
    }

    "accept valid application" in {
      modifyHakemus (hakemus1){ hakemus => hakemus} { hakemus =>
        val result: JValue = JsonMethods.parse(body)
        status must_== 200
        hasSameHakuToiveet(hakemus, result.extract[Hakemus]) must_== true
      }
    }

    "save application" in {
      modifyHakemus(hakemus1)(answerExtraQuestion(preferencesPhaseKey, "539158b8e4b0b56e67d2c74b", "yes sir")) { newHakemus =>
        status must_== 200
        val result: JValue = JsonMethods.parse(body)
        hasSameHakuToiveet(newHakemus, result.extract[Hakemus]) must_== true
        // verify saved application
        withSavedApplication(newHakemus) { application =>
          application.getPhaseAnswers(personalInfoPhaseKey).get(ssnKey) must_== testHetu
          application.getPhaseAnswers(preferencesPhaseKey).get("539158b8e4b0b56e67d2c74b") must_== "yes sir"
        }
      }
    }

    "prune answers to removed questions" in {
      modifyHakemus(hakemus1)(answerExtraQuestion(preferencesPhaseKey, "539158b8e4b0b56e67d2c74b", "yes sir")) { _ =>
        status must_== 200
        modifyHakemus(hakemus1)(removeHakutoive) { hakemus =>
          status must_== 200
          withSavedApplication(hakemus) { application =>
            application.getPhaseAnswers(preferencesPhaseKey).containsKey("539158b8e4b0b56e67d2c74b") must_== false
          }
        }
      }
    }

    "reject answers to unknown questions" in {
      modifyHakemus(hakemus1)(answerExtraQuestion(preferencesPhaseKey, "unknown", "hacking")) { hakemus =>
        status must_== 400
      }
    }

    "reject update of application with invalid application period" in {
      modifyHakemus(inactiveHakemus)((hakemus) => hakemus) { hakemus =>
          status must_== 403
      }
    }

    "reject update of application that is not ACTIVE or INCOMPLETE" in {
      setupFixture("submittedApplication")
      modifyHakemus(hakemus2)((hakemus) => hakemus) { hakemus =>
        status must_== 403
      }
    }

    "reject update of application that is in post processing" in {
      setupFixture("postProcessingFailed")
      modifyHakemus(hakemus2)((hakemus) => hakemus) { hakemus =>
        status must_== 403
      }
    }

    "reject update of 'LISÄHAKU' application after application period" in {
      setupFixture("lisahakuEnded")
      modifyHakemus(hakemusLisahaku)((hakemus) => hakemus) { hakemus =>
        status must_== 403
      }
    }
  }
}

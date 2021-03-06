package fi.vm.sade.omatsivut

import fi.vm.sade.omatsivut.fixtures.TestFixture
import fi.vm.sade.omatsivut.servlet.ApplicationsServlet

class AuthenticationTimeOutSpec extends ScalatraTestSupport {
  "GET /applications" should {
    "return 401 if cookie has timed out" in {
      authGet("/applications", TestFixture.personOid) {
        status must_== 401
      }
    }

    "delete cookie if cookie has timed out" in {
      authGet("/applications", TestFixture.personOid) {
        val cookieValues = response.getHeader("Set-Cookie").split(";").toList
        val expires = cookieValues.find(_.startsWith("Expires="))
        expires.get must_== "Expires=Thu, 01-Jan-1970 00:00:00 GMT"
        val path = cookieValues.find(_.startsWith("Path="))
        path.get must_== "Path=/"
      }
    }
  }

  addServlet(new ApplicationsServlet() {
    override val cookieTimeoutMinutes = 0
  }, "/*")
}

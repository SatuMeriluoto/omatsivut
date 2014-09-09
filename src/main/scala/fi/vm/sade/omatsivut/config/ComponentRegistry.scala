package fi.vm.sade.omatsivut.config

import fi.vm.sade.omatsivut.auditlog.{AuditLogger, AuditLoggerComponent}
import fi.vm.sade.omatsivut.config.AppConfig.{MockAuthentication, AppConfig, ITWithSijoitteluService, StubbedExternalDeps}
import fi.vm.sade.omatsivut.fixtures.TestFixture
import fi.vm.sade.omatsivut.hakemus.{HakemusRepository, HakemusRepositoryComponent}
import fi.vm.sade.omatsivut.haku.{HakuRepository, HakuRepositoryComponent}
import fi.vm.sade.omatsivut.koulutusinformaatio.{KoulutusInformaatioComponent, KoulutusInformaatioService}
import fi.vm.sade.omatsivut.ohjausparametrit.{OhjausparametritComponent, OhjausparametritService}
import fi.vm.sade.omatsivut.security.{AuthenticationInfoComponent, AuthenticationInfoService}
import fi.vm.sade.omatsivut.valintatulokset._

protected class ComponentRegistry(implicit val config: AppConfig)
  extends KoulutusInformaatioComponent with
          OhjausparametritComponent with
          HakuRepositoryComponent with
          HakemusRepositoryComponent with
          ValintatulosServiceComponent with
          AuditLoggerComponent with
          AuthenticationInfoComponent {

  private def configureOhjausparametritService: OhjausparametritService = config match {
    case _ : StubbedExternalDeps => new StubbedOhjausparametritService()
    case _ => CachedRemoteOhjausparametritService(config)
  }

  private def configureKoulutusInformaatioService: KoulutusInformaatioService = config match {
    case x: StubbedExternalDeps => new StubbedKoulutusInformaatioService
    case _ => CachedKoulutusInformaatioService(config)
  }

  private def configureValintatulosService: ValintatulosService = config match {
    case x: ITWithSijoitteluService =>
      new RemoteValintatulosService("http://localhost:8180/resources/sijoittelu") {
        override def makeRequest(url: String) =  {
          super.makeRequest(url).map(_.header("Authorization", "Basic " + System.getProperty("omatsivut.sijoittelu.auth")))
        }
      }
    case x: StubbedExternalDeps =>
      new MockValintatulosService()
    case _ =>
      new NoOpValintatulosService
  }

  private def configureAuthenticationInfoService: AuthenticationInfoService = config match {
    case x: MockAuthentication => new AuthenticationInfoService {
      def getHenkiloOID(hetu: String) = TestFixture.persons.get(hetu)
    }
    case _ => new RemoteAuthenticationInfoService(config.settings.authenticationServiceConfig)(config)
  }

  val koulutusInformaatioService: KoulutusInformaatioService = configureKoulutusInformaatioService
  val ohjausparametritService: OhjausparametritService = configureOhjausparametritService
  val valintatulosService: ValintatulosService = configureValintatulosService
  val authenticationInfoService: AuthenticationInfoService = configureAuthenticationInfoService
  val auditLogger: AuditLogger = new AuditLoggerFacade(config.auditLogger)
  val hakuRepository: HakuRepository = new RemoteHakuRepository()
  val hakemusRepository: HakemusRepository = new RemoteHakemusRepository()
}

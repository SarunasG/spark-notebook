package modules

import com.google.inject.AbstractModule
import controllers.{ApplicationHacks, CustomAuthorizer, RoleAdminAuthGenerator, RoleUserAuthGenerator}
import modules.authentication.SingleUserPassAuthenticator
import org.pac4j.core.authorization.authorizer.{RequireAllRolesAuthorizer, RequireAnyRoleAuthorizer}
import org.pac4j.core.client.{BaseClient, Clients}
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.credentials.{AnonymousCredentials, Credentials, UsernamePasswordCredentials}
import org.pac4j.core.profile.{AnonymousProfile, CommonProfile}
import org.pac4j.http.client.indirect.{FormClient, IndirectBasicAuthClient}
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.kerberos.client.indirect.IndirectKerberosClient
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.http.DefaultHttpActionAdapter
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import utils.ConfigurationUtils.ConfigurationOps
/**
  * Guice DI module to be included in application.conf
  */
class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {
  val kerbPrefix = "notebook.server.auth.KerberosAuthentication"

  private def setupIndirectKerberosClient: IndirectKerberosClient = {
    val conf = configuration
    val servicePrincipal = conf.tryGetString(s"$kerbPrefix.service_principal").get
    val serviceKeytabFile = conf.tryGetString(s"$kerbPrefix.service_keytab").get

    import org.pac4j.kerberos.credentials.authenticator.{KerberosAuthenticator, SunJaasKerberosTicketValidator}
    import org.springframework.core.io.FileSystemResource
    val validator = new SunJaasKerberosTicketValidator()
    validator.setServicePrincipal(servicePrincipal)
    val keytabFile = new FileSystemResource(serviceKeytabFile)
    if (!keytabFile.exists()){
      throw new IllegalArgumentException(s"Keytab file '$serviceKeytabFile' do not exist")
    }
    validator.setKeyTabLocation(new FileSystemResource(serviceKeytabFile))
    validator.setDebug(true)
    validator.reinit()
    new IndirectKerberosClient(new KerberosAuthenticator(validator))
  }

  private def createSingleUserAuthenticator = {
    new SingleUserPassAuthenticator(configuration.getMandatoryConfig("notebook.server.auth.SingleUserPassAuthenticator"))
  }

  override def configure(): Unit = {
    val baseUrl = configuration.getString("baseUrl").getOrElse("")
    // modify here to any other Auth method supported by pac4j
    val mainAuthModule = configuration.getString("notebook.server.auth.main_module").getOrElse("AnonymousClient")

    val enabledClients = mainAuthModule match {
      case "IndirectKerberosClient" =>
        Seq(setupIndirectKerberosClient)

      case "IndirectBasicAuthClient" =>
        Seq(new IndirectBasicAuthClient(createSingleUserAuthenticator))

      case "FormClient" =>
        Seq(new FormClient(baseUrl + "/loginForm", createSingleUserAuthenticator))

      case "OidcClient" =>

        val oidcConfiguration = new OidcConfiguration()
        oidcConfiguration.setClientId("oidctest2")
        oidcConfiguration.setSecret("5f4cec85-1ae4-467a-9b47-190e25aa75e1")
        oidcConfiguration.setDiscoveryURI("http://localhost:8080/auth/realms/demo/.well-known/openid-configuration")
        oidcConfiguration.setClientAuthenticationMethodAsString("client_secret_basic")
        oidcConfiguration.addCustomParam("prompt", "consent")
        val oidcClient = new OidcClient[OidcProfile](oidcConfiguration)
        oidcClient.addAuthorizationGenerator(new RoleAdminAuthGenerator)
        oidcClient.addAuthorizationGenerator(new RoleUserAuthGenerator)

        Seq(oidcClient)

      case _ => Seq()
    }

    val clients = new Clients(baseUrl + "/callback", enabledClients: _*)

    val config = new Config(clients)
    config.addAuthorizer("admin", new RequireAllRolesAuthorizer[Nothing]("ROLE_ADMIN"))
    config.addAuthorizer("user", new RequireAllRolesAuthorizer("oidcrole"))
    config.addAuthorizer("custom", new CustomAuthorizer)
    config.setHttpActionAdapter(new DefaultHttpActionAdapter())
    bind(classOf[Config]).toInstance(config)

    // this is a hack to pass session store to controller w/o using DI
    // FIXME: when we use proper DI in controller(s), this can be a cleaner oneliner
    // bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

    /*    import play.cache.CacheApi
    val playCacheSessionStore: PlayCacheSessionStore = new PlayCacheSessionStore(getProvider(classOf[CacheApi]))
    ApplicationHacks.playPac4jSessionStoreOption = Some(playCacheSessionStore)*/

    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/?defaulturlafterlogout")
    callbackController.setMultiProfile(true)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/?logout")
    bind(classOf[LogoutController]).toInstance(logoutController)
  }
}

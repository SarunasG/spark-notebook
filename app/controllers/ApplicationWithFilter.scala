package controllers

import javax.inject.Inject

import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.Security
import org.pac4j.play.store.PlaySessionStore
import play.api.mvc._
import play.libs.concurrent.HttpExecutionContext

import scala.language.postfixOps

/**
  * Created by sarunas on 1/14/18.
  */
class ApplicationWithFilter @Inject() (val config: Config, val playSessionStore: PlaySessionStore, override val ec: HttpExecutionContext) extends Controller with Security[CommonProfile] {

  def oidcLogin = Secure("OidcClient") { profiles =>

    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }


}

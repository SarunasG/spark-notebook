package controllers

import org.pac4j.core.authorization.generator.AuthorizationGenerator
import org.pac4j.core.context.WebContext
import org.pac4j.oidc.profile.OidcProfile
/**
  * Created by sarunas on 9/11/17.
  */
class RoleUserAuthGenerator extends AuthorizationGenerator[OidcProfile] {

  override def generate(context: WebContext, profile: OidcProfile): OidcProfile = {
    profile.addRole("oidcrole")
    profile
  }
}

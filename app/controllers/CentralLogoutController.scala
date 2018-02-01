package controllers

/**
  * Created by sarunas on 12/6/17.
  */

import org.pac4j.play.LogoutController

class CentralLogoutController() extends LogoutController {
  setDefaultUrl("http://localhost:9001/")
  setLocalLogout(true)
  setCentralLogout(true)
  setLogoutUrlPattern("http://localhost:9001/.*")
}

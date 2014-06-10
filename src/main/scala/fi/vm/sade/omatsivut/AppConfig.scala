package fi.vm.sade.omatsivut

import com.typesafe.config._ 

object AppConfig {
 
  def loadSettings(): Settings = { 
 
    /** ConfigFactory.load() defaults to the following in order: 
      * system properties 
      * omatsivut.properties 
      * reference.conf 
      */ 
    new Settings(ConfigFactory.load("omatsivut"))
  }
  
  class Settings(config: Config) { 
    val casTicketUrl = config getString "omatsivut.cas.ticket.url" 
    val hakuAppUsername = config getString "omatsivut.haku-app.username" 
    val hakuAppPassword = config getString "omatsivut.haku-app.password" 
    val hakuAppUrl = config getString "omatsivut.haku-app.url"
    val hakuAppHakuQuery = config getString "omatsivut.haku-app.haku.query"
    val hakuAppTicketConsumer = config getString "omatsivut.haku-app.ticket.consumer.query"
  }   
}
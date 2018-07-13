package ing.wbaa.gargoyle.sts

import java.io.FileInputStream
import java.security.{ KeyStore, SecureRandom }

import akka.http.scaladsl.{ ConnectionContext, Http }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

trait Web {
  this: Actors with Routes with CoreActorSystem =>

  //  private val keystore = System.getProperty("javax.net.ssl.keyStore")
  //  private val password = System.getProperty("javax.net.ssl.keyStorePassword")
  //  private val sslContext = SSLContext.getInstance("TLS")
  //  private val ks = KeyStore.getInstance("JKS")
  //  private val fis = new FileInputStream(keystore)
  //  ks.load(fis, password.toCharArray)
  //  fis.close()
  //  private val kmf = KeyManagerFactory.getInstance("SunX509")
  //  kmf.init(ks, password.toCharArray)
  //  private val tmf = TrustManagerFactory.getInstance("SunX509")
  //  tmf.init(ks)
  //  sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
  //  private val connectionContext = ConnectionContext.https(sslContext)
  //
  private val serverConfig = config.getConfig("api.server")
  //  Http().bindAndHandle(routes, serverConfig.getString("interface"), serverConfig.getInt("port"), connectionContext)
  Http().bindAndHandle(routes, serverConfig.getString("interface"), serverConfig.getInt("port"))
}

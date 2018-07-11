package ing.wbaa.dab.sts

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{ Config, ConfigFactory }

trait CoreActorSystem {
  implicit def system: ActorSystem = ActorSystem()

  sys.addShutdownHook {
    system.terminate()
  }
  val config: Config = ConfigFactory.load()
}

trait Actors {
  this: CoreActorSystem =>
  implicit val materializer: ActorMaterializer = ActorMaterializer()
}

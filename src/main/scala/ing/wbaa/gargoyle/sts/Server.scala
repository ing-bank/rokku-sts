package ing.wbaa.gargoyle.sts

object Server extends App
  with CoreActorSystem
  with Routes
  with Actors
  with Web {
}

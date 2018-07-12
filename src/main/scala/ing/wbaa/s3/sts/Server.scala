package ing.wbaa.s3.sts

object Server extends App
  with CoreActorSystem
  with Routes
  with Actors
  with Web {
}

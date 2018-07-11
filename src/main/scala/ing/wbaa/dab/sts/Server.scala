package ing.wbaa.dab.sts

/**
 *
 * radosgw-admin user create --uid=authapi --display-name="auth api"
 * radosgw-admin caps add --uid=authapi --caps="users=*;buckets=*"
 */
object Server extends App
  with CoreActorSystem
  with Routes
  with Actors
  with Web {
}

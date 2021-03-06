import NativePackagerKeys._

packageArchetype.java_application

name := """my-app"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0"
)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := remoteAppName

herokuProcessTypes in Compile := Map(
  "web" -> "target/universal/stage/bin/my-app -Dtest.var=monkeys -Dhttp.port=$PORT",
  "worker" -> "java -version"
)

TaskKey[Unit]("createApp") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.log
  Process("heroku", Seq("create", "-n", remoteAppName)) ! streams.log
}

TaskKey[Unit]("runWorker") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  val output = Process("heroku", Seq("run", "worker", "-a", remoteAppName)).!!
  if (!output.contains("java version \"1.7.0_55\"")) {
    sys.error("Plugin should include custom process definitions")
  }
}

TaskKey[Unit]("check") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  var retries = 0
  while (retries < 10) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("http://" + remoteAppName + ".herokuapp.com").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Hello from Scala with var monkeys"))
        sys.error("There is a problem with the webpage: " + page)
      retries = 99999
    } catch {
      case ex: Exception =>
        if (retries < 10) {
          println("Error (retrying): " + ex.getMessage)
          Thread.sleep(1000)
          retries += 1
        } else {
          throw ex
        }
    }
    ()
  }
}
package kamon.mongo

import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

trait DockerMongodbService extends DockerKit {

  val DefaultMongodbPort = 27017

  val mongodbContainer = DockerContainer("mongo:3.4")
    .withPorts(DefaultMongodbPort -> Some(DefaultMongodbPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("waiting for connections on port"))
    .withCommand("mongod", "--smallfiles", "--syncdelay", "0")

  abstract override def dockerContainers: List[DockerContainer] =
    mongodbContainer :: super.dockerContainers
}

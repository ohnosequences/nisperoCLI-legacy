package ohnosequences.nispero

import java.io.File

import ohnosequences.nispero.Names._
import ohnosequences.awstools.s3.ObjectAddress
import org.clapper.avsl.Logger
import ohnosequences.nispero.tasks.Task
import ohnosequences.nativeTaskSolver.NativeTaskSolver

object Nispero {

    def checkNativeTaskSolver(awsClients: AWSClients, config: Config) {

      val tasksString = awsClients.s3.readWholeObject(config.initialTasks.get)
      val tasks = JSON.parse[List[Task]](tasksString)
      tasks.foreach { task =>
        println("trying solving task with id: " + task.id)
        ScriptExecutor.solve(awsClients.s3, task)
      }
    }

  def getFile(name: String): Option[File] = {
    val file = new File(name)
    if(file.exists()) {
      Some(file)
    } else {
      None
    }
  }

  def getEnvironmentVariable(name: String): Option[String] = {
    val value = System.getenv(name)
    if (value == null || value.isEmpty) {
      // println("none: value")
      None
    } else {
      // println("some: " + value)
      Some(value)
    }
  }


  def getAWSClientsFromFile(): Option[AWSClients] = {
    //println("getAWSClientsFromFile")
    for {
      credentialsFilePath <- getEnvironmentVariable("AWS_CREDENTIAL_FILE")
      file <- getFile(credentialsFilePath)
      awsClients <- AWSClients.fromFile(file)
    } yield awsClients
  }

  def getAWSClientsFromVariables(): Option[AWSClients] = {
    for {
      accessKey <- getEnvironmentVariable("AWS_ACCESS_KEY")
      secretKey <- getEnvironmentVariable("AWS_SECRET_KEY")
    } yield AWSClients.fromCredentials(accessKey = accessKey, secretKey = secretKey)
  }

  def getAWSClientsFromConfig(args: Array[String]): Option[AWSClients] = {
    try {
      val json = scala.io.Source.fromFile(new File(args.last)).mkString
      val config = JSON.parse[Config](json)
      if(!config.accessKey.isEmpty && !config.secretKey.isEmpty) {
        Some(AWSClients.fromCredentials(accessKey = config.accessKey, secretKey = config.secretKey))
      } else {
        None
      }
    } catch {
      case t: Throwable => None
    }
  }


  def getAWSClients(args: Array[String]): Option[AWSClients] = {
    val c1 = getAWSClientsFromConfig(args)

    if(c1.isDefined) {
      //println("found in file" + c1.get.accessKey)
      c1
    } else {
      val c2 = getAWSClientsFromVariables()
      if (c2.isDefined) {
        c2
      } else {
        getAWSClientsFromFile()
      }
    }
  }

  def main(args: Array[String]) {
    println("------------------------------------------------------")
    //print(Console.YELLOW)
    getAWSClients(args) match {
      case Some(awsClients) => handleArguments(args.head, args.toList.tail, awsClients)
      case None => println("Couldn't get AWS Credentials")
    }
    // print(Console.WHITE)
    println("------------------------------------------------------")
  }

  def handleArguments(command: String, args: List[String], awsClients: AWSClients) {
    import awsClients._
    command match {
      case "deploy" => {
        if(args.isEmpty) {
          println("usage: nispero deploy <nispero.config>")
        } else {
          val deploy = Deploy.fromFile(args(0), awsClients)
          val generatedConfig = deploy.deploy()
          val generatedConfigFile = new File(args(0) + ".generated")
          println("writing generated config to " + generatedConfigFile.getPath)
          Utils.writeStringToFile(JSON.toJson(generatedConfig), generatedConfigFile)
        }
      }
      case "undeploy" if(args.isEmpty) => handleArguments("list", args, awsClients)

      case "undeploy" => {

          val configFile = new File(args(0))


          val configBody: Option[String] = if (configFile.exists()) {
            Some(scala.io.Source.fromFile(configFile).mkString)
          } else {
            val groupName = args(0)
            autoScaling.getTagValue(groupName, InstanceTags.BUCKET) match {
              case Some(bucket) if(s3.getBucket(bucket).isDefined) => {
                Some(s3.readWholeObject(ObjectAddress(bucket, CONFIG_KEY)))
              }
              case None => None
            }
          }
          configBody match {
            case Some(body) => {
              val config = JSON.parse[Config](body)
              UnDeploy.shutdown(
                awsClients = awsClients,
                logger = Logger(Deploy.getClass),
                config = config,
                reason = "manual undeploy"
              )
            }
            case None => println("couldn't found config, usage undeploy <file>|<autoScalingGroupName>")
          }

      }

      case "list" => {
        autoScaling.describeAutoScalingGroups().foreach {
          group =>
            val name = group.name
            val tags = awsClients.autoScaling.describeTags(name)
            println(name + " --> " + tags)
        }
      }

      case "managerLog" => {
        val json = scala.io.Source.fromFile(new File(args.last)).mkString
        val rawConfig = JSON.parse[Config](json)
        s3.readObject(ObjectAddress(rawConfig.bucket, "manager.log")) match {
          case Some(log) => println(log)
          case None =>  println("haven't generated yet")
        }
      }

      case "check" => {
        val tasksString = scala.io.Source.fromFile(args(0)).mkString
        val tasks = JSON.parse[List[Task]](tasksString)

        val json = scala.io.Source.fromFile(new File(args.last)).mkString
        val rawConfig = JSON.parse[Config](json)

        val currentInstance = ec2.getCurrentInstance

        val workersAMI = rawConfig.workersGroup.launchingConfiguration.instanceSpecs.amiId

        if(currentInstance.isEmpty || currentInstance.isDefined && !currentInstance.get.getAMI().equals(workersAMI)) {
          println("warning you checking task solver on AMI: " + currentInstance.map(_.getAMI()) +
            " that differs from nispero farm AMI: " + workersAMI
          )
        }

        //amicheck:

        import scala.sys.process._

        val commands = List("unzip", "python --help", "java -version", "yum --help")
        val processLogger = ProcessLogger(
          oline => (),
          eline => println(eline)
        )
        commands.foreach { command =>
          try {
            if(command.!(processLogger) != 0) {
              throw new Error("couldn't run " + command)
            }
          } catch {
            case t: Throwable => println("error during run command " + command + " : " + t.getMessage)

          }
        }

                tasks.foreach { task =>
                  println("trying solving task with id: " + task.id)
                  ScriptExecutor.solve(awsClients.s3, task)
                }
      }

      case _ => {
        println("unknown command")
        println("known commands: check, deploy, undeploy, list, managerLog")
      }

    }
  }



}

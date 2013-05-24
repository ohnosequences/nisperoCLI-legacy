package ohnosequences.nispero

import java.io.File

import ohnosequences.nispero.Names._
import ohnosequences.awstools.s3.ObjectAddress
import org.clapper.avsl.Logger
import ohnosequences.nispero.tasks.Task


case class Exit(code: Int) extends xsbti.Exit

case class nisperoArgs(
  command: String = "",
  config: String = "",
  tasks: String = "",
  sources: String = "."
)

class nisperoCLI extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    Exit(nisperoCLI.run(config.arguments))
  }
}

object nisperoCLI {

  def main(args: Array[String]) {
    System.exit(run(args))
  }

  def run(args: Array[String]): Int = {

    val parser = new scopt.immutable.OptionParser[nisperoArgs]("nispero", "0.2.5") {

      def options = Seq(
        arg("<command>", "nispero command") {
          (v: String, c: nisperoArgs) => c.copy(command = v)
        },
        opt("t", "tasks", "file with tasks description") { 
          (v: String, c: nisperoArgs) => c.copy(tasks = v)
        },
        opt("s", "sources", "sources path to nispero sources") { 
          (v: String, c: nisperoArgs) => c.copy(sources = v)
        },
        argOpt("<file>|<autoScaligGroup>", "nispero config or auto scaling group name") {
          (v: String, c: nisperoArgs) => c.copy(config = v)
        }
      )
    }

    println("-------------------------------")
    val result = parser.parse(args, nisperoArgs()) map { nisperoArgs =>      
      //println(nisperoArgs)

      getAWSClients(nisperoArgs.config) match {
        case Some(awsClients) => handleArguments(nisperoArgs, awsClients, parser)
        case None => println("Couldn't get AWS Credentials"); 1
      }

    } getOrElse {
      1
    } 

    println("-------------------------------")
    result
  }

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

  def getAWSClientsFromConfig(configFile: String): Option[AWSClients] = {
    try {
      val json = scala.io.Source.fromFile(new File(configFile)).mkString
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


  def getAWSClients(configFile: String): Option[AWSClients] = {
    val c1 = getAWSClientsFromConfig(configFile)

    if(c1.isDefined) {
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

  def handleArguments(args: nisperoArgs, awsClients: AWSClients, parser: scopt.immutable.OptionParser[nisperoArgs]): Int = {
    //println("handleArguments")
    import awsClients._
    args.command match {
      case "deploy" if (!args.config.isEmpty()) => {
          val deploy = Deploy.fromFile(args.config, awsClients, args.sources)
          val generatedConfig = deploy.deploy()
          val generatedConfigFile = new File(args.config + ".generated")
          println("writing generated config to " + generatedConfigFile.getPath)
          Utils.writeStringToFile(JSON.toJson(generatedConfig), generatedConfigFile)
          0
      }
      case "undeploy" if(args.config.isEmpty()) => {
        handleArguments(args.copy(command = "list"), awsClients, parser)
        0
      }

      case "undeploy" => {
          val configFile = new File(args.config)
          val configBody: Option[String] = if (configFile.exists()) {
            Some(scala.io.Source.fromFile(configFile).mkString)
          } else {
            val groupName = args.config
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
              0
            }
            case None => {
              println("couldn't found config, usage undeploy <file>|<autoScalingGroupName>")
              1
            }
          }
      }

      case "list" => {
        autoScaling.describeAutoScalingGroups().foreach {
          group =>
            val name = group.name
            val tags = awsClients.autoScaling.describeTags(name)
            println(name + " --> " + tags)
        }
        0
      }

      case "managerLog" if(!args.config.isEmpty) => {
        val json = scala.io.Source.fromFile(args.config).mkString
        val rawConfig = JSON.parse[Config](json)
        s3.readObject(ObjectAddress(rawConfig.bucket, "manager.log")) match {
          case Some(log) => println(log); 0
          case None =>  println("haven't generated yet"); 1
        }
      }

      case "check" if(!args.config.isEmpty) => {

        val json = scala.io.Source.fromFile(new File(args.config)).mkString
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
            case t: Throwable => 
              throw new Error("error during run command " + command + " : " + t.getMessage)

          }
        }

        if(new File(args.tasks).exists()) {
          println("testing tasks form file " + args.tasks)
          val tasksString = scala.io.Source.fromFile(args.tasks).mkString
          val tasks = JSON.parse[List[Task]](tasksString)
          tasks.foreach { task =>
            println("trying solving task with id: " + task.id)
            ScriptExecutor.solve(awsClients.s3, task)
          }
          0
        } else {
          println("couldn't find " + args.tasks)
          1
        }
      }

      case _ => {
        parser.showUsage
        1
      }

    }
  }



}

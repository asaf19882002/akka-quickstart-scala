
  import java.io.{BufferedReader, File, FileNotFoundException, FileReader, FileWriter, IOException, PrintWriter}
  import Kind.Kind
  import akka.actor.{Actor, ActorRef, ActorSystem, Props}
  import scala.language.postfixOps
  import scala.util.Random

  object Kind extends Enumeration {
    type Kind = Value
    val READ, WRITE, CLOSE = Value
  }

  object FileServerManager {
    case class FreeFileServer(fileServer: ActorRef)
    case class ServerReq(client: ActorRef)
  }
  class FileServerManager extends Actor {

    import FileServerManager._

    def receive = {


      case ServerReq(client) =>
        println(s"$self has Received ${client}'s request for a file server'")
        client ! Client.ServerReqReply(context.actorOf(Props[FileServer]))

      case FreeFileServer(fileServer) =>
        println(s"$fileServer is freed by ${sender()}")
        context.stop(fileServer)
    }
  }

  object FileServer {
    case class Open(fileName: String, client: ActorRef)
    case class Access(kind: Kind, argument: String)
  }
  class FileServer() extends Actor {

    import FileServer._

    var client: ActorRef = null
    var fileName: String = ""
    var fileObject: File = null
    var printWriter: PrintWriter = null
    var reader: BufferedReader = null

    def receive = {

      case Open(fileName1, client1) =>
        client = client1
        fileName = fileName1
        println(s"$self is opening $fileName on behalf of $client")
        try {
          fileObject = new File(fileName)
          println(fileObject + " is opened")
          client ! Client.OpenReply(true)
        } catch {
          case x: FileNotFoundException =>

            client ! Client.OpenReply(false)
        }
      case Access(kind, argument) =>
        println(s"$self is trying to $kind (in) the $fileName on behalf of $client ")
        if (kind == Kind.READ) {
          try {
            reader = new BufferedReader(new FileReader(fileName))
            println(s"The reading result of the $fileName is going to be sent by $self to the $client")
            sender() ! Client.AccessReply(Kind.READ, reader.readLine())
            reader.close()
          } catch {
            case x: FileNotFoundException => {
              println("Exception: " + fileName + " is missing")
              sender() ! Client.Start()
            }
            case x: IOException => {
              println("Reading Input/output Exception")
              sender() ! Client.AccessReply(Kind.CLOSE, "file is closed")
            }
          }
        } else {
          if (kind == Kind.WRITE) {
            try {
              printWriter = new PrintWriter(new FileWriter(fileObject, true))
              printWriter.write(argument + " is writing, written by " + self + "\n")
              printWriter.close()
              println(s"The writing result on the $fileName is going to be sent by $self to the $client")
              sender() ! Client.AccessReply(Kind.WRITE, "Writing is done")
            } catch {
              case x: IOException => {
                println("Writing Input/output Exception")
                sender() ! Client.AccessReply(Kind.CLOSE, "file is closed")
              }
            }
          }
          else {
            println(s"$client is closing $fileName by requesting from $self")
            sender() ! Client.AccessReply(Kind.CLOSE, "file is closed")

          }
        }
    }
  }

  object Client {
    case class Start()
    case class ServerReqReply(fileServer: ActorRef)
    case class OpenReply(successful: Boolean)
    case class AccessReply(kind: Kind, Argument: String)
  }
  class Client(fileServerManager: ActorRef, fileNameArray: Array[String]) extends Actor {
    import Client._

    var fileName = ""
    var fileServer: ActorRef = null
    var action: Kind = null
    var numberOfReadAndWrites = 0
    var numberOfTriedReadAndWrite = 0
    val mean = 5
    val standardDeviation = 1
    val totalNumberOfFileInteractions = 20
    var currentCompletedInteractions = 0

    def receive = {

      case Start() =>
        numberOfTriedReadAndWrite = 0
        numberOfReadAndWrites = Math.round(Random.nextGaussian() * standardDeviation + mean).toInt
        fileName = fileNameArray(Random.nextInt(fileNameArray.length))
        println(s"$self will try to read and write ${numberOfReadAndWrites} times in the $fileName so it tries to acquire a file server by requesting from $fileServerManager")
        fileServerManager ! FileServerManager.ServerReq(self)

      case ServerReqReply(fileServer1) =>

        fileServer = fileServer1
        println(s"$self received $fileServer as file server from $fileServerManager and is going to send its openreq")
        fileServer ! FileServer.Open(fileName, self)

      case OpenReply(successful) =>
        if (successful) {
          println(s"openreq of $self is answered by $fileServer and $fileName is open. Now it is trying to interact with the file")
          action = Kind(Random.nextInt(2))
          fileServer ! FileServer.Access(action, s"$self")

        } else {
          println(s"openreq of $self is answered by $fileServer and $fileName is not open. Now it is trying to ask again")
          fileServer ! FileServer.Open(fileName, self)
        }
      case AccessReply(kind, data) =>

        if(kind == Kind.READ || kind == Kind.WRITE){
          println(s"$self received the answer from $fileServer for  $kind request")
          numberOfTriedReadAndWrite = numberOfTriedReadAndWrite + 1
          println(kind + " " + self.path + " " + data)

          if(numberOfTriedReadAndWrite < numberOfReadAndWrites){
            println(s"$self still has ${numberOfReadAndWrites-numberOfTriedReadAndWrite} more requsts from $fileName should be be answered by $fileServer")
            action = Kind(Random.nextInt(2))
            fileServer ! FileServer.Access(action, s"$self")
          }else{
            println(s"$self has no more request form $fileName, so $fileServer is going to be released")
            fileServer ! FileServer.Access(Kind.CLOSE, "Close the file.")
          }
        }else{
          currentCompletedInteractions = currentCompletedInteractions +1
          println(s"$self is interacted with files for $currentCompletedInteractions times")
          if(currentCompletedInteractions < totalNumberOfFileInteractions) {
            fileServerManager ! FileServerManager.FreeFileServer(fileServer)
            println(fileServer + " is freed and " + self + "is going to start again.")
            self ! Start()
          }else{
            fileServerManager ! FileServerManager.FreeFileServer(fileServer)
            println(fileServer + " is freed by " + self + " and it was its last interaction.")
          }
        }
    }
  }

  object DynamicFileServersAndClients extends App {
    val numberOfFiles = 2
    val numberOfClients = 5

    //creating system
    val system = ActorSystem("fileServersAndClients")
    println(system + "is running")

    //creating files
    var fileNameArray: Array[String] = Array.fill(numberOfFiles)("")
    for (i <- 0 until numberOfFiles) {
      fileNameArray(i) = "file" + i + ".txt"
      val file_Object = new File(fileNameArray(i))
      val print_Writer = new PrintWriter(file_Object)
      print_Writer.write("Hello, This is file" + i + " used for coding!\n")
      print_Writer.close()
    }
    println("files have been created")


    //creating file server manager
    val fileServerManager = system.actorOf(Props(classOf[FileServerManager]), "fileServerManager")

    println("file server manager has been created")

    //creating clients
    var arrayOfClients: Array[ActorRef] = Array.fill(numberOfClients)(null)
    for (i <- 0 until numberOfClients) {
      arrayOfClients(i) = system.actorOf(Props(classOf[Client], fileServerManager, fileNameArray), "client" + i)
    }
    println("clients has been created")

    //(starting program)
    for (i <- 0 until numberOfClients) {
      arrayOfClients(i) ! Client.Start()
    }
    println("program has been started")



}

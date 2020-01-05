
import java.io.{BufferedReader, File, FileNotFoundException, FileReader, FileWriter, IOException, PrintWriter}

import Kind.Kind
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scala.language.postfixOps
import scala.util.Random
import scala.collection.mutable._

object Kind extends Enumeration{
  type Kind = Value
  val READ, WRITE, CLOSE = Value
}

object FileServerManager{
  case class ServerReq(client:ActorRef)
  case class FreeFileServer(fileServer: ActorRef)
}
class FileServerManager(queueOfFileServers: Queue[ActorRef]) extends Actor{
  import FileServerManager._
  var queueOfClients = Queue[ActorRef]()

  def receive ={

    case ServerReq(client) =>
      println(s"$self has Received ${client}'s request for a file server'")
      if (!queueOfFileServers.isEmpty) {
        println("A file server is available")
        println(queueOfFileServers)
        client ! Client.ServerReqReply(queueOfFileServers.dequeue())
      } else{
        println(s"No file server is available. Putting the $client in the line.")
        queueOfClients.enqueue(client)
        println(queueOfClients)
      }

    case  FreeFileServer(fileServer) =>
      println(s"$fileServer is freed by ${sender()}")
      if(queueOfClients.isEmpty) {
        println("No client in the line waiting, therefore putting the file server in the line ")
        queueOfFileServers.enqueue(fileServer)
        println(queueOfFileServers)
      } else{
        println(s"There is at least one client in the line, so $fileServer is going to be assigned")
        queueOfClients.dequeue() ! Client.ServerReqReply(fileServer)
      }
  }
}

object FileServer{
  case class Open(fileAgent: ActorRef, client:ActorRef)
  case class Access(kind: Kind, argument: String)
  case class AccessReply(kind: Kind, argument: String)
  case class OpenReply ()
}
class FileServer() extends Actor{
  import FileServer._
  var client : ActorRef = null
  var fileActor: ActorRef = null


  def receive = {

    case Open(fileActor1, client1) =>

      client = client1
      fileActor = fileActor1
      println(s"$self is requesting $fileActor to be opened on behalf of $client")
      fileActor ! FileActor.Open(self)

    case OpenReply() =>
      println(s"$self is responding to openreq of $client on behalf of $fileActor")
      client ! Client.OpenReply()

    case Access(kind, argument) =>
      println(s"$self is trying to $kind (in) the $fileActor on behalf of $client ")
      fileActor ! FileActor.Access(kind, argument)

    case AccessReply(kind, argument) =>
      println(s"$self is responding the access request $kind made by $client on behalf of $fileActor")
      client ! Client.AccessReply(kind, argument)
  }
}

object FileActor{
  case class Open(fileServer: ActorRef)
  case class Access(kind: Kind, argument: String)
  case class prepare()
}
class FileActor(fileName: String) extends Actor{
  import FileActor._

  var activeFileServer : ActorRef = null
  var openReqQueue = Queue[ActorRef]()
  var fileObject: File = new File(fileName)
  var printWriter: PrintWriter = null
  var reader: BufferedReader = null

  def receive = {

    case Open(fileServer) =>
      println(s"Request by $fileServer to open file to me: $self")
      if( openReqQueue.isEmpty && activeFileServer == null){
        println(s"There is no active file server nor any one on the line for using $self, so $fileServer is going to be served")
        activeFileServer = fileServer
        activeFileServer ! FileServer.OpenReply()
      }else{
        println(s"There is an active file server using $self, so $fileServer is going to be put in the queue")
        openReqQueue.enqueue(fileServer)
        println(openReqQueue)
      }

    case Access(kind, argument) =>
      println(s"$activeFileServer trying to $kind (in) the $self")
      if(kind == Kind.READ){
        try {
          reader = new BufferedReader(new FileReader(fileName))
          println(s"The reading result is going to be sent by $self to the $activeFileServer")
          sender() ! FileServer.AccessReply(Kind.READ, reader.readLine())
          reader.close()
        }catch{
          case x: FileNotFoundException => {
            println("Exception: "+fileName +" is missing")
            sender() ! FileServer.AccessReply(Kind.CLOSE, "file is closed")
          }
          case x: IOException   => {
            println("Reading Input/output Exception")
            sender() ! FileServer.AccessReply(Kind.CLOSE, "file is closed")
          }
        }
      } else { if (kind == Kind.WRITE) {
          try {
            printWriter = new PrintWriter(new FileWriter(fileObject, true))
            printWriter.write(argument + " is writing, written by " + activeFileServer + "\n")
            printWriter.close()
            println(s"The writing result is going to be sent by $self to the $activeFileServer")
            sender() ! FileServer.AccessReply(Kind.WRITE, "Writing is done")
          } catch {
            case x: IOException =>

              println("Writing Input/output Exception")
              sender() ! FileServer.AccessReply(Kind.CLOSE, "file is closed")

          }
        } else {
          println(s"$activeFileServer is closing $self")
          sender() ! FileServer.AccessReply(Kind.CLOSE, "file is closed")
          if (!openReqQueue.isEmpty) {
            println("there is at least one request on the queue, therefore the first one in the line would be served")
            println(openReqQueue)
            activeFileServer = openReqQueue.dequeue()
            println(s"$activeFileServer is going to be served by $self")
            activeFileServer ! FileServer.OpenReply()
          }else{
            println(s"No pending request so $self is going to wait for the next openreq")
            activeFileServer = null
          }
        }
      }
  }
}

object Client{
  case class Start()
  case class ServerReqReply(fileServer: ActorRef)
  case class AccessReply(kind: Kind, Argument: String)
  case class OpenReply ()
}
class Client(fileServerManager: ActorRef, fileAgentArray: Array[ActorRef]) extends Actor{
  import Client._

  var fileAgent: ActorRef = null
  var fileServer: ActorRef = null
  var action : Kind = null
  var numberOfReadAndWrites = 0
  var numberOfTriedReadAndWrite = 0
  val mean = 5
  val standardDeviation = 1
  val totalNumberOfFileInteractions = 20
  var currentCompletedInteractions = 0

  def receive ={

    case Start() =>

      numberOfTriedReadAndWrite = 0
      numberOfReadAndWrites = Math.round(Random.nextGaussian()*standardDeviation + mean).toInt
      fileAgent = fileAgentArray(Random.nextInt(fileAgentArray.length))
      println(s"$self will try to read and write ${numberOfReadAndWrites} times in the $fileAgent, so it tries to acquire a file server by requesting from $fileServerManager")
      fileServerManager ! FileServerManager.ServerReq(self)

    case ServerReqReply(fileServer1) =>

      fileServer = fileServer1
      println(s"$self received $fileServer as file server from $fileServerManager and is going to send its openreq")
      fileServer ! FileServer.Open(fileAgent,self)

    case OpenReply() =>

      println(s"openreq of $self is answered by $fileServer. Now it is trying to interact with the file")
      action = Kind(Random.nextInt(2))
      println(s"$self is trying to $action (on) the $fileAgent through $fileServer")
      fileServer ! FileServer.Access(action, s"$self")

    case AccessReply(kind, data) =>

      if(kind == Kind.READ || kind == Kind.WRITE){
      println(s"$self received the answer from $fileServer for  $kind request")
      numberOfTriedReadAndWrite = numberOfTriedReadAndWrite + 1
      println(kind + " " + self.path + " " + data)

      if(numberOfTriedReadAndWrite < numberOfReadAndWrites){
        println(s"$self still has ${numberOfReadAndWrites-numberOfTriedReadAndWrite} more requsts from $fileAgent should be be answered by $fileServer")
        action = Kind(Random.nextInt(2))
        fileServer ! FileServer.Access(action, s"$self")
      }else {
        println(s"$self has no more request form $fileAgent, so $fileServer is going to be released")
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

object FileServersClientsFileActors extends App {
  val numberOfFiles   = 2
  val numberOfClients = 5

  //creating system
  val system = ActorSystem("fileServersAndClients")
  println(system+"is running" )

  //creating files
  var fileNameArray: Array[String] = Array.fill(numberOfFiles)("")
  for(i <- 0 until numberOfFiles) {
    fileNameArray(i)= "file"+ i +".txt"
    val file_Object = new File(fileNameArray(i))
    val print_Writer = new PrintWriter(file_Object)
    print_Writer.write("Hello, This is file"+i+" used for coding!\n")
    print_Writer.close()
  }
  println("files have been created")

  //creating file actors
  val fileActorArray: Array[ActorRef] = Array.fill(numberOfFiles)(null)
  for(i <- 0 until numberOfFiles) {
    fileActorArray(i) = system.actorOf(Props(classOf[FileActor],fileNameArray(i)), "fileActor"+i)
  }
  println("file actors have been created")

  //creating file Servers
  var queueOfFileServers = Queue[ActorRef]()
  for(i <- 0 until numberOfFiles){
    queueOfFileServers.enqueue (system.actorOf(Props[FileServer],"fileServer"+i))
  }
  println("file servers have been created")

  //creating file server manager
  val fileServerManager = system.actorOf(Props(classOf[FileServerManager],queueOfFileServers), "fileServerManager")
  println("file server manager has been created")

  //creating clients
  var arrayOfClients: Array[ActorRef] = Array.fill(numberOfClients)(null)
  for(i <- 0 until numberOfClients){
    arrayOfClients(i) = system.actorOf(Props(classOf[Client],fileServerManager,fileActorArray), "client"+i)
  }
  println("clients has been created")

  //(starting program)
  for(i <- 0 until numberOfClients){
    arrayOfClients(i) ! Client.Start()
  }
  println("program has been started")
}




/*

import java.io.{BufferedReader, FileReader, IOException, FileNotFoundException, File, FileWriter, PrintWriter}
import Kind.Kind
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scala.language.postfixOps
import scala.util.Random
import scala.collection.mutable._

object Kind extends Enumeration{
  type Kind = Value
  val READ, WRITE, CLOSE = Value
}

case class ServerReq(client:ActorRef)
case class ServerReqReply(fileServer: ActorRef,successful: Boolean)
case class Open(fileName: String, client:ActorRef)
case class Access(kind: Kind, argument: String)
case class OpenReply (successful: Boolean)
case class AccessReply(kind: Kind, Argument: String)
case class FreeFileServer(fileServer: ActorRef)
case class Start()



class FileServerManager(queueOfFileServers: Queue[ActorRef]) extends Actor{

  def receive ={


    case ServerReq(client) =>

        if (!queueOfFileServers.isEmpty) {

          client ! ServerReqReply(queueOfFileServers.dequeue() , true)
        } else {
          //println(sender() + " should try again")
          client ! ServerReqReply(null,false)
        }

    case  FreeFileServer(fileServer) =>

      println("Server"+fileServer +" is freed")
      queueOfFileServers.enqueue(fileServer)
  }
}

class FileServer() extends Actor{
  var client : ActorRef = null
  var fileName: String = ""
  var fileObject: File = null
  var printWriter: PrintWriter = null
  var reader: BufferedReader = null
  def receive = {

    case Open(fileName1, client1) =>
      try {
        client = client1
        fileName = fileName1
        fileObject = new File(fileName)
        println(fileObject + " is opened")
        client ! OpenReply(true)
      }catch{
        case x: FileNotFoundException =>
        client ! OpenReply(false)
      }
    case Access(kind, argument) =>

      if(kind == Kind.READ){
        try {
          reader = new BufferedReader(new FileReader(fileName))
          sender() ! AccessReply(Kind.READ, reader.readLine())
          reader.close()
        }catch{
          case x: FileNotFoundException => {
            println("Exception: "+fileName +" is missing")
            sender() ! Start()
          }
          case x: IOException   => {
            println("Input/output Exception")
            sender() ! AccessReply(Kind.CLOSE, "file is closed")
          }
        }
      }
      else{
        if(kind == Kind.WRITE) {
          try {
            printWriter = new PrintWriter(new FileWriter(fileObject, true))
            printWriter.write(client + " is writing:" + argument + "written by " + self + "\n")
            printWriter.close()
            sender() ! AccessReply(Kind.WRITE, "Writing is done")
          }catch{
            case x: IOException => {
              println("Input/output Exception")
              sender() ! AccessReply(Kind.CLOSE, "file is closed")
              }
          }
        }
        else{
          sender() ! AccessReply(Kind.CLOSE, "file is closed")

        }
      }
  }
}

class Client(fileServerManager: ActorRef,fileNameArray: Array[String]) extends Actor{
  var fileName = ""
  var fileServerID: Int = 0
  var fileServer: ActorRef = null
  var action : Kind = null

  def receive ={

    case Start() =>
      fileName = fileNameArray(Random.nextInt(fileNameArray.length))
      println(self+" wants to work with "+fileName+".")
      fileServerManager ! ServerReq(self)

    case ServerReqReply(fileServer1, successful) =>
      if(successful){
        fileServer = fileServer1
        println(self+"'s file server is ")
        fileServer ! Open(fileName,self)
      }
      else{
        fileServerManager ! ServerReq(self)
      }
    case OpenReply(successful) =>
      if (successful) {
        action = Kind(Random.nextInt(Kind.maxId))

        if (action == Kind.WRITE) {
          fileServer ! Access(action, s"First try of $self")
        }
        else {
          fileServer ! Access(action, s"$self")
        }
      }else{
        fileServer ! Open(fileName,self)
      }
    case AccessReply(kind, data) =>
      if(kind == Kind.READ || kind == Kind.WRITE){
        println(kind+" "+self.path+" "+data)

        action = Kind(Random.nextInt(Kind.maxId))
        if(action == Kind.WRITE) {
          fileServer ! Access(action, s"First try of $self")
        }
        else {
          fileServer ! Access(action, s"$self")
        }
      }
      else{

        fileServerManager ! FreeFileServer(fileServer)
        println(fileServer + " is freed by "+self +".")
        self ! Start()
      }
  }
}

object FileServersAndClients extends App {
  val numberOfFiles   = 3
  val numberOfClients = 4

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
    arrayOfClients(i) = system.actorOf(Props(classOf[Client],fileServerManager,fileNameArray), "client"+i)
  }
  println("clients has been created")

  //(starting program)
  for(i <- 0 until numberOfClients){
    arrayOfClients(i) ! Start()
  }
  println("program has been started")
}


*/

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}

import language.postfixOps
import scala.collection.mutable.{ListBuffer}
import scala.util.{Random}


case class Request (req_num: Int, cyl_num: Int, client: ActorRef)
case class Response (req_num: Int , data: String)
case class Release (head_pos: Int)
case class Start()
case class Correspondent(diskSchedulerActor: ActorRef)

class DiskSchedulerActor(diskActor: ActorRef) extends Actor{

  var leftQueue  : ListBuffer[(Int,Int,ActorRef)] = ListBuffer()
  var rightQueue : ListBuffer[(Int,Int,ActorRef)] = ListBuffer()
  var headPos    : Int                            = 127
  var released   : Boolean                        = true

   def receive= this.synchronized{

     case Start() =>

       diskActor ! Correspondent(self)

     case Request(req_num, cyl_num, client) =>

       if (released && !leftQueue.nonEmpty && !rightQueue.nonEmpty) {
         println("your lucky!")
         released = false
         diskActor ! Request(req_num, cyl_num, client)

       }
       else {
         if (cyl_num <= headPos) {
            println(s"It's on left: $cyl_num ")
            leftQueue.append((req_num, cyl_num, client))
            leftQueue = leftQueue.sortBy(_._2)
            println(s"left queue: $leftQueue")
         }
        else {
            println(s"It's on right: $cyl_num ")
            rightQueue.append((req_num, cyl_num, client))
            rightQueue = rightQueue.sortBy(_._2)
            println(s"right queue: $rightQueue")
         }
       }

     case Release(head_pos) =>

        println ("hey let's read something")
        if (leftQueue.nonEmpty && rightQueue.nonEmpty){

           if (math.abs(head_pos - (leftQueue(leftQueue.length-1))._2 )<= math.abs(head_pos - (rightQueue(0))._2 )) {
              println("Left Read")
              val request : (Int,Int,ActorRef)= leftQueue.remove(leftQueue.length-1)
              headPos = request._2
              println(request)
              diskActor ! Request(request._1,request._2,request._3)

           } else {
              println("Right Read")
              val request : (Int,Int,ActorRef)= rightQueue.remove(0)
              headPos = request._2
              println(request)
              diskActor ! Request(request._1,request._2,request._3)
           }
        }
        else {
            if (!rightQueue.nonEmpty && leftQueue.nonEmpty) {
              println("Left Read")
              val request: (Int, Int, ActorRef) = leftQueue.remove(leftQueue.length - 1)
              headPos = request._2
              println(s"$headPos")
              println(request)
              diskActor ! Request(request._1, request._2, request._3)
            }
            else {
              if (!leftQueue.nonEmpty && rightQueue.nonEmpty) {
                println("Right Read")
                val request: (Int, Int, ActorRef) = rightQueue.remove(0)
                headPos = request._2
                println(s"$headPos")
                println(request)
                diskActor ! Request(request._1, request._2, request._3)
              }
              else {
                released = true
              }
            }
        }
   }
}

class DiskActor() extends Actor {

  var head_pos = 127
  var diskSchedulerActor: ActorRef = null

  def receive = this.synchronized{

    case Correspondent(diskSchedulerActor1) =>
      diskSchedulerActor = diskSchedulerActor1
     // println(s"I know who you are $diskSchedulerActor")
      diskSchedulerActor ! Release (head_pos)

    case Request(req_num,cyl_num,client)=>
      println(s"requestchek $client")
      Thread.sleep(math.abs(cyl_num - head_pos))
      head_pos = cyl_num
      client ! Response(req_num,"data")
      diskSchedulerActor ! Release(head_pos)
  }
}

class ClientActor(diskSchedulerActor: ActorRef) extends Actor{

  var address_len = 8
  var max         = 10
  var counter     = 0
  def receive = {

    case Start() =>
      for (seq_num <- 0 until max) {
        val cyl_num: Int = Random.nextInt((math.pow(2, address_len) - 1).toInt)
        println(s"request is $seq_num: cylinder: $cyl_num")
        diskSchedulerActor ! Request(seq_num,cyl_num,self)
        Thread.sleep(10)
     }

    case Response(req_num, data) =>
      println(s"${self.path} received data for request number $req_num: $data ")
      counter += 1
      if (!(counter <= max)){
        diskSchedulerActor ! PoisonPill
        sender() ! PoisonPill
        self ! PoisonPill

      }
  }
}


object DiskSchedulerMain extends App{

  val system = ActorSystem("disk_scheduler")

  val disk          = system.actorOf(Props[DiskActor], "disk")
  val diskScheduler = system.actorOf(Props(classOf[DiskSchedulerActor], disk), "diskScheduler")
  val client        = system.actorOf(Props(classOf[ClientActor], diskScheduler), "client")

  client ! Start()
  diskScheduler ! Start()


}
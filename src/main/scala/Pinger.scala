import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props }
import language.postfixOps

case class Ping(number: Int)
case class Pong(printer: ActorRef)
case class Print(string: String)

class Pinger extends Actor {
  var countDown = 5

  def receive = {
    case Pong(printer) =>
      printer !Print("yooooohoooooooooo")
      println(s"${self.path} received pong, count down $countDown")

      if (countDown > 0) {
        countDown -= 1
        sender() ! Ping(countDown)
      } else {
        sender() ! PoisonPill
        self ! PoisonPill
      }
  }
}

class Ponger(pinger: ActorRef,printer: ActorRef) extends Actor {
  def receive = {
    case Ping(number) =>
      println(s"${self.path} received $number ping")
      pinger ! Pong(printer)
  }
}
class Printer() extends Actor {
  def receive = {
    case Print(string) =>
      println(s"${self.path} print $string")
  }
}
object Main extends App {


  val system = ActorSystem("pingpong")

  val pinger = system.actorOf(Props[Pinger], "pinger")

  val printer = system.actorOf(Props[Printer],"printer")

  val ponger = system.actorOf(Props(classOf[Ponger], pinger,printer), "ponger")

  ponger ! Ping(0)
}

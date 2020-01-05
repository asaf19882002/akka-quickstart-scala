/*

import java.io.{File, FileWriter, PrintWriter}

import scala.util.Random
object Kind extends Enumeration{
  type Kind = Value
  val READ, WRITE, CLOSE = Value
}
object qwerty extends App {
  import Kind._
 /* val numberOfFiles = 2
  var fileNameArray: Array[String] = Array.fill(numberOfFiles)("")
  for (i <- 0 until numberOfFiles) {
    fileNameArray(i) = "file" + i + ".txt"
    val file_Object = new File(fileNameArray(i))
    val print_Writer = new PrintWriter(file_Object)
    print_Writer.write("Hello, This file"+i+" used for coding!\n")
    print_Writer.write("something\n")
    print_Writer.close()
    val print_Writer1 = new PrintWriter(new FileWriter(file_Object,true))
    print_Writer1.write("dd\n")
    print_Writer1.close()
  }*/


  Ququ.main()
}
class Ququ{

  def main(){
    for (i <- 0 until 20) {
      println(s"${Kind(Random.nextInt(2))}")
    }
  }

}
*/

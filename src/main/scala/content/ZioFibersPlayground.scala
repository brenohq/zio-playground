package content

import zio.{Exit, ExitCode, UIO, URIO, ZIO}
import zio.duration._

object ZioFibersPlayground extends zio.App {

  // effect pattern
  // computation = value + an effect in the world (side effects)
  // substitution model

  val aValue = {
    println("hello scala")
    42
  }

  def incrementValue(x: Int) = x + 1

  // equal but don't do the same thing in real world
  incrementValue(42) == incrementValue(aValue)

  val zmol: UIO[Int] = ZIO.succeed(42)
  // UIO[Int] == ZIO[Any, Nothing, Int]

  // concurrency = daily routine of me
  val showerTime = ZIO.succeed("taking a shower")
  val boilingWater = ZIO.succeed("boiling water")
  val preparingCoffee = ZIO.succeed("preparing some coffee")

  def printThread = s"[${Thread.currentThread().getName}]"

  // simple synchronous routine, the tasks runs after the previous finishes
  def synchronousRoutine() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  // fiber = schedulable computation
  // defined with Fiber[E, A]

  // takes a shower in a separate thread while boiling water and prepare the coffee in original one
  def concurrentShowerWhileBoilingWater() = for {
    _ <- showerTime.debug(printThread).fork
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  // prepares the coffe only when shower and boiling water is finished by another thread
  def concurrentRoutine() = for {
    showerFiber <- showerTime.debug(printThread).fork
    boilingWaterFiber <- boilingWater.debug(printThread).fork
    zippedFiber = showerFiber.zip(boilingWaterFiber)
    result <- zippedFiber.join.debug(printThread)
    _ <- ZIO.succeed(s"${result} done").debug(printThread) *> preparingCoffee.debug(printThread)
  } yield ()

  val callFromAna = ZIO.succeed("Call from Ana")
  val boilingWaterWithTime = boilingWater.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("boiled water ready")

  // interrupts boiling water when an event occurs on another thread
  def concurrentRoutineWithAnaCall() = for {
    _ <- showerTime.debug(printThread)
    boilingFiber <- boilingWaterWithTime.fork
    _ <- callFromAna.debug(printThread).fork *> ZIO.sleep(2.seconds) *> boilingFiber.interrupt.debug(printThread)
    _ <- ZIO.succeed("screw my coffee, going with Ana").debug(printThread)
  } yield ()

  val prepareCoffeeWithTime = preparingCoffee.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("coffe ready")

  // defines an uninterruptible task and waits for coffee ready to respond Ana
  def concurrentRoutineWithCoffeeAtHome() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    coffeeFiber <- prepareCoffeeWithTime.debug(printThread).fork.uninterruptible
    result <- callFromAna.debug(printThread).fork *> coffeeFiber.interrupt.debug(printThread)
    _ <- result match {
      case Exit.Success(value) => ZIO.succeed("Sorry Ana, making breakfast at home.").debug(printThread)
      case _ => ZIO.succeed("Going to a cafe with Ana.").debug(printThread)
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // synchronousRoutine().exitCode
    // concurrentShowerWhileBoilingWater().exitCode
    // concurrentRoutine().exitCode
    // concurrentRoutineWithAnaCall().exitCode
    concurrentRoutineWithCoffeeAtHome().exitCode
  }
}

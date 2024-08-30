package content

import zio.{ExitCode, Has, Task, URIO, ZIO, ZLayer}
import zio.console._

object ZLayersPlayground extends zio.App {

  // ZIO[-R, +E, +A] = "effects"
  // R => Either[E, A]

  val meaningOfLife = ZIO.succeed(42)
  val aFailure = ZIO.fail("Something went wrong.")

  val greeting = for {
    _ <- putStrLn("Hi, whats your name?")
    name <- getStrLn
    _ <- putStrLn(s"Helllo $name, welcome back!")
  } yield ()

  /*
    Creating heavy apps involve services:
    - interacting with storage layer
    - business logic
    - front-facing APIs e.g. through HTTP
    - communicating with other services
   */

  case class User(name: String, email: String)

  object UserEmailer {
    type UserEmailerEnv = Has[UserEmailer.Service]

    // service definition
    trait Service {
      def notify(user: User, message: String): Task[Unit] // Zio[Any, Throwable, Unit]
    }

    // service impl
    val live: ZLayer[Any, Nothing, UserEmailerEnv] = ZLayer.succeed(new Service {
      override def notify(user: User, message: String): Task[Unit] = Task {
        println(s"sending $message to ${user.email}")
      }
    })

    // front-facing API
    def notify(user: User, message: String): ZIO[UserEmailerEnv, Throwable, Unit] = {
      ZIO.accessM(hasService => hasService.get.notify(user, message))
    }
  }

  // same pattern
  object UserDb {
    type UserDbEnv = Has[UserDb.Service]

    trait Service {
      def insert(user: User): Task[Unit]
    }

    val live = ZLayer.succeed(new Service {
      override def insert(user: User)= Task {
        println(s"[Database] insert into public.user values ('${user.email}')")
      }
    })

    def insert(user: User): ZIO[UserDbEnv, Throwable, Unit] = ZIO.accessM(_.get.insert(user))
  }

  // HORIZONTAL COMPOSITION
  // ZLayer[In1, E1, Out1] ++ ZLayer[In2, E2, Out2]
  //   => ZLayer[In1 with In2, super(E1, E2), Out1 with Out2]

  import UserDb._
  import UserEmailer._

  val userBackendLayer: ZLayer[Any, Nothing, UserDbEnv with UserEmailerEnv] = UserDb.live ++ UserEmailer.live

  // VERTICAL COMPOSITION
  object UserSubscription {
    type UserSubscriptionEnv = Has[UserSubscription.Service]

    class Service(notifier: UserEmailer.Service, userDb: UserDb.Service) {
      def subscribe(user: User): Task[User] = {
        for {
          _ <- userDb.insert(user)
          _ <- notifier.notify(user, s"Welcome to the db: ${user.name}!")
        } yield user
      }
    }

    val live: ZLayer[UserEmailerEnv with UserDbEnv, Nothing, UserSubscriptionEnv] = {
      ZLayer.fromServices[UserEmailer.Service, UserDb.Service, UserSubscription.Service] {
        (userEmailer, userDb) => new Service(userEmailer, userDb)
      }
    }

    def subscribe(user: User): ZIO[UserSubscriptionEnv, Throwable, User] = ZIO.accessM(_.get.subscribe(user))
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // greeting.exitCode
    val breno = new User("breno", "brenosc2@hotmail.com")
    val message = "hello breno!"

    // SIMPLE CALLING
    // UserEmailer.notify(breno, message) // the kind of effect
    //  .provideLayer(UserEmailer.live) // provide the input for the effect ... like DI
    //  .exitCode // runs the effect

    // HORIZONTAL COMPOSITED CALLING
    // UserEmailer.notify(breno, message)
    //   .provideLayer(userBackendLayer)
    //   .exitCode

    // VERTICAL COMPOSITED CALLING
    import UserSubscription._
    val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscriptionEnv] = userBackendLayer >>> UserSubscription.live

    UserSubscription.subscribe(breno)
      .provideLayer(userSubscriptionLayer)
      .exitCode
  }
}

package com.github.turtton.exampleuitest

import cats.Monad
import cats.effect.{IO, Temporal}
import com.comcast.ip4s.SocketAddress
import io.github.kory33.s2mctest.core.client.api.worldview.{PositionAndOrientation, WorldTime}
import io.github.kory33.s2mctest.core.client.api.{DiscretePath, MinecraftVector, Vector2D}
import io.github.kory33.s2mctest.core.client.{PacketAbstraction, ProtocolPacketAbstraction}
import io.github.kory33.s2mctest.core.clientpool.{AccountPool, ClientPool}
import io.github.kory33.s2mctest.impl.client.abstraction.{DisconnectAbstraction, KeepAliveAbstraction, PlayerPositionAbstraction, TimeUpdateAbstraction}
import io.github.kory33.s2mctest.impl.client.api.MoveClient
import io.github.kory33.s2mctest.impl.clientpool.ClientInitializationImpl
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ClientBound.{DeclareRecipes, JoinGame_WorldNames_IsHard, WindowOpen}
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ServerBound.{Player, PlayerLook, PlayerPosition, PlayerPositionLook}
import monocle.Lens
import monocle.macros.GenLens
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.{GameTest, TestContext}

import scala.concurrent.duration.FiniteDuration

class ChestUITest extends FabricGameTest {
  import cats.effect.unsafe.implicits.global
  import cats.implicits.given
  import io.github.kory33.s2mctest.impl.connection.protocol.versions
  import spire.implicits.given
  import versions.v1_17_1.{loginProtocol, playProtocol, protocolVersion}

  import scala.concurrent.duration.given

  @GameTest(structureName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
  def openAndClickUI(context: TestContext): Unit = {
    val address = SocketAddress.fromString("localhost:25565").get

    val packetAbstraction = ProtocolPacketAbstraction
      .empty[IO, WorldView](playProtocol)
      .thenAbstractWithLens(KeepAliveAbstraction.forProtocol, WorldView.unitLens)

    val accountPool = AccountPool.default[IO].unsafeRunSync()

    val clientPool: ClientPool[IO, ?, ?, WorldView] = ClientPool
      .withInitData(
        accountPool,
        WorldView(),
        ClientInitializationImpl(
          address,
          protocolVersion,
          loginProtocol,
          playProtocol,
          packetAbstraction
        )
      )
      .cached(50)
      .unsafeRunSync()

    clientPool
      .recycledClient
      .use { client =>
        client.readLoopUntilDefined[Nothing] {
          case client.ReadLoopStepResult.WorldUpdate(view) => IO.pure(None)
          case client.ReadLoopStepResult.PacketArrived(packet) =>
            packet match {
              case _: JoinGame_WorldNames_IsHard => IO.pure(None)
              case _: DeclareRecipes =>
                IO {
                  context
                    .getWorld
                    .getServer
                    .execute(() => {
                      //Open Chest Inventory Window
                      val player = context
                        .getWorld
                        .getServer
                        .getPlayerManager
                        .getPlayer(client.identity.uuid)
                      println("Open Window")
                      ChestUI.open(player)
                    })
                } >> IO.pure(None)
              case windowPacket: WindowOpen =>
                IO {
                  println(s"Window opened!${windowPacket.title.json}")
                  //TODO: click slot 0 item and check item name
                  context
                    .getWorld
                    .getServer
                    .execute(() => {
                      context.complete()
                    })
                } >> IO.pure(None)
              case _ => IO(println(s"packet: $packet")) >> IO.pure(None)
            }
        }
      }
      .void
      .unsafeRunAsync {
        case Left(ex) =>
          ex.printStackTrace()
        case Right(_) =>
      }
  }

  case class WorldView()
  object WorldView {
    given unitLens: Lens[WorldView, Unit] = Lens[WorldView, Unit](_ => ())(_ => s => s)
  }
}
package com.github.turtton.exampleuitest

import cats.Monad
import cats.effect.{IO, Temporal}
import com.comcast.ip4s.SocketAddress
import io.github.kory33.s2mctest.core.client.api.worldview.{PositionAndOrientation, WorldTime}
import io.github.kory33.s2mctest.core.client.api.{DiscretePath, MinecraftVector, Vector2D}
import io.github.kory33.s2mctest.core.client.{PacketAbstraction, ProtocolPacketAbstraction}
import io.github.kory33.s2mctest.core.clientpool.{AccountPool, ClientPool}
import io.github.kory33.s2mctest.impl.client.abstraction.{
  DisconnectAbstraction,
  KeepAliveAbstraction,
  PlayerPositionAbstraction,
  TimeUpdateAbstraction
}
import io.github.kory33.s2mctest.impl.client.api.MoveClient
import io.github.kory33.s2mctest.impl.clientpool.ClientInitializationImpl
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ServerBound.{
  Player,
  PlayerLook,
  PlayerPosition,
  PlayerPositionLook
}
import monocle.Lens
import monocle.macros.GenLens

import scala.concurrent.duration.FiniteDuration

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.{GameTest, TestContext}


class ChestUITest extends FabricGameTest {
  import io.github.kory33.s2mctest.impl.connection.protocol.versions
  import versions.v1_17_1.{protocolVersion, loginProtocol, playProtocol}
  import cats.effect.unsafe.implicits.global

  import cats.implicits.given
  import spire.implicits.given
  import scala.concurrent.duration.given

  @GameTest(structureName = FabricGameTest.EMPTY_STRUCTURE)
  def openAndClickUI(context: TestContext): Unit = {
    val address = SocketAddress.fromString("localhost:25565").get

    val packetAbstraction = ProtocolPacketAbstraction
      .empty[IO, WorldView](playProtocol)
      .thenAbstractWithLens(KeepAliveAbstraction.forProtocol, WorldView.unitLens)
      .thenAbstractWithLens(PlayerPositionAbstraction.forProtocol, WorldView.positionLens)
      .thenAbstractWithLens(TimeUpdateAbstraction.forProtocol, WorldView.worldTimeLens)

    val accountPool = AccountPool.default[IO].unsafeRunSync()

    val clientPool = ClientPool
      .withInitData(
        accountPool,
        WorldView(PositionAndOrientation.zero, WorldTime.zero),
        ClientInitializationImpl(address,
          protocolVersion,
          loginProtocol,
          playProtocol,
          packetAbstraction
        )
      )
      .cached(50)
      .unsafeRunSync()

    val program: IO[Unit] = clientPool
      .recycledClient
      .use { client =>
        client.readLoopAndDiscard.use { _ =>
          IO.sleep(1000.seconds)
        }
      }
      .void



    //TODO: connect TestClient and wait
    context.waitAndRun(60, () => {
      val player = context.getWorld.getPlayers.get(0)
      ChestUI.open(player)
      context.complete()
    })


    program.unsafeRunAsync(_ => {})

    //TODO: click slot 0 item and check display name
  }


  case class WorldView(position: PositionAndOrientation, worldTime: WorldTime)
  object WorldView {
    given unitLens: Lens[WorldView, Unit] = Lens[WorldView, Unit](_ => ())(_ => s => s)
    given worldTimeLens: Lens[WorldView, WorldTime] = GenLens[WorldView](_.worldTime)
    given positionLens: Lens[WorldView, PositionAndOrientation] = GenLens[WorldView](_.position)
  }


}
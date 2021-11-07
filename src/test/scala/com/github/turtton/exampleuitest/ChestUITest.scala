package com.github.turtton.exampleuitest

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.given
import cats.{Monad, MonadThrow}
import com.comcast.ip4s.SocketAddress
import com.mojang.brigadier.ParseResults
import io.github.kory33.s2mctest.core.client.ProtocolPacketAbstraction
import io.github.kory33.s2mctest.core.client.worldview.{PositionAndOrientation, WorldTime}
import io.github.kory33.s2mctest.core.clientpool.{AccountPool, ClientPool}
import io.github.kory33.s2mctest.core.connection.codec.interpreters.ParseResult
import io.github.kory33.s2mctest.core.connection.protocol.CodecBinding
import io.github.kory33.s2mctest.core.connection.transport.ProtocolBasedTransport
import io.github.kory33.s2mctest.core.generic.compiletime.Includes
import io.github.kory33.s2mctest.impl.client.abstraction.{KeepAliveAbstraction, PlayerPositionAbstraction, TimeUpdateAbstraction}
import io.github.kory33.s2mctest.impl.clientpool.ClientInitializationImpl
import io.github.kory33.s2mctest.impl.clientpool.ClientInitializationImpl.DoLoginEv
import io.github.kory33.s2mctest.impl.connection.packets.PacketDataPrimitives.{UnspecifiedLengthByteArray, VarInt}
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Login.ClientBound.LoginPluginRequest
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Login.ServerBound.{LoginPluginResponse, LoginStart}
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ClientBound.PluginMessageClientbound
import io.github.kory33.s2mctest.impl.connection.protocol.versions.v19w02a.{loginProtocol, playProtocol, protocolVersion}
import monocle.Lens
import monocle.macros.GenLens
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.{GameTest, TestContext}

import java.awt.font.GlyphJustificationInfo
import java.io.IOException


class ChestUITest extends FabricGameTest {

  @GameTest(structureName = FabricGameTest.EMPTY_STRUCTURE)
  def openAndClickUI(context: TestContext): Unit = {
    val address = SocketAddress.fromString("localhost:25565").get

    val packetAbstraction = ProtocolPacketAbstraction
      .empty[IO, WorldView](playProtocol.asViewedFromClient)
      .thenAbstractWithLens(KeepAliveAbstraction.forProtocol, WorldView.unitLens)
      .thenAbstractWithLens(PlayerPositionAbstraction.forProtocol, WorldView.positionLens)
      .thenAbstractWithLens(TimeUpdateAbstraction.forProtocol, WorldView.worldTimeLens)

    val accountPool = AccountPool.default[IO].unsafeRunSync()

    val clientPool = ClientPool
      .withInitData(
        accountPool,
        WorldView(PositionAndOrientation(0, 0, 0, 0, 0), WorldTime(0, 0)),
        ClientInitializationImpl(
          address,
          //1.17.1 protocol version
          VarInt(756), loginProtocol, playProtocol, packetAbstraction
        )(using DoModLoginEv.doLoginWithPlugin)
      )
      .cached(50)
      .unsafeRunSync()

    val program: IO[Unit] = clientPool.recycledClient.use { client =>
      Monad[IO].foreverM {
        for {
          packet <- client.nextPacket
          state <- client.worldView
          _ <- IO(println((packet, state)))
        } yield ()
      }
    }
    program.unsafeRunAsync((either) => {
      either match {
        case Left(exception) => exception.printStackTrace()
        case Right(_) => {}
      }
    })

    //TODO: connect TestClient and wait
    context.waitAndRun(60, new Runnable {
      override def run(): Unit = {
        val player = context.getWorld.getPlayers.get(0)
        ChestUI.open(player)
        context.complete()
      }
    })



    //TODO: click slot 0 item and check display name
  }


  private case class WorldView(position: PositionAndOrientation, worldTime: WorldTime)

  private object WorldView {
    val unitLens: Lens[WorldView, Unit] = Lens[WorldView, Unit](_ => ())(_ => s => s)
    val worldTimeLens: Lens[WorldView, WorldTime] = GenLens[WorldView](_.worldTime)
    val positionLens: Lens[WorldView, PositionAndOrientation] = GenLens[WorldView](_.position)
  }

  object DoModLoginEv {
    inline given doLoginWithPlugin[
      F[_] : MonadThrow, LoginServerBoundPackets <: Tuple, LoginClientBoundPackets <: Tuple : Includes[LoginPluginRequest]
    ](
       using Includes[CodecBinding[LoginStart]][Tuple.Map[LoginServerBoundPackets, CodecBinding]]
     ): DoLoginEv[F, LoginServerBoundPackets, LoginClientBoundPackets] = (
        transport: ProtocolBasedTransport[F, LoginClientBoundPackets, LoginServerBoundPackets],
        name: String
      ) =>
      transport.writePacket(LoginStart(name)) >> transport.nextPacket >>= {
        case ParseResult.Just(LoginPluginRequest(messageId, channel, _)) =>
          MonadThrow[F].pure(x = {
            //FIXME: ここどうやって書くんだ…？LoginPluginResponseはこれでは返せてないらしい
            println(s"$messageId:$channel")
            LoginPluginResponse(messageId, false, UnspecifiedLengthByteArray(Array.emptyByteArray))
          })
        case failture =>
          MonadThrow[F].raiseError(IOException {
            s""
          })
      }
  }

}
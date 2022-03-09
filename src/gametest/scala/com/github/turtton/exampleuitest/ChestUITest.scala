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
import io.github.kory33.s2mctest.impl.connection.packets.PacketDataCompoundTypes.Slot.Upto_1_17_1
import io.github.kory33.s2mctest.impl.connection.packets.PacketDataPrimitives.{LenPrefixedSeq, NBTCompoundOrEnd, VarInt, UByte}
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ClientBound.{ChunkData_withBlockEntity, DeclareRecipes, Disconnect, JoinGame_WorldNames_IsHard, UpdateLight_WithTrust, WindowItems_withState, WindowOpen_VarInt}
import io.github.kory33.s2mctest.impl.connection.packets.PacketIntent.Play.ServerBound.{ClickWindow, ClickWindowButton, ClickWindow_State, Player, PlayerLook, PlayerPosition, PlayerPositionLook}
import monocle.Lens
import monocle.macros.GenLens
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.katsstuff.typenbt.{NBTCompound, NBTString}
import net.minecraft.item.Items
import net.minecraft.test.{GameTest, TestContext}
import net.minecraft.text.Text
import net.minecraft.util.registry.Registry

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

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

    val clientPool = ClientPool
      .withInitData(
        accountPool,
        WorldView(0),
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
      .freshClient
      .use { client =>
        client.readLoopUntilDefined[Boolean] {
          case client.ReadLoopStepResult.WorldUpdate(view) => IO.pure(None)
          case client.ReadLoopStepResult.PacketArrived(packet) =>
            packet match {
              case _: JoinGame_WorldNames_IsHard | _: ChunkData_withBlockEntity |
                  _: UpdateLight_WithTrust =>
                IO.pure(None)
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
              case windowPacket: WindowOpen_VarInt =>
                IO {
                  val title = windowPacket.title.json
                  println(s"Window opened!$title")
                  //TODO: click slot 0 item and check item name
                  if (title == Text.Serializer.toJson(ChestUI.TITLE)) {
                    println("Target window is detected")
                    val view = client.worldView.unsafeRunSync()
                    view.targetWindowId = windowPacket.id.raw
                  }
                } >> IO.pure(None)
              case windowState: WindowItems_withState[Upto_1_17_1] =>
                  val windowId = windowState.windowId.asShort.toInt
                  if (client.worldView.unsafeRunSync().targetWindowId == windowId) {
                    windowState
                      .items
                      .asVector
                      .zipWithIndex
                      .find(_._1.itemId match {
                        case Some(id) =>
                          id.raw == Registry.ITEM.getRawId(Items.STICK)
                        case _ => false
                      })
                      .map {
                        case (item: Upto_1_17_1, slot: Int) =>
                          println(s"Detected: $item")
                          item.nbt.map {
                            case NBTCompoundOrEnd.Compound(compound) =>
                              compound.getNestedValue[String]("display", "Name").map { displayName =>
                                if (displayName == Text.Serializer.toJson(ChestUI.ITEM_DISPLAY_CLICK_ME)) {
                                  IO(println("Send Click Packet"))
                                    >> client.writePacket(ClickWindow_State[Upto_1_17_1](windowState.windowId, VarInt(0), 0, 0, VarInt(0), LenPrefixedSeq(Vector.empty), Upto_1_17_1(false, None, None, None)))
                                    >> IO.none
                                } else if (displayName == Text.Serializer.toJson(ChestUI.ITEM_DISPLAY_CLICKED)) {
                                  //Ok
                                  IO(context.getWorld.getServer.execute(() => context.complete())) >> IO.some(true)
                                } else IO.none
                              }.getOrElse(IO.none)
                            case _ => IO.none
                          }.getOrElse(IO.none)
                      }.getOrElse(IO.none)
                  } else IO.none
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

  case class WorldView(var targetWindowId: Int)
  object WorldView {
    given unitLens: Lens[WorldView, Unit] = Lens[WorldView, Unit](_ => ())(_ => s => s)
  }
}

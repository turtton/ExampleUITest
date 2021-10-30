package com.github.turtton.exampleuitest

import com.comcast.ip4s.{Host, SocketAddress}
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.{GameTest, TestContext}

class ChestUITest extends FabricGameTest {

  val address: SocketAddress[Host] = SocketAddress.fromString("localhost:25565").get

  @GameTest(structureName = FabricGameTest.EMPTY_STRUCTURE)
  def openAndClickUI(context: TestContext): Unit = {
    //TODO: connect TestClient and wait

    val player = context.getWorld.getPlayers.get(0)
    ChestUI.open(player)

    //TODO: click slot 0 item and check display name
  }


}

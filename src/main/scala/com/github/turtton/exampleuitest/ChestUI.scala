package com.github.turtton.exampleuitest

import com.github.turtton.exampleuitest.ChestUI.{ITEM_DISPLAY_CLICKED, ITEM_DISPLAY_CLICK_ME}
import net.minecraft.entity.player.{PlayerEntity, PlayerInventory}
import net.minecraft.inventory.{Inventory, SimpleInventory}
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.screen.slot.{Slot, SlotActionType}
import net.minecraft.screen.{
  ScreenHandler,
  ScreenHandlerFactory,
  ScreenHandlerType,
  SimpleNamedScreenHandlerFactory
}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

class ChestUI private (syncId: Int, val player: PlayerEntity)
    extends ScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId) {

  val size = 9
  val inventory = new SimpleInventory(1)
  val playerInventory: PlayerInventory = player.getInventory

  var isClicked = false

  addSlot(new Slot(inventory, 0, 0, 0))
  for (i <- 1 until size) {
    addSlot(new ImmutableSlot(inventory, i))
  }
  setStackInSlot(0, 0, getItem)

  for (
    y <- 0 until 3;
    x <- 0 until 9
  ) {
    addSlot(new ImmutableSlot(playerInventory, x + y * 9 + 9))
  }

  override def canUse(player: PlayerEntity): Boolean = true

  override def onSlotClick(
    i: Int,
    j: Int,
    actionType: SlotActionType,
    playerEntity: PlayerEntity
  ): Unit = {
    if (i == 0 && j == 0) {
      isClicked = !isClicked
      setStackInSlot(0, 0, getItem)
    }

  }

  private def getItem: ItemStack = {
    val item = Items.STICK.getDefaultStack.copy()
    val name = if (isClicked) ITEM_DISPLAY_CLICKED else ITEM_DISPLAY_CLICK_ME
    item.setCustomName(name)
    item
  }

  class ImmutableSlot(inventory: Inventory, slot: Int) extends Slot(inventory, slot, 0, 0) {
    override def canInsert(stack: ItemStack): Boolean = false
    override def canTakeItems(playerEntity: PlayerEntity): Boolean = false
  }
}

object ChestUI {

  val TITLE: LiteralText = LiteralText("Test")

  val ITEM_DISPLAY_CLICKED: LiteralText = LiteralText("Clicked")
  val ITEM_DISPLAY_CLICK_ME: LiteralText = LiteralText("ClickMe")

  def open(player: ServerPlayerEntity): Unit = player.openHandledScreen(
    new SimpleNamedScreenHandlerFactory(
      (syncId: Int, _: PlayerInventory, player: PlayerEntity) => new ChestUI(syncId, player),
      TITLE
    )
  )
}

package com.github.turtton.exampleuitest

import net.minecraft.entity.player.{PlayerEntity, PlayerInventory}
import net.minecraft.inventory.{Inventory, SimpleInventory}
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.screen.slot.{Slot, SlotActionType}
import net.minecraft.screen.{ScreenHandler, ScreenHandlerFactory, ScreenHandlerType, SimpleNamedScreenHandlerFactory}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

class ChestUI private (syncId: Int, val player: PlayerEntity) extends ScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId) {

  val size = 9
  val inventory = new SimpleInventory(1)
  val playerInventory: PlayerInventory = player.getInventory

  var isClicked = false

  addSlot(new Slot(inventory, 0, 0, 0))
  for (i <- 1 until size) {
    addSlot(new ImmutableSlot(inventory, i))
  }

  for (
    y <- 0 until 3;
    x <- 0 until 9
  ) {
    addSlot(new ImmutableSlot(playerInventory, x + y * 9 + 9))
  }


  override def canUse(player: PlayerEntity): Boolean = true

  override def onSlotClick(i: Int, j: Int, actionType: SlotActionType, playerEntity: PlayerEntity): Unit = {
    if (i == 0 && j == 0) {
      val item = Items.STICK.getDefaultStack.copy()
      if (isClicked) {
        item.setCustomName(new LiteralText("Clicked"))
      } else {
        item.setCustomName(new LiteralText("ClickMe"))
      }
      isClicked = !isClicked

      setStackInSlot(0, 0, item)
    }

  }

  class ImmutableSlot(inventory: Inventory, slot: Int) extends Slot(inventory, slot, 0, 0) {
    override def canInsert(stack: ItemStack): Boolean = false
    override def canTakeItems(playerEntity: PlayerEntity): Boolean = false
  }


}

object ChestUI{
  def open(player: ServerPlayerEntity): Unit = player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId: Int, _: PlayerInventory, player: PlayerEntity) => new ChestUI(syncId, player), new LiteralText("Test")))
}
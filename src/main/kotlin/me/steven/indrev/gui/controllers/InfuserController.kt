package me.steven.indrev.gui.controllers

import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.widget.WGridPanel
import io.github.cottonmc.cotton.gui.widget.WItemSlot
import me.steven.indrev.IndustrialRevolution
import me.steven.indrev.gui.PatchouliEntryShortcut
import me.steven.indrev.gui.widgets.ProcessWidget
import me.steven.indrev.utils.add
import me.steven.indrev.utils.configure
import me.steven.indrev.utils.identifier
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.util.Identifier

class InfuserController(syncId: Int, playerInventory: PlayerInventory, ctx: ScreenHandlerContext) :
    SyncedGuiDescription(
        IndustrialRevolution.INFUSER_HANDLER,
        syncId,
        playerInventory,
        getBlockInventory(ctx),
        getBlockPropertyDelegate(ctx)
    ), PatchouliEntryShortcut {
    init {
        val root = WGridPanel()
        setRootPanel(root)
        configure("block.indrev.infuser", ctx, playerInventory, blockInventory, propertyDelegate)

        val firstInput = WItemSlot.of(blockInventory, 2)
        root.add(firstInput, 2.0, 1.5)

        val secondInput = WItemSlot.of(blockInventory, 3)
        root.add(secondInput, 3.0, 1.5)

        val processWidget = ProcessWidget(propertyDelegate)
        root.add(processWidget, 4.2, 1.5)

        val outputSlot = WItemSlot.outputOf(blockInventory, 4)
        outputSlot.isInsertingAllowed = false
        root.add(outputSlot, 6.0, 1.5)

        root.validate(this)
    }

    override fun canUse(player: PlayerEntity?): Boolean = true

    override fun getEntry(): Identifier = identifier("machines/basic_machines")

    override fun getPage(): Int = 4

    companion object {
        val SCREEN_ID = identifier("infuser")
    }
}
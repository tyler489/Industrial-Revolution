package me.steven.indrev.blockentities.crafters

import alexiil.mc.lib.attributes.Simulation
import alexiil.mc.lib.attributes.fluid.FluidTransferable
import alexiil.mc.lib.attributes.fluid.GroupedFluidInv
import alexiil.mc.lib.attributes.fluid.amount.FluidAmount
import alexiil.mc.lib.attributes.fluid.filter.FluidFilter
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume
import me.steven.indrev.api.machines.Tier
import me.steven.indrev.components.FluidComponent
import me.steven.indrev.components.TemperatureComponent
import me.steven.indrev.inventories.inventory
import me.steven.indrev.items.upgrade.Enhancer
import me.steven.indrev.recipes.machines.FluidInfuserRecipe
import me.steven.indrev.recipes.machines.IRRecipeType
import me.steven.indrev.registry.MachineRegistry
import me.steven.indrev.utils.createWrapper
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

class FluidInfuserBlockEntity(tier: Tier, pos: BlockPos, state: BlockState)
    : CraftingMachineBlockEntity<FluidInfuserRecipe>(tier, MachineRegistry.FLUID_INFUSER_REGISTRY, pos, state) {

    override val enhancerSlots: IntArray = intArrayOf(4, 5, 6, 7)
    override val availableEnhancers: Array<Enhancer> = Enhancer.DEFAULT

    init {
        this.temperatureComponent = TemperatureComponent(this, 0.06, 700..1100, 1400)
        this.inventoryComponent = inventory(this) {
            input { slot = 2 }
            output { slot = 3 }
        }
        this.fluidComponent = FluidInfuserFluidComponent()
    }

    override val type: IRRecipeType<FluidInfuserRecipe> = FluidInfuserRecipe.TYPE

    inner class FluidInfuserFluidComponent : FluidComponent({ this }, FluidAmount.ofWhole(8) , 2) {
        init {
            this.inputTanks = intArrayOf(0)
            this.outputTanks = intArrayOf(1)
        }

        private val grouped = this.createWrapper(1, 0)

        override fun getGroupedInv(): GroupedFluidInv = grouped

        override fun attemptInsertion(fluid: FluidVolume, simulation: Simulation?): FluidVolume? {
            return grouped.attemptInsertion(fluid, simulation)
        }

        override fun getMinimumAcceptedAmount(): FluidAmount? {
            return grouped.minimumAcceptedAmount
        }

        override fun getInsertionFilter(): FluidFilter? {
            return grouped.insertionFilter
        }

        override fun attemptExtraction(
            filter: FluidFilter?,
            maxAmount: FluidAmount,
            simulation: Simulation?
        ): FluidVolume {
            return grouped.attemptExtraction(filter, maxAmount, simulation)
        }

        override fun attemptAnyExtraction(maxAmount: FluidAmount?, simulation: Simulation?): FluidVolume? {
            return grouped.attemptAnyExtraction(maxAmount, simulation)
        }

        override fun getInteractInventory(tank: Int): FluidTransferable {
            return createWrapper(tank, tank)
        }
    }
}
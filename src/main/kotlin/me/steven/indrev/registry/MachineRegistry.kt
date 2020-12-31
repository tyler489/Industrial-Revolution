package me.steven.indrev.registry

import dev.technici4n.fasttransferlib.api.energy.EnergyApi
import dev.technici4n.fasttransferlib.api.energy.EnergyIo
import me.steven.indrev.IndustrialRevolution.CONFIG
import me.steven.indrev.api.machines.Tier
import me.steven.indrev.blockentities.MachineBlockEntity
import me.steven.indrev.blockentities.cables.CableBlockEntity
import me.steven.indrev.blockentities.crafters.*
import me.steven.indrev.blockentities.farms.*
import me.steven.indrev.blockentities.generators.BiomassGeneratorBlockEntity
import me.steven.indrev.blockentities.generators.CoalGeneratorBlockEntity
import me.steven.indrev.blockentities.generators.HeatGeneratorBlockEntity
import me.steven.indrev.blockentities.generators.SolarGeneratorBlockEntity
import me.steven.indrev.blockentities.modularworkbench.ModularWorkbenchBlockEntity
import me.steven.indrev.blockentities.storage.ChargePadBlockEntity
import me.steven.indrev.blockentities.storage.LazuliFluxContainerBlockEntity
import me.steven.indrev.blocks.machine.*
import me.steven.indrev.config.IConfig
import me.steven.indrev.gui.controllers.machines.*
import me.steven.indrev.items.energy.MachineBlockItem
import me.steven.indrev.utils.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry
import net.fabricmc.fabric.api.tool.attribute.v1.FabricToolTags
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.Material
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.item.BlockItem
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.math.Direction
import java.util.*
import java.util.function.Supplier

class MachineRegistry(private val key: String, val upgradeable: Boolean = true, vararg val tiers: Tier = Tier.values()) {

    private val configs: MutableMap<Tier, IConfig> = EnumMap(Tier::class.java)
    private val blocks: MutableMap<Tier, Block> = EnumMap(Tier::class.java)
    val blockEntities: MutableMap<Tier, BlockEntityType<*>> = EnumMap(Tier::class.java)

    fun blockProvider(blockProvider: MachineRegistry.(Tier) -> Block): MachineRegistry {
        tiers.forEach { tier ->
            val block = blockProvider(this, tier)
            if (FabricLoader.getInstance().environmentType == EnvType.CLIENT)
                BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout())
            val blockItem =
                if (block is MachineBlock) MachineBlockItem(block, itemSettings())
                else BlockItem(block, itemSettings())
            identifier("${key}_${tier.toString().toLowerCase()}").apply {
                block(block)
                item(blockItem)
                if (block is MachineBlock && block.config != null)
                    configs[tier] = block.config
            }
            blocks[tier] = block
        }
        return this
    }

    fun blockEntityProvider(entityProvider: (Tier) -> () -> BlockEntity): MachineRegistry {
        tiers.forEach { tier ->
            val blockEntityType =
                BlockEntityType.Builder.create(Supplier(entityProvider(tier)), block(tier)).build(null)
            identifier("${key}_${tier.toString().toLowerCase()}").apply {
                blockEntityType(blockEntityType)
            }
            blockEntities[tier] = blockEntityType
        }
        return this
    }

    fun forEachBlockEntity(action: (Tier, BlockEntityType<*>) -> Unit) = blockEntities.forEach(action)

    fun forEachBlock(action: (Tier, Block) -> Unit) = blocks.forEach(action)

    fun blockEntityType(tier: Tier) = blockEntities[tier]
        ?: throw IllegalStateException("invalid tier for machine $key")

    fun config(tier: Tier) = configs[tier]
        ?: throw java.lang.IllegalStateException("invalid tier for machine $key")

    fun block(tier: Tier) = blocks[tier]
        ?: throw java.lang.IllegalStateException("invalid tier for machine $key")

    fun energyProvider(provider: (Tier) -> (BlockEntity, Direction) -> EnergyIo?): MachineRegistry {
        blockEntities.forEach { (tier, type) ->
            EnergyApi.SIDED.registerForBlockEntities(provider(tier), type)
        }
        return this
    }
    
    fun defaultEnergyProvider(): MachineRegistry = energyProvider { { be, _ -> be as? MachineBlockEntity<*>? } }

    @Suppress("UNCHECKED_CAST")
    @Environment(EnvType.CLIENT)
    fun <T : BlockEntity> registerBlockEntityRenderer(renderer: (BlockEntityRenderDispatcher) -> BlockEntityRenderer<T>) {
        blockEntities.forEach { (_, type) ->
            BlockEntityRendererRegistry.INSTANCE.register(type as BlockEntityType<T>) { dispatcher -> renderer(dispatcher) }
        }
    }

    @Environment(EnvType.CLIENT)
    fun setRenderLayer(layer: RenderLayer) {
        blocks.forEach { (_, block) -> BlockRenderLayerMap.INSTANCE.putBlock(block, layer) }
    }

    companion object {

        private val SETTINGS = {
            FabricBlockSettings.of(Material.METAL)
                .sounds(BlockSoundGroup.METAL)
                .requiresTool()
                .breakByTool(FabricToolTags.PICKAXES, 2)
                .strength(5.0f, 6.0f)
                .luminance { state -> if (state[MachineBlock.WORKING_PROPERTY]) 7 else 0 }
        }

        val COAL_GENERATOR_REGISTRY = MachineRegistry("coal_generator", false, Tier.MK1)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.generators.coalGenerator, ::CoalGeneratorController
                )
            }
            .blockEntityProvider { { CoalGeneratorBlockEntity() } }
            .defaultEnergyProvider()

        val SOLAR_GENERATOR_REGISTRY = MachineRegistry("solar_generator", false, Tier.MK1, Tier.MK3)
            .blockProvider { tier ->
                MachineBlock(
                    this,
                    SETTINGS(), 
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.generators.solarGeneratorMk1
                        else -> CONFIG.generators.solarGeneratorMk3
                    }, ::SolarGeneratorController
                )
            }
            .blockEntityProvider { tier -> { SolarGeneratorBlockEntity(tier) } }
            .defaultEnergyProvider()

        val BIOMASS_GENERATOR_REGISTRY = MachineRegistry("biomass_generator", false, Tier.MK3)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.generators.biomassGenerator, ::BiomassGeneratorController
                )
            }
            .blockEntityProvider { tier -> { BiomassGeneratorBlockEntity(tier) } }
            .defaultEnergyProvider()

        val HEAT_GENERATOR_REGISTRY = MachineRegistry("heat_generator", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.generators.heatGenerator, ::HeatGeneratorController
                )
            }
            .blockEntityProvider { tier -> { HeatGeneratorBlockEntity(tier) } }
            .defaultEnergyProvider()

        val LAZULI_FLUX_CONTAINER_REGISTRY = MachineRegistry("lazuli_flux_container", false)
            .blockProvider { tier -> LazuliFluxContainerBlock(this, SETTINGS(), tier) }
            .blockEntityProvider { tier -> { LazuliFluxContainerBlockEntity(tier) } }
            .energyProvider {
                { be, dir ->
                    val blockEntity = be as? LazuliFluxContainerBlockEntity
                    if (blockEntity != null) LazuliFluxContainerBlockEntity.LFCEnergyIo(blockEntity, dir) else null
                }
            }

        val ELECTRIC_FURNACE_REGISTRY = MachineRegistry("electric_furnace")
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(), tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.electricFurnaceMk1
                        Tier.MK2 -> CONFIG.machines.electricFurnaceMk2
                        Tier.MK3 -> CONFIG.machines.electricFurnaceMk3
                        else -> CONFIG.machines.electricFurnaceMk4
                    }, ::ElectricFurnaceController
                )
            }
            .blockEntityProvider { tier -> { ElectricFurnaceBlockEntity(tier) } }
            .defaultEnergyProvider()

        val PULVERIZER_REGISTRY = MachineRegistry("pulverizer")
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.pulverizerMk1
                        Tier.MK2 -> CONFIG.machines.pulverizerMk2
                        Tier.MK3 -> CONFIG.machines.pulverizerMk3
                        else -> CONFIG.machines.pulverizerMk4
                    }, ::PulverizerController
                )
            }
            .blockEntityProvider { tier -> { PulverizerBlockEntity(tier) } }
            .defaultEnergyProvider()

        val COMPRESSOR_REGISTRY = MachineRegistry("compressor")
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.compressorMk1
                        Tier.MK2 -> CONFIG.machines.compressorMk2
                        Tier.MK3 -> CONFIG.machines.compressorMk3
                        else -> CONFIG.machines.compressorMk4
                    }, ::CompressorController
                )
            }
            .blockEntityProvider { tier -> { CompressorBlockEntity(tier) } }
            .defaultEnergyProvider()

        val INFUSER_REGISTRY = MachineRegistry("infuser")
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.infuserMk1
                        Tier.MK2 -> CONFIG.machines.infuserMk2
                        Tier.MK3 -> CONFIG.machines.infuserMk3
                        else -> CONFIG.machines.infuserMk4
                    }, ::InfuserController
                )
            }
            .blockEntityProvider { tier -> { SolidInfuserBlockEntity(tier) } }
            .defaultEnergyProvider()

        val SAWMILL_REGISTRY = MachineRegistry("sawmill")
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(), tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.sawmillMk1
                        Tier.MK2 -> CONFIG.machines.sawmillMk2
                        Tier.MK3 -> CONFIG.machines.sawmillMk3
                        else -> CONFIG.machines.sawmillMk4
                    }, ::SawmillController
                )
            }
            .blockEntityProvider { tier -> { SawmillBlockEntity(tier) } }
            .defaultEnergyProvider()

        val RECYCLER_REGISTRY = MachineRegistry("recycler", false, Tier.MK2)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.machines.recycler, ::RecyclerController
                )
            }
            .blockEntityProvider { tier -> { RecyclerBlockEntity(tier) } }
            .defaultEnergyProvider()

        val SMELTER_REGISTRY = MachineRegistry("smelter", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.machines.smelter, ::SmelterController
                )
            }
            .blockEntityProvider { tier -> { SmelterBlockEntity(tier) } }
            .defaultEnergyProvider()

        val CONDENSER_REGISTRY = MachineRegistry("condenser", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this, SETTINGS(), tier, CONFIG.machines.condenser, ::CondenserController
                )
            }
            .blockEntityProvider { tier -> { CondenserBlockEntity(tier) } }
            .defaultEnergyProvider()

        val ELECTRIC_FURNACE_FACTORY_REGISTRY = MachineRegistry("electric_furnace_factory", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    CONFIG.machines.electricFurnaceFactory,
                    ::ElectricFurnaceFactoryController
                )
            }
            .blockEntityProvider { tier -> { ElectricFurnaceFactoryBlockEntity(tier) } }
            .defaultEnergyProvider()

        val PULVERIZER_FACTORY_REGISTRY = MachineRegistry("pulverizer_factory", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    CONFIG.machines.pulverizerFactory,
                    ::PulverizerFactoryController
                )
            }
            .blockEntityProvider { tier -> { PulverizerFactoryBlockEntity(tier) } }
            .defaultEnergyProvider()

        val COMPRESSOR_FACTORY_REGISTRY = MachineRegistry("compressor_factory", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    CONFIG.machines.compressorFactory,
                    ::CompressorFactoryController
                )
            }
            .blockEntityProvider { tier -> { CompressorFactoryBlockEntity(tier) } }
            .defaultEnergyProvider()

        val INFUSER_FACTORY_REGISTRY = MachineRegistry("infuser_factory", false, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    CONFIG.machines.infuserFactory,
                    ::InfuserFactoryController
                )
            }
            .blockEntityProvider { tier -> { InfuserFactoryBlockEntity(tier) } }
            .defaultEnergyProvider()

        val DRAIN_REGISTRY = MachineRegistry("drain", false, Tier.MK1)
            .blockProvider { tier ->
                MachineBlock(
                    this, SETTINGS(), tier, CONFIG.machines.drain, null
                )
            }
            .blockEntityProvider { tier -> { DrainBlockEntity(tier) } }
            .defaultEnergyProvider()

        val PUMP_REGISTRY = MachineRegistry("pump", false, Tier.MK1)
            .blockProvider { PumpBlock(this, SETTINGS().nonOpaque()) }
            .blockEntityProvider { tier -> { PumpBlockEntity(tier) } }
            .energyProvider { { be, dir -> if (dir == Direction.UP) be as? MachineBlockEntity<*> else null } }

        val FLUID_INFUSER_REGISTRY = MachineRegistry("fluid_infuser", true)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.fluidInfuserMk1
                        Tier.MK2 -> CONFIG.machines.fluidInfuserMk2
                        Tier.MK3 -> CONFIG.machines.fluidInfuserMk3
                        else -> CONFIG.machines.fluidInfuserMk4
                    },
                    ::FluidInfuserController
                )
            }
            .blockEntityProvider { tier -> { FluidInfuserBlockEntity(tier) } }
            .defaultEnergyProvider()

        val CHOPPER_REGISTRY = MachineRegistry("chopper", true)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.chopperMk1
                        Tier.MK2 -> CONFIG.machines.chopperMk2
                        Tier.MK3 -> CONFIG.machines.chopperMk3
                        else -> CONFIG.machines.chopperMk4
                    }, ::ChopperController
                )
            }
            .blockEntityProvider { tier -> { ChopperBlockEntity(tier) } }
            .defaultEnergyProvider()

        val FARMER_REGISTRY = MachineRegistry("farmer", true)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.farmerMk1
                        Tier.MK2 -> CONFIG.machines.farmerMk2
                        Tier.MK3 -> CONFIG.machines.farmerMk3
                        else -> CONFIG.machines.farmerMk4
                    }, ::FarmerController
                )
            }
            .blockEntityProvider { tier -> { FarmerBlockEntity(tier) } }
            .defaultEnergyProvider()

        val RANCHER_REGISTRY = MachineRegistry("rancher", true)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK1 -> CONFIG.machines.rancherMk1
                        Tier.MK2 -> CONFIG.machines.rancherMk2
                        Tier.MK3 -> CONFIG.machines.rancherMk3
                        else -> CONFIG.machines.rancherMk4
                    }, ::RancherController
                )
            }
            .blockEntityProvider { tier -> { RancherBlockEntity(tier) } }
            .defaultEnergyProvider()

        val MINER_REGISTRY = MachineRegistry("miner", false, Tier.MK4)
            .blockProvider { tier -> MinerBlock(this, SETTINGS(), tier) }
            .blockEntityProvider { tier -> { MinerBlockEntity(tier) } }
            .defaultEnergyProvider()

        val FISHING_FARM_REGISTRY = MachineRegistry("fishing_farm", false, Tier.MK2, Tier.MK3, Tier.MK4)
            .blockProvider { tier ->
                HorizontalFacingMachineBlock(
                    this,
                    SETTINGS(),
                    tier,
                    when (tier) {
                        Tier.MK2 -> CONFIG.machines.fishingMk2
                        Tier.MK3 -> CONFIG.machines.fishingMk3
                        else -> CONFIG.machines.fishingMk4
                    }, ::FishingFarmController
                )
            }
            .blockEntityProvider { tier -> { FishingFarmBlockEntity(tier) } }
            .defaultEnergyProvider()

        val MODULAR_WORKBENCH_REGISTRY = MachineRegistry("modular_workbench", false, Tier.MK4)
            .blockProvider { tier ->
                ModularWorkbenchBlock(
                    this,
                    SETTINGS().nonOpaque(),
                    tier,
                    CONFIG.machines.modularWorkbench,
                    ::ModularWorkbenchController
                )
            }
            .blockEntityProvider { tier -> { ModularWorkbenchBlockEntity(tier) } }
            .defaultEnergyProvider()

        val CHARGE_PAD_REGISTRY = MachineRegistry("charge_pad", false, Tier.MK4)
            .blockProvider { tier -> ChargePadBlock(this, SETTINGS(), tier) }
            .blockEntityProvider { tier -> { ChargePadBlockEntity(tier) } }
            .energyProvider { { be, dir -> if (dir == Direction.DOWN) ChargePadBlockEntity.ChargePadEnergyIo(be as ChargePadBlockEntity) else null } }

        val CABLE_REGISTRY = MachineRegistry("cable", false, Tier.MK1, Tier.MK2, Tier.MK3, Tier.MK4)
            .blockProvider { tier -> CableBlock(SETTINGS().luminance(0).nonOpaque(), tier) }
            .blockEntityProvider { tier -> { CableBlockEntity(tier) } }
    }
}
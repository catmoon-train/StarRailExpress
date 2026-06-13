package io.wifi.starrailexpress.index;

import java.util.function.Function;

import org.agmas.noellesroles.init.SREFumoBlocks;

import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.PlaneSmallDoorBlock;
import io.wifi.starrailexpress.content.block.PlaneTrainDoorBlock;
import io.wifi.starrailexpress.content.block.RemoteRedstoneBlock;
import io.wifi.starrailexpress.content.block.TrainLightBlock;
import io.wifi.starrailexpress.content.block.UpSmallDoorBlock;
import io.wifi.starrailexpress.content.block.UpTrainDoorBlock;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;

public interface SREBlocks {
    public static ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            SRE.id("misc_block"));
    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(SRE.MOD_ID);

    Block TRAIN_LIGHT = registerOpBlock("train_light", new TrainLightBlock(
            (Block.Properties.of().replaceable().strength(-1.0F, 3600000.8F)
                    .mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion()
                    .lightLevel(TrainLightBlock.LIGHT_EMISSION))),
            new Item.Properties().rarity(Rarity.EPIC));
    Block REMOTE_REDSTONE = registerOpBlock("remote_redstone", new RemoteRedstoneBlock(
            (Block.Properties.of().replaceable().strength(-1.0F, 3600000.8F)
                    .mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion())),
            new Item.Properties().rarity(Rarity.EPIC));

    // 卷帘门

    Block UP_GLASS_DOOR = registerBlock("up_glass_door", new UpSmallDoorBlock(() -> TMMBlockEntities.UP_GLASS_DOOR,
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));
    Block UP_WOOD_DOOR = registerBlock("up_wood_door", new UpSmallDoorBlock(() -> TMMBlockEntities.UP_WOOD_DOOR,
            BlockBehaviour.Properties.ofFullCopy(UP_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));
    Block UP_STEEL_DOOR = registerBlock("up_steel_door",
            new UpTrainDoorBlock(() -> TMMBlockEntities.UP_STEEL_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(UP_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));

    // 飞机门
    Block PLANE_GLASS_DOOR = registerBlock("plane_glass_door",
            new PlaneSmallDoorBlock(() -> TMMBlockEntities.PLANE_GLASS_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));
    Block PLANE_WOOD_DOOR = registerBlock("plane_wood_door",
            new PlaneSmallDoorBlock(() -> TMMBlockEntities.PLANE_WOOD_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(PLANE_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));
    Block PLANE_STEEL_DOOR = registerBlock("plane_steel_door",
            new PlaneTrainDoorBlock(() -> TMMBlockEntities.PLANE_STEEL_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(PLANE_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.EPIC));

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerOpBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, CreativeModeTabs.OP_BLOCKS);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.starrailexpress.misc_blocks")).icon(() -> {
                    return new ItemStack(SREFumoBlocks.BAMBOO_PLUSH.asItem());
                })
                .build());
        blockRegistrar.registerEntries();
    }

    private static Function<BlockState, MapColor> waterloggedMapColor(MapColor mapColor) {
        return (blockState) -> (Boolean) blockState.getValue(BlockStateProperties.WATERLOGGED) ? MapColor.WATER
                : mapColor;
    }
}

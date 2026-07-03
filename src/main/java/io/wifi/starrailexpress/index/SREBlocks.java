package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.*;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModSceneBlocks;

import java.util.function.Function;

public interface SREBlocks {
    /** 该常量已废弃：SRE 方块现已合并到 ModBlocks.BLOCK_CREATIVE_GROUP（SRE 方块）。 */
    @Deprecated
    public static net.minecraft.resources.ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP =
            org.agmas.noellesroles.init.ModBlocks.BLOCK_CREATIVE_GROUP;
    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(SRE.MOD_ID);

    Block TRAIN_LIGHT = registerSceneOpBlock("train_light", new TrainLightBlock(
            (Block.Properties.of().replaceable().strength(-1.0F, 3600000.8F)
                    .mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion()
                    .lightLevel(TrainLightBlock.LIGHT_EMISSION))),
            new Item.Properties().rarity(Rarity.EPIC));
    Block REMOTE_REDSTONE = registerSceneOpBlock("remote_redstone", new RemoteRedstoneBlock(
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

    Block LOCKABLE_SMALL_BUTTON = registerBlock("lockable_small_button", new LockableSmallButtonBlock(BlockBehaviour.Properties.of()
            .sound(SoundType.CHERRY_WOOD).noOcclusion().forceSolidOn().noCollission().strength(-1.0f, 3600000.0f)));
    Block LOCKABLE_ELEVATOR_BUTTON = registerBlock("lockable_elevator_button",
            new LockableElevatorButtonBlock(BlockBehaviour.Properties.ofFullCopy(LOCKABLE_SMALL_BUTTON)));

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, ModBlocks.BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, ModBlocks.BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerSceneOpBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings,
                CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    }

    static void initialize() {
        // SRE 方块现已合并到 ModBlocks.BLOCK_CREATIVE_GROUP，不再单独注册 starrailexpress:misc_block 标签
        blockRegistrar.registerEntries();
    }

    private static Function<BlockState, MapColor> waterloggedMapColor(MapColor mapColor) {
        return (blockState) -> (Boolean) blockState.getValue(BlockStateProperties.WATERLOGGED) ? MapColor.WATER
                : mapColor;
    }
}

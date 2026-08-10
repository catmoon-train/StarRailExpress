package io.wifi.starrailexpress.index;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.agmas.noellesroles.init.ModBlocks;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.LockableElevatorButtonBlock;
import io.wifi.starrailexpress.content.block.LockableSmallButtonBlock;
import io.wifi.starrailexpress.content.block.PlaneSmallDoorBlock;
import io.wifi.starrailexpress.content.block.PlaneTrainDoorBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block.TrainDoorBlock;
import io.wifi.starrailexpress.content.block.UpSmallDoorBlock;
import io.wifi.starrailexpress.content.block.UpTrainDoorBlock;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public interface SREDoorBlocks {
    public static class CustomDoorBlockAndEntity {
        public final Block block;
        public final BlockEntityType<? extends SmallDoorBlockEntity> blockEntity;
        public final ResourceLocation texture;

        public CustomDoorBlockAndEntity(Block block, BlockEntityType<? extends SmallDoorBlockEntity> entity,
                ResourceLocation texture) {
            this.block = block;
            this.blockEntity = entity;
            this.texture = texture;
        }
    }

    public static final HashMap<ResourceLocation, CustomDoorBlockAndEntity> DOOR_BLOCK_AND_ENTITIES = new HashMap<>();
    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(SRE.MOD_ID);
    public static final BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(
            SRE.TMM_MOD_ID);

    public static BlockEntityType<? extends DoorBlockEntity> getDoorBlockEntityType(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null)
            return null;
        var info = DOOR_BLOCK_AND_ENTITIES.getOrDefault(id, null);
        if (info == null)
            return null;
        return info.blockEntity;
    }

    public static Block registerCustomSmallDoorBlockAndCreateEntity(String id,
            Block.Properties blockSettings,
            Item.Properties settings,
            ResourceLocation texture) {

        AtomicReference<BlockEntityType<SmallDoorBlockEntity>> ref = new AtomicReference<>();
        SmallDoorBlock block = new SmallDoorBlock(() -> ref.get(), blockSettings);
        Block b = registerDoorBlock(id, block, settings);
        BlockEntityType<SmallDoorBlockEntity> entity = blockEntityRegistrar.create(id,
                BlockEntityType.Builder.of(
                        (pos, state) -> SmallDoorBlockEntity.createCustom(ref.get(), pos,
                                state),
                        block));
        // 3. 创建完成后，将结果存入 ref
        ref.set(entity);

        DOOR_BLOCK_AND_ENTITIES.put(SRE.id(id), new CustomDoorBlockAndEntity(b, entity, texture));
        return b;
    }

    public static Block registerCustomTrainDoorBlockAndCreateEntity(String id,
            Block.Properties blockSettings,
            Item.Properties settings,
            ResourceLocation texture) {

        AtomicReference<BlockEntityType<SmallDoorBlockEntity>> ref = new AtomicReference<>();
        SmallDoorBlock block = new TrainDoorBlock(() -> ref.get(), blockSettings);
        Block b = registerDoorBlock(id, block, settings);
        BlockEntityType<SmallDoorBlockEntity> entity = blockEntityRegistrar.create(id,
                BlockEntityType.Builder.of(
                        (pos, state) -> SmallDoorBlockEntity.createCustom(ref.get(), pos,
                                state),
                        block));
        // 3. 创建完成后，将结果存入 ref
        ref.set(entity);

        DOOR_BLOCK_AND_ENTITIES.put(SRE.id(id), new CustomDoorBlockAndEntity(b, entity, texture));
        return b;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerDoorBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, ModBlocks.BLOCK_DOORS_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerDoorBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, ModBlocks.BLOCK_DOORS_GROUP);
    }

    // 纸门
    Block SMALL_PAPER_DOOR = registerCustomSmallDoorBlockAndCreateEntity("small_paper_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/small_paper_door.png"));

    // SCP门
    Block SCP_DOOR = registerCustomSmallDoorBlockAndCreateEntity("scp_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.COPPER),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/scp_door.png"));
    // 卷帘门
    Block UP_GLASS_DOOR = registerDoorBlock(
            "up_glass_door", new UpSmallDoorBlock(() -> TMMBlockEntities.UP_GLASS_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR)
                            .sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));
    Block UP_WOOD_DOOR = registerDoorBlock("up_wood_door", new UpSmallDoorBlock(() -> TMMBlockEntities.UP_WOOD_DOOR,
            BlockBehaviour.Properties.ofFullCopy(UP_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));
    Block UP_STEEL_DOOR = registerDoorBlock("up_steel_door",
            new UpTrainDoorBlock(() -> TMMBlockEntities.UP_STEEL_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(UP_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));

    // 飞机门
    Block PLANE_GLASS_DOOR = registerDoorBlock("plane_glass_door",
            new PlaneSmallDoorBlock(() -> TMMBlockEntities.PLANE_GLASS_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR)
                            .sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));
    Block PLANE_WOOD_DOOR = registerDoorBlock("plane_wood_door",
            new PlaneSmallDoorBlock(() -> TMMBlockEntities.PLANE_WOOD_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(PLANE_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));
    Block PLANE_STEEL_DOOR = registerDoorBlock("plane_steel_door",
            new PlaneTrainDoorBlock(() -> TMMBlockEntities.PLANE_STEEL_DOOR,
                    BlockBehaviour.Properties.ofFullCopy(PLANE_GLASS_DOOR).sound(SoundType.COPPER)),
            new Item.Properties().rarity(Rarity.COMMON));

    Block LOCKABLE_SMALL_BUTTON = registerDoorBlock("lockable_small_button",
            new LockableSmallButtonBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.CHERRY_WOOD).noOcclusion().forceSolidOn().noCollission()
                    .strength(-1.0f, 3600000.0f)));
    Block LOCKABLE_ELEVATOR_BUTTON = registerDoorBlock("lockable_elevator_button",
            new LockableElevatorButtonBlock(BlockBehaviour.Properties.ofFullCopy(LOCKABLE_SMALL_BUTTON)));

    public static void initialize() {
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
        ItemGroupEvents.modifyEntriesEvent(ModBlocks.BLOCK_DOORS_GROUP)
                .register((itemGroup) -> {
                    itemGroup.accept(TMMBlocks.SMALL_BUTTON);
                    itemGroup.accept(TMMBlocks.ELEVATOR_BUTTON);
                    itemGroup.accept(TMMBlocks.SMALL_WOOD_DOOR);
                    itemGroup.accept(TMMBlocks.SMALL_GLASS_DOOR);
                    itemGroup.accept(TMMBlocks.COCKPIT_DOOR);
                    itemGroup.accept(TMMBlocks.NAVY_STEEL_DOOR);
                    itemGroup.accept(TMMBlocks.KHAKI_STEEL_DOOR);
                    itemGroup.accept(TMMBlocks.METAL_SHEET_DOOR);
                    itemGroup.accept(TMMBlocks.MUNTZ_STEEL_DOOR);
                    itemGroup.accept(TMMBlocks.MAROON_STEEL_DOOR);
                    itemGroup.accept(TMMBlocks.ANTHRACITE_STEEL_DOOR);
                });
    }
}

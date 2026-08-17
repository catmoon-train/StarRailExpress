/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    // 自定义风格小门
    // 科幻门 - 青色发光条的金属门
    Block SCIFI_DOOR = registerCustomSmallDoorBlockAndCreateEntity("scifi_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.COPPER),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/scifi_door.png"));
    // 加固钢门 - 厚钢板配铆钉
    Block REINFORCED_DOOR = registerCustomSmallDoorBlockAndCreateEntity("reinforced_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/reinforced_door.png"));
    // 监狱门 - 黑色铁栅门
    Block PRISON_DOOR = registerCustomSmallDoorBlockAndCreateEntity("prison_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/prison_door.png"));
    // 木门 - 标准竖向木板门
    Block WOOD_DOOR = registerCustomSmallDoorBlockAndCreateEntity("wood_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/wood_door.png"));
    // 金库门 - 厚重钢门配圆形转盘
    Block VAULT_DOOR = registerCustomSmallDoorBlockAndCreateEntity("vault_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/vault_door.png"));

    // 中国风小门
    // 朱红宫门 - 红色门板配金色门钉与门环
    Block CHINESE_RED_DOOR = registerCustomSmallDoorBlockAndCreateEntity("chinese_red_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/chinese_red_door.png"));
    // 中式木门 - 深木拼板配黄铜拉手
    Block CHINESE_WOOD_DOOR = registerCustomSmallDoorBlockAndCreateEntity("chinese_wood_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/chinese_wood_door.png"));
    // 日式障子门 - 浅木框白纸屏推拉门
    Block JAPANESE_SHOJI_DOOR = registerCustomSmallDoorBlockAndCreateEntity("japanese_shoji_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/japanese_shoji_door.png"));
    // 日式格栅门 - 深木格子推拉门
    Block JAPANESE_LATTICE_DOOR = registerCustomSmallDoorBlockAndCreateEntity("japanese_lattice_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/japanese_lattice_door.png"));

    // 现代/工业风小门
    // 地铁站台屏蔽门 - 铝合金框配玻璃幕墙
    Block SUBWAY_PLATFORM_DOOR = registerCustomSmallDoorBlockAndCreateEntity("subway_platform_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.GLASS),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/subway_platform_door.png"));
    // 自动玻璃门 - 两侧金属立柱中间玻璃推拉
    Block AUTOMATIC_GLASS_DOOR = registerCustomSmallDoorBlockAndCreateEntity("automatic_glass_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.GLASS),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/automatic_glass_door.png"));
    // 电梯门 - 灰色金属对开带楼层指示灯
    Block ELEVATOR_DOOR = registerCustomSmallDoorBlockAndCreateEntity("elevator_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/elevator_door.png"));
    // 防爆舱门 - 圆形舱盖配泄压阀
    Block BLAST_DOOR = registerCustomSmallDoorBlockAndCreateEntity("blast_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/blast_door.png"));
    // 防火门 - 红色烤漆钢门带观察窗
    Block FIREPROOF_DOOR = registerCustomSmallDoorBlockAndCreateEntity("fireproof_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/fireproof_door.png"));
    // 工业铆钉门 - 深灰钢板密集铆钉
    Block RIVETED_DOOR = registerCustomSmallDoorBlockAndCreateEntity("riveted_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/riveted_door.png"));
    // 恐怖/末日风小门
    // 锈蚀铁门 - 锈迹斑驳带划痕
    Block RUSTED_DOOR = registerCustomSmallDoorBlockAndCreateEntity("rusted_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/rusted_door.png"));
    // 腐烂木门 - 开裂霉变的旧木门
    Block ROTTED_WOOD_DOOR = registerCustomSmallDoorBlockAndCreateEntity("rotted_wood_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/rotted_wood_door.png"));
    // 铁链封锁门 - 铁链缠绕挂锁
    Block CHAINED_DOOR = registerCustomSmallDoorBlockAndCreateEntity("chained_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/chained_door.png"));
    // 血迹斑斑门 - 血手印泼溅血迹
    Block BLOODY_DOOR = registerCustomSmallDoorBlockAndCreateEntity("bloody_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/bloody_door.png"));
    // 地下牢门 - 暗色粗铁栏配锁
    Block DUNGEON_BAR_DOOR = registerCustomSmallDoorBlockAndCreateEntity("dungeon_bar_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/dungeon_bar_door.png"));
    // 奇幻/中世纪风小门
    // 哥特尖拱门 - 深橡木铁箍门环
    Block GOTHIC_DOOR = registerCustomSmallDoorBlockAndCreateEntity("gothic_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/gothic_door.png"));
    // 城堡橡木门 - 厚木条铁皮加固
    Block CASTLE_OAK_DOOR = registerCustomSmallDoorBlockAndCreateEntity("castle_oak_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/castle_oak_door.png"));
    // 地牢铁门 - 粗铁筋网格
    Block DUNGEON_IRON_DOOR = registerCustomSmallDoorBlockAndCreateEntity("dungeon_iron_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/dungeon_iron_door.png"));
    // 魔法符文门 - 暗紫底发光符文
    Block RUNE_DOOR = registerCustomSmallDoorBlockAndCreateEntity("rune_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.STONE),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/rune_door.png"));
    // 末地之门 - 紫黑裂纹
    Block END_DOOR = registerCustomSmallDoorBlockAndCreateEntity("end_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.STONE),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/end_door.png"));
    // 下界之门 - 绯红岩浆裂纹
    Block NETHER_DOOR = registerCustomSmallDoorBlockAndCreateEntity("nether_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.STONE),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/nether_door.png"));
    // 复古/蒸汽朋克风小门
    // 蒸汽朋克铜门 - 黄铜机身齿轮压力表
    Block STEAMPUNK_DOOR = registerCustomSmallDoorBlockAndCreateEntity("steampunk_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/steampunk_door.png"));
    // 维多利亚木门 - 深木雕花玻璃窗
    Block VICTORIAN_DOOR = registerCustomSmallDoorBlockAndCreateEntity("victorian_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/victorian_door.png"));
    // 其它东方风格小门
    // 韩式木门 - 浅色横条木格
    Block KOREAN_DOOR = registerCustomSmallDoorBlockAndCreateEntity("korean_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/korean_door.png"));
    // 泰式金门 - 金色雕花镶嵌
    Block THAI_DOOR = registerCustomSmallDoorBlockAndCreateEntity("thai_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.WOOD),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/thai_door.png"));
    // 竹编门 - 竹条编织纹理
    Block BAMBOO_DOOR = registerCustomSmallDoorBlockAndCreateEntity("bamboo_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.BAMBOO),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/bamboo_door.png"));
    // 视觉特效类小门
    // 霓虹灯门 - 黑色底彩色霓虹
    Block NEON_DOOR = registerCustomSmallDoorBlockAndCreateEntity("neon_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.METAL),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/neon_door.png"));
    // 全息投影门 - 青蓝网格发光
    Block HOLOGRAPHIC_DOOR = registerCustomSmallDoorBlockAndCreateEntity("holographic_door",
            BlockBehaviour.Properties.ofFullCopy(TMMBlocks.SMALL_GLASS_DOOR).sound(SoundType.GLASS),
            new Item.Properties().rarity(Rarity.COMMON),
            SRE.id("textures/item/doors/holographic_door.png"));

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

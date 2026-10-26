package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/**
 * 回声托运站的**地形类**方块：草丛与横板。
 *
 * <p>与 {@link EchoMachines} 的 12 种机器分开注册，因为两者契约不同：
 * <ul>
 * <li>机器是 3×2×3 / 2×2×2 的多格结构，用 {@code PART} 关联，属于「点位外壳」；</li>
 * <li>草丛与横板是**单格**、无方块实体、无 PART 的地形件，属于「地图装饰与临时工事」。</li>
 * </ul>
 * 分开后 {@code EchoMachineMappingTest} 里「12 种机器映射」的断言不需要放宽，
 * 也不会让「点位必须对应机器」的规则被地形件污染。
 */
public final class EchoTerrainBlocks {
    public static final EchoHideBlock HIDE = Registry.register(BuiltInRegistries.BLOCK,
            id("echo_hide_bush"),
            new EchoHideBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(0.2F)
                    .sound(SoundType.GRASS)
                    // 无碰撞 + 不挡光：玩家能直接走进去，路线准入把它当空气。
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .pushReaction(PushReaction.DESTROY)));

    public static final EchoPalletBlock PALLET = Registry.register(BuiltInRegistries.BLOCK,
            id("echo_pallet"),
            new EchoPalletBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.2F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    private static final List<Block> ALL = List.of(HIDE, PALLET);

    private EchoTerrainBlocks() { }

    /** 只触发类静态初始化；重复调用不会重复注册。 */
    public static void register() { }

    public static List<Block> all() { return ALL; }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("noellesroles", path);
    }

    private static void withItem(Block block) {
        BlockItem item = new BlockItem(block, new Item.Properties());
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK.getKey(block), item);
    }

    static {
        // 方块物品：与机器同一套注册方式，保证创造栏与 /give 都能拿到。
        // 显式逐个注册，避免在静态初始化块里依赖 ALL 的初始化顺序。
        withItem(HIDE);
        withItem(PALLET);
    }
}

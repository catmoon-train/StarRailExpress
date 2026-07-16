package net.exmo.sre.sixtyseconds.logic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 末日60秒的合成资源分级（两端共享的静态定义）——大量物品配方都基于这两级资源：
 * <ul>
 * <li><b>初级资源</b>（{@link #primary}）：搜刮/事件/拆解直接产出的原料，不可再分解——
 *     废料/破布/塑料/玻璃碎片/化学品/火药包 + 原版 铁锭/木板/木炭。</li>
 * <li><b>高级资源</b>（{@link #advanced}）：由初级资源在合成台加工出的中间件，供高阶配方使用——
 *     电线/胶带/布卷/齿轮/电子元件/电池/钢锭/钉子/活性炭滤芯。也会少量出现在 loot 里。</li>
 * </ul>
 * 用途：{@link SixtySecondsDismantle} 的拆解产物只落在这两级上（展开到资源即停）；
 * 合成 GUI 的「资源」分类（{@link SixtySecondsRecipes.Category#MATERIALS}）按此判定。
 */
public final class SixtySecondsResources {

    private static Set<Item> primary;
    private static Set<Item> advanced;

    private SixtySecondsResources() {
    }

    /** 初级资源（懒构建，保证 ModItems 已注册；LinkedHashSet 保序，两端一致）。 */
    public static synchronized Set<Item> primary() {
        if (primary == null) {
            primary = new LinkedHashSet<>(java.util.List.of(
                    ModItems.SIXTY_SECONDS_SCRAP, ModItems.SIXTY_SECONDS_RAG,
                    ModItems.SIXTY_SECONDS_PLASTIC, ModItems.SIXTY_SECONDS_GLASS_SHARD,
                    ModItems.SIXTY_SECONDS_CHEMICALS, ModItems.SIXTY_SECONDS_GUNPOWDER_PACK,
                    Items.IRON_INGOT, Items.OAK_PLANKS, Items.CHARCOAL,
                    // 野外专属原料（科技树重构批次）
                    ModItems.SIXTY_SECONDS_SCRAP_METAL, ModItems.SIXTY_SECONDS_PRECIOUS_PARTS,
                    ModItems.SIXTY_SECONDS_BREWING_PARTS));
        }
        return primary;
    }

    /** 高级资源（由初级加工出的中间件）。 */
    public static synchronized Set<Item> advanced() {
        if (advanced == null) {
            advanced = new LinkedHashSet<>(java.util.List.of(
                    ModItems.SIXTY_SECONDS_WIRE, ModItems.SIXTY_SECONDS_DUCT_TAPE,
                    ModItems.SIXTY_SECONDS_CLOTH_ROLL, ModItems.SIXTY_SECONDS_GEAR,
                    ModItems.SIXTY_SECONDS_ELECTRONICS, ModItems.SIXTY_SECONDS_BATTERY,
                    ModItems.SIXTY_SECONDS_STEEL_INGOT, ModItems.SIXTY_SECONDS_NAILS,
                    ModItems.SIXTY_SECONDS_CHARCOAL_FILTER,
                    // 冶金链中间件（科技树重构批次）
                    ModItems.SIXTY_SECONDS_COPPER_SCRAP, ModItems.SIXTY_SECONDS_GLASS_PLATE,
                    ModItems.SIXTY_SECONDS_PRECIOUS_METAL, ModItems.SIXTY_SECONDS_ALLOY_PLATE,
                    ModItems.SIXTY_SECONDS_HEMP, ModItems.SIXTY_SECONDS_BATTERY_LARGE));
        }
        return advanced;
    }

    /** 是否属于资源（初级或高级）。 */
    public static boolean isResource(Item item) {
        return primary().contains(item) || advanced().contains(item);
    }
}

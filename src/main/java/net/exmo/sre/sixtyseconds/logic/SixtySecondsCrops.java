package net.exmo.sre.sixtyseconds.logic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 培育箱作物注册表（两端共享静态定义）：种子 → 产物/数量/允许的培育箱/科技门控。
 * <ul>
 *   <li>普通培育箱（BASIC）：常规作物；高级培育箱（ADVANCED）：常规作物 2 阶段速生 +
 *       专属作物（工业麻/火把花/瓶子草）；菌丝箱（MUSHROOM）：只种蘑菇。</li>
 *   <li>种植门控用科技 id（{@link SixtySecondsTechTree}）：农业-I 小麦、农业-II 常规作物与野米/野茶、
 *       农业-III 工业麻/火把花/瓶子草、烟草科技种烟草。</li>
 * </ul>
 * 队伍收获加成（野谷/野茶/工业麻/烟草种子概率）见 PlanterBlock 收获结算。
 */
public final class SixtySecondsCrops {

    /** 培育箱等级。 */
    public enum Tier {
        BASIC, ADVANCED, MUSHROOM, GARDENER, ARID, HYDROPONIC
    }

    /**
     * 作物定义：{@code techId} 为 null 表示无门控；{@code advancedOnly} 的作物只能种高级培育箱；
     * {@code mushroom} 的作物只能种菌丝箱；{@code fullStagesInAdvanced} 在高级培育箱也要走满 3 阶段
     * （工业麻/火把花/瓶子草）；{@code growthTimeMultiplier} 默认为 1.0，紫颂花等慢生长作物使用 2.0。
     */
    public record Crop(String id, Item seed, Item product, int minCount, int maxCount,
            String techId, boolean advancedOnly, boolean mushroom, boolean fullStagesInAdvanced,
            boolean gardenerOnly, float growthTimeMultiplier) {
        public Crop(String id, Item seed, Item product, int minCount, int maxCount,
                String techId, boolean advancedOnly, boolean mushroom, boolean fullStagesInAdvanced,
                boolean gardenerOnly) {
            this(id, seed, product, minCount, maxCount, techId, advancedOnly, mushroom,
                    fullStagesInAdvanced, gardenerOnly, 1.0F);
        }
    }

    private static List<Crop> crops;

    private SixtySecondsCrops() {
    }

    /** 懒构建（保证 ModItems 已完成注册）。 */
    public static synchronized List<Crop> all() {
        if (crops == null) {
            crops = build();
        }
        return crops;
    }

    /** 按手持种子物品找作物；找不到返回 null。 */
    public static Crop bySeed(Item seed) {
        for (Crop crop : all()) {
            if (crop.seed() == seed) {
                return crop;
            }
        }
        return null;
    }

    public static Crop byId(String id) {
        for (Crop crop : all()) {
            if (crop.id().equals(id)) {
                return crop;
            }
        }
        return null;
    }

    /** 该作物能否种进指定等级的培育箱。 */
    public static boolean allowedIn(Crop crop, Tier tier) {
        if (crop.mushroom()) {
            return tier == Tier.MUSHROOM;
        }
        if (crop.advancedOnly()) {
            return tier == Tier.ADVANCED;
        }
        if (crop.gardenerOnly()) {
            return tier == Tier.GARDENER;
        }
        // ARID 培育箱仅种旱地作物（目前只有仙人掌）
        if (tier == Tier.ARID) {
            return "cactus".equals(crop.id());
        }
        if ("cactus".equals(crop.id())) {
            return tier == Tier.ARID;
        }
        // 水培箱：甘蔗、海带、药用蕨、荷叶
        if (tier == Tier.HYDROPONIC) {
            return "sugar_cane".equals(crop.id()) || "kelp".equals(crop.id())
                    || "medicinal_fern".equals(crop.id()) || "lily_pad".equals(crop.id());
        }
        if ("sugar_cane".equals(crop.id()) || "kelp".equals(crop.id())
                || "medicinal_fern".equals(crop.id()) || "lily_pad".equals(crop.id())) {
            return tier == Tier.HYDROPONIC;
        }
        return tier == Tier.BASIC || tier == Tier.ADVANCED;
    }

    private static List<Crop> build() {
        List<Crop> list = new ArrayList<>();
        // 基础：新鲜蔬菜（无门控，沿用旧培育箱闭环）
        list.add(new Crop("vegetables", ModItems.SIXTY_SECONDS_SEEDS_PACK,
                ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2, 2, null, false, false, false, false));
        // 农业-I：小麦
        list.add(new Crop("wheat", Items.WHEAT_SEEDS, Items.WHEAT, 2, 2, "agri_1", false, false, false, false));
        // 农业-II：野米/野茶 + 常规作物
        list.add(new Crop("wild_rice", ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS,
                ModItems.SIXTY_SECONDS_WILD_RICE, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("wild_tea", ModItems.SIXTY_SECONDS_WILD_TEA_SEED,
                ModItems.SIXTY_SECONDS_WILD_TEA_LEAF, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("potato", Items.POTATO, Items.POTATO, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("carrot", Items.CARROT, Items.CARROT, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("beetroot", Items.BEETROOT_SEEDS, Items.BEETROOT, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("melon", Items.MELON_SEEDS, Items.MELON, 2, 2, "agri_2", false, false, false, false));
        list.add(new Crop("pumpkin", Items.PUMPKIN_SEEDS, Items.PUMPKIN, 2, 2, "agri_2", false, false, false, false));
        // 农业-III：工业麻（仅高级培育箱）+ 火把花/瓶子草（仅高级培育箱，满 3 阶段，收 1 个）
        list.add(new Crop("hemp", ModItems.SIXTY_SECONDS_HEMP_SEEDS,
                ModItems.SIXTY_SECONDS_HEMP, 2, 2, "agri_3", true, false, true, false));
        list.add(new Crop("torchflower", Items.TORCHFLOWER_SEEDS, Items.TORCHFLOWER,
                1, 1, "agri_3", true, false, true, false));
        list.add(new Crop("pitcher", Items.PITCHER_POD, Items.PITCHER_PLANT,
                1, 1, "agri_3", true, false, true, false));
        // 菌丝箱：红/棕蘑菇（种 1 收 2，标准速度）；诡异菌/绯红菌（2倍生长）；下界疣（1.5倍生长）
        list.add(new Crop("red_mushroom", Items.RED_MUSHROOM, Items.RED_MUSHROOM,
                2, 2, null, false, true, false, false));
        list.add(new Crop("brown_mushroom", Items.BROWN_MUSHROOM, Items.BROWN_MUSHROOM,
                2, 2, null, false, true, false, false));
        list.add(new Crop("warped_fungus", Items.WARPED_FUNGUS, Items.WARPED_FUNGUS,
                2, 2, null, false, true, false, false, 2.0F));
        list.add(new Crop("crimson_fungus", Items.CRIMSON_FUNGUS, Items.CRIMSON_FUNGUS,
                2, 2, null, false, true, false, false, 2.0F));
        list.add(new Crop("nether_wart", Items.NETHER_WART, Items.NETHER_WART,
                2, 2, null, false, true, false, false, 1.5F));
        // 烟草科技：烟草
        list.add(new Crop("tobacco", ModItems.SIXTY_SECONDS_TOBACCO_SEEDS,
                ModItems.SIXTY_SECONDS_TOBACCO, 2, 2, "tobacco", false, false, false, false));
        // 园丁培育箱：竹子/浆果/发光浆果（种1收2）
        list.add(new Crop("bamboo", Items.BAMBOO, Items.BAMBOO,
                2, 2, "misc_planter_2", false, false, false, true));
        list.add(new Crop("sweet_berries", Items.SWEET_BERRIES, Items.SWEET_BERRIES,
                2, 2, "misc_planter_2", false, false, false, true));
        list.add(new Crop("glow_berries", ModItems.SIXTY_SECONDS_SHIMMER_BERRY, ModItems.SIXTY_SECONDS_SHIMMER_BERRY,
                2, 2, "misc_planter_2", false, false, false, true));
        // 发光浆果也可种出微光浆果（互通）
        list.add(new Crop("glow_berries_alt", Items.GLOW_BERRIES, ModItems.SIXTY_SECONDS_SHIMMER_BERRY,
                2, 2, "misc_planter_2", false, false, false, true));
        // 园丁培育箱：可可豆（种1收2）
        list.add(new Crop("cocoa_beans", Items.COCOA_BEANS, Items.COCOA_BEANS,
                2, 2, "misc_planter_2", false, false, false, true));
        // 园丁培育箱：紫颂花（种1收1紫颂花+1紫颂果，生长2倍时间）
        list.add(new Crop("chorus_flower", Items.CHORUS_FLOWER, Items.CHORUS_FLOWER,
                1, 1, "misc_planter_2", false, false, false, true, 2.0F));
        // 旱地培育箱：仙人掌（种一收二）
        list.add(new Crop("cactus", Items.CACTUS, Items.CACTUS,
                2, 2, "misc_planter_2", false, false, false, false));
        // 水培箱：甘蔗、海带、药用蕨（种一收二）
        list.add(new Crop("sugar_cane", Items.SUGAR_CANE, Items.SUGAR_CANE,
                2, 2, "misc_planter_3", false, false, false, false));
        list.add(new Crop("kelp", Items.KELP, Items.KELP,
                2, 2, "misc_planter_3", false, false, false, false));
        list.add(new Crop("medicinal_fern", ModItems.SIXTY_SECONDS_MEDICINAL_FERN,
                ModItems.SIXTY_SECONDS_MEDICINAL_FERN,
                2, 2, "misc_planter_3", false, false, false, false));
        list.add(new Crop("lily_pad", Items.LILY_PAD, Items.LILY_PAD,
                2, 2, "misc_planter_3", false, false, false, false));
        return List.copyOf(list);
    }
}

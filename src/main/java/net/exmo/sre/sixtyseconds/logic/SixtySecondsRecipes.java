package net.exmo.sre.sixtyseconds.logic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成台绑定配方表（两端共享的静态定义）：物品必须拿到<b>对应家具</b>才能合成——
 * 书桌/工作台=简易工作台、厨房灶台=熔炉/烟熏炉/营火、浴缸=炼药锅、
 * 裁缝台=织布机（护甲/装备）、军械台=锻造台/砂轮/铁砧（武器/弹药）。
 * 配方受科技树（{@link SixtySecondsTechTree}）门控，部分需要供电（{@link SixtySecondsPowerSystem}）。
 */
public final class SixtySecondsRecipes {

    /** 合成站类型（由方块判定，见 {@link #stationOf}）。新值只能追加在末尾（网络包按 ordinal 传输）。 */
    public enum Station {
        WORKBENCH, STOVE, BATHTUB, TAILOR, ARSENAL;

        public String translationKey() {
            return "station.noellesroles.sixty_seconds." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * 配方产物的展示分类（合成 GUI 顶部标签栏）。由 {@link #categoryOf} 按产物物品类型自动判定，
     * 少数歧义配方用 {@link #CATEGORY_OVERRIDES} 指定。
     */
    public enum Category {
        TOOLS(0xFFC9A84C), BUILD(0xFF72C17B), WEAPONS(0xFFE06B65), ARMOR(0xFF5EB7D8),
        MEDICAL(0xFFB18AE6), FOOD(0xFFE0AD5B), COMFORT(0xFFFF66AA), MATERIALS(0xFF9AAAB8);

        public final int color;

        Category(int color) {
            this.color = color;
        }

        public String translationKey() {
            return "category.noellesroles.sixty_seconds." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Ingredient(Item item, int count) {
    }

    public record Recipe(String id, Station station, String techId, boolean needsPower,
            List<Ingredient> inputs, Item output, int outputCount) {
    }

    private static List<Recipe> recipes;

    private SixtySecondsRecipes() {
    }

    /** 懒构建（保证 ModItems 已完成注册）。 */
    public static synchronized List<Recipe> all() {
        if (recipes == null) {
            recipes = build();
        }
        return recipes;
    }

    public static Recipe byId(String id) {
        for (Recipe recipe : all()) {
            if (recipe.id().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<Recipe> forStation(Station station) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : all()) {
            if (recipe.station() == station) {
                result.add(recipe);
            }
        }
        return result;
    }

    /** 方块 → 合成站类型；非合成站返回 null。 */
    public static Station stationOf(BlockState state) {
        // 60s 专用合成台方块（可制作、可携带、白色混凝土标记上放置）优先识别
        if (state.getBlock() instanceof net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock station) {
            return station.station();
        }
        if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.LECTERN) || state.is(Blocks.FLETCHING_TABLE)) {
            return Station.WORKBENCH; // 书桌/工作台
        }
        if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.BLAST_FURNACE)) {
            return Station.STOVE; // 厨房灶台
        }
        if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)) {
            return Station.BATHTUB; // 浴缸
        }
        if (state.is(Blocks.LOOM)) {
            return Station.TAILOR; // 织布机 → 裁缝台（护甲/装备）
        }
        if (state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.GRINDSTONE) || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) {
            return Station.ARSENAL; // 锻造台/砂轮/铁砧 → 军械台（武器弹药）
        }
        return null;
    }

    // ── 展示分类 ─────────────────────────────────────────────────

    /** 按产物类型难以判定的少数配方：配方 id → 分类。 */
    private static final java.util.Map<String, Category> CATEGORY_OVERRIDES = java.util.Map.of(
            "riot_shield", Category.ARMOR,   // 普通 Item，但属于护具
            "fuel_can", Category.TOOLS,      // 化学科技产物，但是燃料材料
            "wrench", Category.TOOLS,        // 防御科技产物，但是拆装工具
            "charcoal_pill", Category.MEDICAL); // 净化科技产物，但是清污药品

    /** 各科技的默认分类（产物类型判定不出时的兜底）。 */
    private static Category categoryOfTech(String techId) {
        return switch (techId) {
            case "defense", "fortification", "rainwater" -> Category.BUILD;
            case "weapons", "gunsmith" -> Category.WEAPONS;
            case "armor" -> Category.ARMOR;
            case "medicine", "chemistry" -> Category.MEDICAL;
            case "kitchen", "agriculture", "gourmet", "brewing", "filtration" -> Category.FOOD;
            case "comfort" -> Category.COMFORT;
            default -> Category.TOOLS; // basic_tools/survival/locksmith/power/engineering/metallurgy
        };
    }

    /** 配方 → 展示分类：先看覆盖表，再判资源（初级/高级），再按产物物品类型，最后按科技兜底。 */
    public static Category categoryOf(Recipe recipe) {
        Category override = CATEGORY_OVERRIDES.get(recipe.id());
        if (override != null) {
            return override;
        }
        Item out = recipe.output();
        if (SixtySecondsResources.isResource(out)) {
            return Category.MATERIALS; // 资源加工配方（电线/齿轮/电子元件/钢锭…）统一进「资源」页
        }
        if (out instanceof net.minecraft.world.item.BlockItem) {
            return Category.BUILD;
        }
        if (out instanceof net.minecraft.world.item.ArmorItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem) {
            return Category.ARMOR;
        }
        if (out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsRpgItem) {
            return Category.WEAPONS;
        }
        if (out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsMedicineItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBandageItem) {
            return Category.MEDICAL;
        }
        if (out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem) {
            return Category.FOOD;
        }
        if (out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem) {
            return Category.COMFORT;
        }
        return categoryOfTech(recipe.techId());
    }

    public static ItemStack outputStack(Recipe recipe) {
        return new ItemStack(recipe.output(), recipe.outputCount());
    }

    private static List<Recipe> build() {
        List<Recipe> list = new ArrayList<>();
        // ── 简易工作台（basic_tools）──────────────────────────────────
        list.add(new Recipe("torch_pack", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 3), in(Items.OAK_PLANKS, 2)),
                ModItems.SIXTY_SECONDS_TORCH, 2));
        list.add(new Recipe("clock", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 3), in(ModItems.SIXTY_SECONDS_GEAR, 1),
                        in(Items.IRON_INGOT, 1)),
                ModItems.SIXTY_SECONDS_CLOCK, 1));
        // ── 背包（basic_tools；材料独立，避免消耗装了东西的低级背包）─────
        list.add(new Recipe("backpack_small", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 3), in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 2)),
                ModItems.SIXTY_SECONDS_BACKPACK_SMALL, 1));
        list.add(new Recipe("backpack_medium", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 6), in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 3)),
                ModItems.SIXTY_SECONDS_BACKPACK_MEDIUM, 1));
        list.add(new Recipe("backpack_large", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 9), in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 5),
                        in(Items.IRON_INGOT, 3)),
                ModItems.SIXTY_SECONDS_BACKPACK_LARGE, 1));
        // ── 防御工事（defense）────────────────────────────────────────
        list.add(new Recipe("barricade", Station.WORKBENCH, "defense", false,
                List.of(in(Items.OAK_PLANKS, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BARRICADE.asItem(), 1));
        list.add(new Recipe("heavy_barricade", Station.WORKBENCH, "defense", false,
                List.of(in(Items.OAK_PLANKS, 5), in(Items.IRON_INGOT, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_HEAVY_BARRICADE.asItem(), 1));
        list.add(new Recipe("spike_trap", Station.WORKBENCH, "defense", false,
                List.of(in(Items.IRON_INGOT, 3), in(Items.OAK_PLANKS, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SPIKE_TRAP.asItem(), 1));
        // 门陷阱：火药+引线（装家门上炸撬门贼，见 SixtySecondsDoorMenu）
        list.add(new Recipe("door_trap", Station.WORKBENCH, "defense", false,
                List.of(in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 2), in(ModItems.SIXTY_SECONDS_WIRE, 2),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                ModItems.SIXTY_SECONDS_DOOR_TRAP, 1));
        list.add(new Recipe("wrench", Station.WORKBENCH, "defense", false,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                ModItems.SIXTY_SECONDS_WRENCH, 1));
        // ── 电力（power）─────────────────────────────────────────────
        list.add(new Recipe("generator", Station.WORKBENCH, "power", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 10), in(Items.IRON_INGOT, 3),
                        in(ModItems.SIXTY_SECONDS_GEAR, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_GENERATOR.asItem(), 1));
        list.add(new Recipe("lamp", Station.WORKBENCH, "power", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 4), in(Items.IRON_INGOT, 1),
                        in(ModItems.SIXTY_SECONDS_WIRE, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_LAMP.asItem(), 1));
        list.add(new Recipe("battery", Station.WORKBENCH, "power", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 8), in(Items.IRON_INGOT, 2)),
                ModItems.SIXTY_SECONDS_BATTERY, 1));
        list.add(new Recipe("flashlight", Station.WORKBENCH, "power", false,
                List.of(in(ModItems.SIXTY_SECONDS_BATTERY, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 4),
                        in(ModItems.SIXTY_SECONDS_WIRE, 1)),
                ModItems.SIXTY_SECONDS_FLASHLIGHT, 1));
        // ── 武器（weapons）───────────────────────────────────────────
        list.add(new Recipe("pipe_club", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 3)),
                ModItems.SIXTY_SECONDS_PIPE, 1));
        list.add(new Recipe("spiked_bat", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.OAK_PLANKS, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_SPIKED_BAT, 1));
        list.add(new Recipe("machete", Station.WORKBENCH, "weapons", true,
                List.of(in(Items.IRON_INGOT, 5), in(Items.OAK_PLANKS, 2)),
                ModItems.SIXTY_SECONDS_MACHETE, 1));
        // ── 护甲（armor）─────────────────────────────────────────────
        list.add(new Recipe("scrap_helmet", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 9)),
                ModItems.SIXTY_SECONDS_SCRAP_HELMET, 1));
        list.add(new Recipe("scrap_chestplate", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 14)),
                ModItems.SIXTY_SECONDS_SCRAP_CHESTPLATE, 1));
        list.add(new Recipe("iron_helmet", Station.WORKBENCH, "armor", true,
                List.of(in(Items.IRON_INGOT, 6), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_IRON_HELMET, 1));
        list.add(new Recipe("iron_chestplate", Station.WORKBENCH, "armor", true,
                List.of(in(Items.IRON_INGOT, 9), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_IRON_CHESTPLATE, 1));
        // ── 开锁技术（locksmith）──────────────────────────────────────
        list.add(new Recipe("crowbar2", Station.WORKBENCH, "locksmith", false,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_CROWBAR_STEEL, 1));
        list.add(new Recipe("lockpick2", Station.WORKBENCH, "locksmith", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_LOCKPICK_PRO, 1));
        list.add(new Recipe("crowbar3", Station.WORKBENCH, "locksmith", true,
                List.of(in(Items.IRON_INGOT, 6), in(ModItems.SIXTY_SECONDS_SCRAP, 9)),
                ModItems.SIXTY_SECONDS_CROWBAR_HYDRAULIC, 1));
        list.add(new Recipe("lockpick3", Station.WORKBENCH, "locksmith", true,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 9)),
                ModItems.SIXTY_SECONDS_LOCKPICK_MASTER, 1));
        // 门锁：齿轮锁芯（装家门上提升撬锁难度，见 SixtySecondsDoorMenu）
        list.add(new Recipe("door_lock", Station.WORKBENCH, "locksmith", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_GEAR, 1)),
                ModItems.SIXTY_SECONDS_DOOR_LOCK, 1));
        // ── 厨房灶台（kitchen）：破布+酒精 → 消毒绷带 / 简易熟食 ─────────
        list.add(new Recipe("bandage", Station.STOVE, "kitchen", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 2), in(ModItems.SIXTY_SECONDS_ALCOHOL, 2)),
                ModItems.SIXTY_SECONDS_BANDAGE, 1));
        list.add(new Recipe("cooked_meal", Station.STOVE, "kitchen", false,
                List.of(in(Items.ROTTEN_FLESH, 3)),
                Items.COOKED_BEEF, 1));
        // 简易淋浴器：每人每天一次，消耗小瓶水，污染 -50%（见 SixtySecondsShowerBlock）
        list.add(new Recipe("shower", Station.WORKBENCH, "brewing", false,
                List.of(in(ModItems.SIXTY_SECONDS_PLASTIC, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SHOWER.asItem(), 1));
        // ── 浴缸（brewing）：净化污染水 / 蒸馏酒精 ────────────────────────
        list.add(new Recipe("purify_small", Station.BATHTUB, "brewing", false,
                List.of(in(ModItems.SIXTY_SECONDS_DIRTY_WATER, 2)),
                ModItems.SIXTY_SECONDS_WATER_SMALL, 1));
        list.add(new Recipe("purify_medium", Station.BATHTUB, "brewing", true,
                List.of(in(ModItems.SIXTY_SECONDS_DIRTY_WATER, 3)),
                ModItems.SIXTY_SECONDS_WATER_MEDIUM, 1));
        list.add(new Recipe("alcohol", Station.BATHTUB, "brewing", false,
                List.of(in(Items.POTATO, 3)),
                ModItems.SIXTY_SECONDS_ALCOHOL, 1));

        // ── 基础工具补充：绳子/布卷 ───────────────────────────────────
        list.add(new Recipe("rope", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 3), in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 2)),
                ModItems.SIXTY_SECONDS_ROPE, 1));
        // 雨伞：布卷撑骨架（污雨天户外持伞免额外污染，见 SixtySecondsEventSystem）
        list.add(new Recipe("umbrella", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_CLOTH_ROLL, 1), in(Items.IRON_INGOT, 2)),
                ModItems.SIXTY_SECONDS_UMBRELLA, 1));
        list.add(new Recipe("cloth_roll", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 5)),
                ModItems.SIXTY_SECONDS_CLOTH_ROLL, 1));
        // ── 资源加工（高级资源=初级资源加工出的中间件，见 SixtySecondsResources；
        //    电池/钢锭/钉子/滤芯已有配方，此处补齐 电线/胶带/齿轮/电子元件/火药包 的合成闭环）──
        list.add(new Recipe("wire", Station.WORKBENCH, "basic_tools", false,
                List.of(in(Items.IRON_INGOT, 1), in(ModItems.SIXTY_SECONDS_SCRAP, 2)),
                ModItems.SIXTY_SECONDS_WIRE, 2));
        list.add(new Recipe("duct_tape", Station.WORKBENCH, "basic_tools", false,
                List.of(in(ModItems.SIXTY_SECONDS_PLASTIC, 1), in(ModItems.SIXTY_SECONDS_RAG, 2)),
                ModItems.SIXTY_SECONDS_DUCT_TAPE, 2));
        list.add(new Recipe("gear", Station.WORKBENCH, "power", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                ModItems.SIXTY_SECONDS_GEAR, 2));
        list.add(new Recipe("electronics", Station.WORKBENCH, "power", false,
                List.of(in(ModItems.SIXTY_SECONDS_WIRE, 2), in(ModItems.SIXTY_SECONDS_PLASTIC, 1),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                ModItems.SIXTY_SECONDS_ELECTRONICS, 1));
        list.add(new Recipe("gunpowder_pack", Station.WORKBENCH, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 2), in(Items.CHARCOAL, 1)),
                ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 1));
        // ── 野外生存（survival）─────────────────────────────────────
        list.add(new Recipe("compass", Station.WORKBENCH, "survival", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_WIRE, 2)),
                ModItems.SIXTY_SECONDS_COMPASS, 1));
        list.add(new Recipe("radio", Station.WORKBENCH, "survival", false,
                List.of(in(ModItems.SIXTY_SECONDS_ELECTRONICS, 2), in(ModItems.SIXTY_SECONDS_WIRE, 3),
                        in(ModItems.SIXTY_SECONDS_BATTERY, 2)),
                ModItems.SIXTY_SECONDS_RADIO, 1));
        list.add(new Recipe("alarm_item", Station.WORKBENCH, "survival", false,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_WIRE, 2),
                        in(ModItems.SIXTY_SECONDS_BATTERY, 2)),
                ModItems.SIXTY_SECONDS_ALARM, 1));
        list.add(new Recipe("lure_item", Station.WORKBENCH, "survival", false,
                List.of(in(ModItems.SIXTY_SECONDS_JERKY, 2), in(ModItems.SIXTY_SECONDS_CHEMICALS, 2)),
                ModItems.SIXTY_SECONDS_LURE, 1));
        list.add(new Recipe("toolbox_item", Station.WORKBENCH, "survival", false,
                List.of(in(Items.IRON_INGOT, 3), in(Items.OAK_PLANKS, 3),
                        in(ModItems.SIXTY_SECONDS_GEAR, 1)),
                ModItems.SIXTY_SECONDS_TOOLBOX, 1));
        // 钩锁：绳子+齿轮卷扬机构（荡索位移，见 SixtySecondsGrapplingHookItem）
        list.add(new Recipe("grappling_hook", Station.WORKBENCH, "survival", false,
                List.of(in(ModItems.SIXTY_SECONDS_ROPE, 1), in(Items.IRON_INGOT, 3),
                        in(ModItems.SIXTY_SECONDS_GEAR, 1)),
                ModItems.SIXTY_SECONDS_GRAPPLING_HOOK, 1));
        // ── 医疗（medicine，灶台）──────────────────────────────────
        list.add(new Recipe("sedative", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 2), in(ModItems.SIXTY_SECONDS_ALCOHOL, 2)),
                ModItems.SIXTY_SECONDS_SEDATIVE, 1));
        list.add(new Recipe("antibiotics", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 3), in(ModItems.SIXTY_SECONDS_RAG, 2)),
                ModItems.SIXTY_SECONDS_ANTIBIOTICS, 1));
        list.add(new Recipe("painkillers", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 2)),
                ModItems.SIXTY_SECONDS_PAINKILLERS, 1));
        list.add(new Recipe("medkit", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_BANDAGE, 3), in(ModItems.SIXTY_SECONDS_ANTIBIOTICS, 2),
                        in(ModItems.SIXTY_SECONDS_RAG, 3)),
                ModItems.SIXTY_SECONDS_MEDKIT, 1));
        list.add(new Recipe("adrenaline", Station.STOVE, "medicine", true,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 3), in(ModItems.SIXTY_SECONDS_ALCOHOL, 2)),
                ModItems.SIXTY_SECONDS_ADRENALINE, 1));
        // ── 化学（chemistry）────────────────────────────────────────
        list.add(new Recipe("molotov", Station.WORKBENCH, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_ALCOHOL, 2), in(ModItems.SIXTY_SECONDS_RAG, 2),
                        in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 2)),
                ModItems.SIXTY_SECONDS_MOLOTOV, 1));
        list.add(new Recipe("pipe_bomb", Station.WORKBENCH, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 8)),
                ModItems.SIXTY_SECONDS_PIPE_BOMB, 1));
        list.add(new Recipe("flashbang", Station.WORKBENCH, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 2), in(ModItems.SIXTY_SECONDS_CHEMICALS, 2),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_FLASHBANG, 1));
        list.add(new Recipe("purification_tablet", Station.BATHTUB, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 3)),
                ModItems.SIXTY_SECONDS_PURIFICATION_TABLET, 2));
        list.add(new Recipe("fuel_can", Station.BATHTUB, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_ALCOHOL, 3), in(ModItems.SIXTY_SECONDS_PLASTIC, 2)),
                ModItems.SIXTY_SECONDS_FUEL_CAN, 1));
        // ── 工程学（engineering）────────────────────────────────────
        list.add(new Recipe("chainsaw", Station.WORKBENCH, "engineering", true,
                List.of(in(Items.IRON_INGOT, 6), in(ModItems.SIXTY_SECONDS_GEAR, 3),
                        in(ModItems.SIXTY_SECONDS_BATTERY, 2)),
                ModItems.SIXTY_SECONDS_CHAINSAW, 1));
        list.add(new Recipe("stun_baton", Station.WORKBENCH, "engineering", true,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_WIRE, 3),
                        in(ModItems.SIXTY_SECONDS_BATTERY, 2)),
                ModItems.SIXTY_SECONDS_STUN_BATON, 1));
        list.add(new Recipe("solar_panel", Station.WORKBENCH, "engineering", true,
                List.of(in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 5), in(ModItems.SIXTY_SECONDS_ELECTRONICS, 3),
                        in(Items.IRON_INGOT, 3)),
                ModItems.SIXTY_SECONDS_SOLAR_PANEL, 1));
        list.add(new Recipe("night_goggles", Station.WORKBENCH, "engineering", true,
                List.of(in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 3), in(ModItems.SIXTY_SECONDS_ELECTRONICS, 2),
                        in(ModItems.SIXTY_SECONDS_BATTERY, 2)),
                ModItems.SIXTY_SECONDS_NIGHT_GOGGLES, 1));
        // ── 高级枪械（weapons + 供电）────────────────────────────────
        list.add(new Recipe("sniper", Station.WORKBENCH, "weapons", true,
                List.of(in(Items.IRON_INGOT, 15), in(ModItems.SIXTY_SECONDS_WIRE, 8),
                        in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 8), in(ModItems.SIXTY_SECONDS_SCRAP, 23)),
                ModItems.SIXTY_SECONDS_SNIPER, 1));
        list.add(new Recipe("rpg", Station.WORKBENCH, "weapons", true,
                List.of(in(ModItems.SIXTY_SECONDS_PIPE, 4), in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 4),
                        in(ModItems.SIXTY_SECONDS_ELECTRONICS, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 12)),
                ModItems.SIXTY_SECONDS_RPG, 1));
        // 隐藏通关 · 救援信标：户外激活呼叫救援，撑过倒计时即胜（SixtySecondsRescue）
        list.add(new Recipe("rescue_beacon", Station.WORKBENCH, "engineering", true,
                List.of(in(ModItems.SIXTY_SECONDS_BATTERY, 3), in(ModItems.SIXTY_SECONDS_ELECTRONICS, 3),
                        in(ModItems.SIXTY_SECONDS_WIRE, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 14)),
                ModItems.SIXTY_SECONDS_RESCUE_BEACON, 1));
        // ── 武器/护甲/防御补充 ───────────────────────────────────────
        list.add(new Recipe("knife", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 3)),
                ModItems.SIXTY_SECONDS_KNIFE, 1));
        list.add(new Recipe("sledgehammer", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 6), in(Items.OAK_PLANKS, 3)),
                ModItems.SIXTY_SECONDS_SLEDGEHAMMER, 1));
        list.add(new Recipe("fire_axe", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 5), in(Items.OAK_PLANKS, 3)),
                ModItems.SIXTY_SECONDS_FIRE_AXE, 1));
        list.add(new Recipe("scrap_leggings", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 12)),
                ModItems.SIXTY_SECONDS_SCRAP_LEGGINGS, 1));
        list.add(new Recipe("scrap_boots", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP, 9)),
                ModItems.SIXTY_SECONDS_SCRAP_BOOTS, 1));
        list.add(new Recipe("iron_leggings", Station.WORKBENCH, "armor", true,
                List.of(in(Items.IRON_INGOT, 8), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_IRON_LEGGINGS, 1));
        list.add(new Recipe("iron_boots", Station.WORKBENCH, "armor", true,
                List.of(in(Items.IRON_INGOT, 6), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_IRON_BOOTS, 1));
        list.add(new Recipe("gas_mask", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 3), in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 2),
                        in(ModItems.SIXTY_SECONDS_CHEMICALS, 2)),
                ModItems.SIXTY_SECONDS_GAS_MASK, 1));
        list.add(new Recipe("hazmat_suit", Station.WORKBENCH, "armor", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 6), in(ModItems.SIXTY_SECONDS_PLASTIC, 3),
                        in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 3)),
                ModItems.SIXTY_SECONDS_HAZMAT_SUIT, 1));
        list.add(new Recipe("riot_shield", Station.WORKBENCH, "armor", true,
                List.of(in(Items.IRON_INGOT, 8), in(Items.OAK_PLANKS, 3)),
                ModItems.SIXTY_SECONDS_RIOT_SHIELD, 1));
        list.add(new Recipe("repair_kit", Station.WORKBENCH, "defense", false,
                List.of(in(Items.OAK_PLANKS, 6), in(Items.IRON_INGOT, 3),
                        in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 2)),
                ModItems.SIXTY_SECONDS_REPAIR_KIT, 1));

        // ══ 扩充批次：冶金 / 农业 / 工事强化 ══════════════════════════════════
        // ── 冶金（metallurgy）：钢锭冶炼（灶台）→ 钢制武器/护甲（工作台）─────
        list.add(new Recipe("steel_ingot", Station.STOVE, "metallurgy", true,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                ModItems.SIXTY_SECONDS_STEEL_INGOT, 1));
        list.add(new Recipe("steel_sword", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 3), in(Items.OAK_PLANKS, 2)),
                ModItems.SIXTY_SECONDS_STEEL_SWORD, 1));
        list.add(new Recipe("steel_spear", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 2), in(Items.OAK_PLANKS, 3)),
                ModItems.SIXTY_SECONDS_STEEL_SPEAR, 1));
        list.add(new Recipe("steel_helmet", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 5)),
                ModItems.SIXTY_SECONDS_STEEL_HELMET, 1));
        list.add(new Recipe("steel_chestplate", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 8)),
                ModItems.SIXTY_SECONDS_STEEL_CHESTPLATE, 1));
        list.add(new Recipe("steel_leggings", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 6)),
                ModItems.SIXTY_SECONDS_STEEL_LEGGINGS, 1));
        list.add(new Recipe("steel_boots", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 5)),
                ModItems.SIXTY_SECONDS_STEEL_BOOTS, 1));
        list.add(new Recipe("ballistic_vest", Station.WORKBENCH, "metallurgy", false,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_INGOT, 3), in(ModItems.SIXTY_SECONDS_PLASTIC, 3),
                        in(ModItems.SIXTY_SECONDS_RAG, 3)),
                ModItems.SIXTY_SECONDS_BALLISTIC_VEST, 1));
        // ── 武器补充（weapons）：手斧 / 剁刀 ─────────────────────────────
        list.add(new Recipe("hatchet", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 2), in(Items.OAK_PLANKS, 2)),
                ModItems.SIXTY_SECONDS_HATCHET, 1));
        list.add(new Recipe("cleaver", Station.WORKBENCH, "weapons", false,
                List.of(in(Items.IRON_INGOT, 3)),
                ModItems.SIXTY_SECONDS_CLEAVER, 1));
        // ── 农业（agriculture）：肥料/蔬菜/加工食品（灶台）──────────────────
        list.add(new Recipe("fertilizer", Station.STOVE, "agriculture", false,
                List.of(in(Items.ROTTEN_FLESH, 3)),
                ModItems.SIXTY_SECONDS_FERTILIZER, 2));
        list.add(new Recipe("dried_fruit", Station.STOVE, "agriculture", false,
                List.of(in(Items.APPLE, 3)),
                ModItems.SIXTY_SECONDS_DRIED_FRUIT, 2));
        list.add(new Recipe("fresh_vegetables", Station.STOVE, "agriculture", false,
                List.of(in(ModItems.SIXTY_SECONDS_SEEDS_PACK, 2), in(ModItems.SIXTY_SECONDS_FERTILIZER, 2)),
                ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2));
        // 培育箱：种子→蔬菜的耕地方块（见 SixtySecondsPlanterBlock）
        list.add(new Recipe("planter", Station.WORKBENCH, "agriculture", false,
                List.of(in(Items.OAK_PLANKS, 4), in(ModItems.SIXTY_SECONDS_SCRAP, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PLANTER.asItem(), 1));
        // 留种：蔬菜取种，形成可持续种植闭环
        list.add(new Recipe("seeds_pack", Station.WORKBENCH, "agriculture", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 1)),
                ModItems.SIXTY_SECONDS_SEEDS_PACK, 2));
        list.add(new Recipe("trail_mix", Station.WORKBENCH, "agriculture", false,
                List.of(in(ModItems.SIXTY_SECONDS_DRIED_FRUIT, 2), in(ModItems.SIXTY_SECONDS_SEEDS_PACK, 2)),
                ModItems.SIXTY_SECONDS_TRAIL_MIX, 2));
        list.add(new Recipe("canned_soup", Station.STOVE, "agriculture", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2), in(ModItems.SIXTY_SECONDS_WATER_SMALL, 2)),
                ModItems.SIXTY_SECONDS_CANNED_SOUP, 1));
        // ── 医疗/净化补充：输血袋(灶台医疗)/草药茶(灶台医疗)/活性炭滤芯/运动饮料/抗污血清(浴缸)──
        list.add(new Recipe("blood_bag", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 3), in(ModItems.SIXTY_SECONDS_CHEMICALS, 2)),
                ModItems.SIXTY_SECONDS_BLOOD_BAG, 1));
        list.add(new Recipe("herbal_tea", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2), in(ModItems.SIXTY_SECONDS_WATER_SMALL, 2)),
                ModItems.SIXTY_SECONDS_HERBAL_TEA, 1));
        list.add(new Recipe("charcoal_filter", Station.BATHTUB, "brewing", false,
                List.of(in(Items.CHARCOAL, 3), in(ModItems.SIXTY_SECONDS_RAG, 2)),
                ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1));
        list.add(new Recipe("sports_drink", Station.BATHTUB, "brewing", false,
                List.of(in(ModItems.SIXTY_SECONDS_WATER_SMALL, 2), in(ModItems.SIXTY_SECONDS_CHEMICALS, 2)),
                ModItems.SIXTY_SECONDS_SPORTS_DRINK, 1));
        list.add(new Recipe("anti_pollution_serum", Station.BATHTUB, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_CHEMICALS, 3), in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 2)),
                ModItems.SIXTY_SECONDS_ANTI_POLLUTION_SERUM, 1));
        // ── 消除污染的低成本档位（活性炭片 / 纯净水 / 排毒草茶）──────────────
        // 活性炭片：木炭直接压片，最便宜的清污手段（污染 -20）
        list.add(new Recipe("charcoal_pill", Station.BATHTUB, "brewing", false,
                List.of(in(Items.CHARCOAL, 2)),
                ModItems.SIXTY_SECONDS_CHARCOAL_PILL, 2));
        // 纯净水：污水过炭芯，比 purify_small 多一步但顺带清污（解渴 +35 / 污染 -20）
        list.add(new Recipe("purified_water", Station.BATHTUB, "brewing", false,
                List.of(in(ModItems.SIXTY_SECONDS_DIRTY_WATER, 2), in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1)),
                ModItems.SIXTY_SECONDS_PURIFIED_WATER, 2));
        // 排毒草茶：灶台煮草药，清污兼安神（污染 -30 / 理智 +8）
        list.add(new Recipe("detox_tea", Station.STOVE, "medicine", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2),
                        in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1)),
                ModItems.SIXTY_SECONDS_DETOX_TEA, 2));
        // ── 化学补充：燃烧弹 ─────────────────────────────────────────────
        list.add(new Recipe("incendiary_grenade", Station.WORKBENCH, "chemistry", false,
                List.of(in(ModItems.SIXTY_SECONDS_ALCOHOL, 2), in(ModItems.SIXTY_SECONDS_FUEL_CAN, 2),
                        in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 2)),
                ModItems.SIXTY_SECONDS_INCENDIARY_GRENADE, 1));
        // ── 工事强化（fortification）：钉子(灶台)/强化路障/探照灯(工作台)──────
        list.add(new Recipe("nails", Station.STOVE, "fortification", false,
                List.of(in(Items.IRON_INGOT, 2)),
                ModItems.SIXTY_SECONDS_NAILS, 4));
        list.add(new Recipe("reinforced_barricade", Station.WORKBENCH, "fortification", false,
                List.of(in(Items.IRON_INGOT, 3), in(Items.OAK_PLANKS, 5), in(ModItems.SIXTY_SECONDS_NAILS, 6)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_REINFORCED_BARRICADE.asItem(), 1));
        // 铁丝网：廉价版尖刺陷阱（减速为主、伤害低，电线+钉子）
        list.add(new Recipe("barbed_wire", Station.WORKBENCH, "fortification", false,
                List.of(in(ModItems.SIXTY_SECONDS_WIRE, 4), in(ModItems.SIXTY_SECONDS_NAILS, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BARBED_WIRE.asItem(), 2));
        list.add(new Recipe("floodlight", Station.WORKBENCH, "fortification", true,
                List.of(in(ModItems.SIXTY_SECONDS_BATTERY, 2), in(ModItems.SIXTY_SECONDS_GLASS_SHARD, 3),
                        in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_WIRE, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_FLOODLIGHT.asItem(), 1));

        // ══ 扩充批次二：心理慰藉 / 炊事进阶 / 高级净化 / 军械工坊 ══════════════════
        // ── 心理慰藉（comfort）：娱乐物品（右键给周围玩家回理智，见 SixtySecondsEntertainmentItem）──
        list.add(new Recipe("poker", Station.WORKBENCH, "comfort", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 2), in(ModItems.SIXTY_SECONDS_PLASTIC, 1)),
                ModItems.SIXTY_SECONDS_POKER, 1));
        list.add(new Recipe("chess", Station.WORKBENCH, "comfort", false,
                List.of(in(Items.OAK_PLANKS, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 1)),
                ModItems.SIXTY_SECONDS_CHESS, 1));
        list.add(new Recipe("harmonica", Station.WORKBENCH, "comfort", false,
                List.of(in(Items.IRON_INGOT, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 2)),
                ModItems.SIXTY_SECONDS_HARMONICA, 1));
        list.add(new Recipe("guitar", Station.WORKBENCH, "comfort", false,
                List.of(in(Items.OAK_PLANKS, 4), in(ModItems.SIXTY_SECONDS_WIRE, 3)),
                ModItems.SIXTY_SECONDS_GUITAR, 1));
        list.add(new Recipe("teddy_bear", Station.WORKBENCH, "comfort", false,
                List.of(in(ModItems.SIXTY_SECONDS_RAG, 5), in(ModItems.SIXTY_SECONDS_CLOTH_ROLL, 1)),
                ModItems.SIXTY_SECONDS_TEDDY_BEAR, 1));
        // ── 炊事进阶（gourmet，灶台）：保温壶 / 末日乱炖 / 军用口粮 ─────────────
        list.add(new Recipe("thermos", Station.WORKBENCH, "gourmet", false,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_PLASTIC, 2)),
                ModItems.SIXTY_SECONDS_THERMOS, 1));
        list.add(new Recipe("stew", Station.STOVE, "gourmet", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2), in(Items.COOKED_BEEF, 1),
                        in(ModItems.SIXTY_SECONDS_WATER_SMALL, 1)),
                ModItems.SIXTY_SECONDS_STEW, 2));
        list.add(new Recipe("mre", Station.STOVE, "gourmet", false,
                List.of(in(ModItems.SIXTY_SECONDS_CANNED_SOUP, 1), in(ModItems.SIXTY_SECONDS_JERKY, 1),
                        in(ModItems.SIXTY_SECONDS_CHOCOLATE_BAR, 1)),
                ModItems.SIXTY_SECONDS_MRE, 2));
        // ── 高级净化（filtration，浴缸，需供电）：大瓶水 / 批量净化 ─────────────
        list.add(new Recipe("purify_large", Station.BATHTUB, "filtration", true,
                List.of(in(ModItems.SIXTY_SECONDS_DIRTY_WATER, 5), in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1)),
                ModItems.SIXTY_SECONDS_WATER_HIGH, 1));
        list.add(new Recipe("purify_batch", Station.BATHTUB, "filtration", true,
                List.of(in(ModItems.SIXTY_SECONDS_DIRTY_WATER, 4)),
                ModItems.SIXTY_SECONDS_WATER_SMALL, 3));
        // ── 雨水收集（rainwater）：三档集水器（被动产污染水，配合净化链）────────
        list.add(new Recipe("rain_barrel", Station.WORKBENCH, "rainwater", false,
                List.of(in(Items.OAK_PLANKS, 4), in(ModItems.SIXTY_SECONDS_RAG, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RAIN_BARREL.asItem(), 1));
        list.add(new Recipe("rain_collector", Station.WORKBENCH, "rainwater", false,
                List.of(in(Items.OAK_PLANKS, 3), in(ModItems.SIXTY_SECONDS_PLASTIC, 4),
                        in(ModItems.SIXTY_SECONDS_DUCT_TAPE, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RAIN_COLLECTOR.asItem(), 1));
        list.add(new Recipe("condenser", Station.WORKBENCH, "rainwater", true,
                List.of(in(Items.IRON_INGOT, 4), in(ModItems.SIXTY_SECONDS_PLASTIC, 3),
                        in(ModItems.SIXTY_SECONDS_ELECTRONICS, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_CONDENSER.asItem(), 1));
        // ── 军械工坊（gunsmith）：自产弹药与基础枪械（高阶枪械仍走 weapons）─────
        list.add(new Recipe("ammo_pack", Station.WORKBENCH, "gunsmith", false,
                List.of(in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 2), in(ModItems.SIXTY_SECONDS_SCRAP, 4)),
                ModItems.SIXTY_SECONDS_AMMO, 6));
        list.add(new Recipe("pistol_craft", Station.WORKBENCH, "gunsmith", false,
                List.of(in(Items.IRON_INGOT, 4), in(ModItems.SIXTY_SECONDS_SCRAP, 6),
                        in(ModItems.SIXTY_SECONDS_WIRE, 2)),
                ModItems.SIXTY_SECONDS_PISTOL, 1));
        list.add(new Recipe("shotgun_craft", Station.WORKBENCH, "gunsmith", true,
                List.of(in(Items.IRON_INGOT, 8), in(ModItems.SIXTY_SECONDS_PIPE, 2),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 8)),
                ModItems.SIXTY_SECONDS_HUNTING_SHOTGUN, 1));
        list.add(new Recipe("frag_grenade", Station.WORKBENCH, "gunsmith", false,
                List.of(in(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 3), in(ModItems.SIXTY_SECONDS_SCRAP, 6),
                        in(ModItems.SIXTY_SECONDS_NAILS, 4)),
                ModItems.SIXTY_SECONDS_FRAG_GRENADE, 1));

        // ── 专用合成台方块：在任一工作台站制作，可携带、按放置规则布进避难所——
        //    避难所模板里没有对应原版家具时也能自建三类合成站，闭环见 SixtySecondsStationBlock
        list.add(new Recipe("station_workbench", Station.WORKBENCH, "basic_tools", false,
                List.of(in(Items.OAK_PLANKS, 6), in(ModItems.SIXTY_SECONDS_SCRAP, 8)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_WORKBENCH.asItem(), 1));
        list.add(new Recipe("station_stove", Station.WORKBENCH, "kitchen", false,
                List.of(in(Items.IRON_INGOT, 5), in(ModItems.SIXTY_SECONDS_SCRAP, 8),
                        in(Items.CHARCOAL, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_STOVE.asItem(), 1));
        list.add(new Recipe("station_purifier", Station.WORKBENCH, "brewing", false,
                List.of(in(Items.IRON_INGOT, 3), in(ModItems.SIXTY_SECONDS_PLASTIC, 3),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 5)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PURIFIER.asItem(), 1));
        // 裁缝台（护甲/背包等装备类配方在此制作；原版对应家具：织布机）
        list.add(new Recipe("station_tailor", Station.WORKBENCH, "basic_tools", false,
                List.of(in(Items.OAK_PLANKS, 4), in(ModItems.SIXTY_SECONDS_RAG, 4),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 4)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_TAILOR_TABLE.asItem(), 1));
        // 军械台（近战武器/枪械/弹药/投掷物在此制作；原版对应家具：锻造台/砂轮/铁砧）
        list.add(new Recipe("station_arsenal", Station.WORKBENCH, "defense", false,
                List.of(in(Items.IRON_INGOT, 5), in(Items.OAK_PLANKS, 4),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 6)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ARSENAL_TABLE.asItem(), 1));
        // 拆解台（右键把装备/功能方块按 -60% 拆回基础资源；见 SixtySecondsDismantle）
        list.add(new Recipe("station_dismantler", Station.WORKBENCH, "basic_tools", false,
                List.of(in(Items.OAK_PLANKS, 4), in(Items.IRON_INGOT, 2),
                        in(ModItems.SIXTY_SECONDS_SCRAP, 6)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DISMANTLER.asItem(), 1));

        // ── 站点重分配：护甲/装备 → 裁缝台，武器/弹药 → 军械台 ─────────────
        //    只迁移原本在工作台的配方（灶台冶炼等保持原站）；分类即 GUI 标签，二者保持一致。
        for (int i = 0; i < list.size(); i++) {
            Recipe recipe = list.get(i);
            if (recipe.station() != Station.WORKBENCH) {
                continue;
            }
            Category category = categoryOf(recipe);
            Station moved = switch (category) {
                case ARMOR -> Station.TAILOR;
                case WEAPONS -> Station.ARSENAL;
                default -> null;
            };
            if (moved != null) {
                list.set(i, new Recipe(recipe.id(), moved, recipe.techId(), recipe.needsPower(),
                        recipe.inputs(), recipe.output(), recipe.outputCount()));
            }
        }
        return list;
    }

    private static Ingredient in(Item item, int count) {
        return new Ingredient(item, count);
    }
}

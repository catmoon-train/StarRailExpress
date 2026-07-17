package net.exmo.sre.sixtyseconds.logic;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 合成台绑定配方表（两端共享的静态定义，科技树重构版）：物品必须拿到<b>对应合成台</b>才能合成。
 * 站表：简易工作台/厨房灶台/净化台(浴缸)/裁缝台/军械台 + 无菌台/高级工作台/冶金炉/酿造台/
 * 盔甲锻造台/武器锻造台/车床/液体融锅/祭坛（见 {@link Station}；原版家具映射见 {@link #stationOf}）。
 * 配方受科技树（{@link SixtySecondsTechTree}）门控，部分需要供电（{@link SixtySecondsPowerSystem}）；
 * <b>冶金炉每件 4 秒制作时间</b>（见 {@link SixtySecondsStations}）。
 * <p>
 * 配料支持「任意 X」组（{@link Ingredient#items} 多选一，展示名用
 * {@code group.noellesroles.sixty_seconds.<groupKey>}）；产物支持 {@link Recipe#outputFactory}
 * 覆写（酿造台产原版药水、第三方物品）。
 */
public final class SixtySecondsRecipes {

    /** 合成站类型（由方块判定，见 {@link #stationOf}）。新值只能追加在末尾（网络包按 ordinal 传输）。 */
    public enum Station {
        WORKBENCH, STOVE, BATHTUB, TAILOR, ARSENAL,
        // ── 科技树重构批次追加（勿插队/重排）────────────────────────────
        STERILE,       // 无菌台（医疗）
        ADV_WORKBENCH, // 高级工作台（防御工事/抄家技术）
        SMELTER,       // 冶金炉（冶炼，全通电 + 每件 4 秒制作时间）
        BREWING,       // 酿造台（药水，全通电）
        ARMOR_FORGE,   // 盔甲锻造台
        WEAPON_FORGE,  // 武器锻造台
        LATHE,         // 车床（基地设施/交通，全通电）
        MELTING_POT,   // 液体融锅（燃油）
        ALTAR;         // 祭坛（神秘技术，须放白色混凝土上）

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

    /**
     * 配料：{@code items} 多选一（「任意水果」等组配料放全部候选，普通配料单元素）；
     * {@code groupKey} 非空时展示名用组翻译键，否则用首个物品名。
     */
    public record Ingredient(List<Item> items, int count, String groupKey) {

        /** 展示/退料用的代表物品（组配料取第一个候选）。 */
        public Item item() {
            return items.get(0);
        }

        /** 该物品堆是否可充当本配料。 */
        public boolean matches(ItemStack stack) {
            for (Item candidate : items) {
                if (stack.is(candidate)) {
                    return true;
                }
            }
            return false;
        }

        /** 展示名：组配料用 group 键（如「任意水果」），普通配料用物品名。 */
        public Component displayName() {
            if (groupKey != null) {
                return Component.translatable("group.noellesroles.sixty_seconds." + groupKey);
            }
            return item().getDescription();
        }
    }

    /**
     * 配方：{@code outputFactory} 非空时产物用其结果（酿造药水/第三方物品），
     * 否则 {@code new ItemStack(output, outputCount)}。{@code output} 仍用于分类判定与图标。
     */
    public record Recipe(String id, Station station, String techId, boolean needsPower,
            List<Ingredient> inputs, Item output, int outputCount, Supplier<ItemStack> outputFactory) {
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
            return Station.BATHTUB; // 浴缸 → 净化台
        }
        if (state.is(Blocks.LOOM)) {
            return Station.TAILOR; // 织布机 → 裁缝台（护甲/背包）
        }
        if (state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.GRINDSTONE) || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) {
            return Station.ARSENAL; // 锻造台/砂轮/铁砧 → 军械台（弹药/枪械/投掷物）
        }
        return null;
    }

    // ── 展示分类 ─────────────────────────────────────────────────

    /** 按产物类型难以判定的少数配方：配方 id → 分类。 */
    private static final java.util.Map<String, Category> CATEGORY_OVERRIDES = java.util.Map.of(
            "riot_shield", Category.ARMOR,      // 普通 Item，但属于护具
            "turtle_helmet", Category.ARMOR,
            "horse_armor_relic", Category.ARMOR,
            "fuel_can", Category.TOOLS,
            "diesel_can", Category.TOOLS,
            "wrench", Category.TOOLS,
            "detach_wrench", Category.TOOLS,
            "charcoal_pill", Category.MEDICAL);

    /** 各科技的默认分类（产物类型判定不出时的兜底），按科技树重构版大类映射。 */
    private static Category categoryOfTech(String techId) {
        if (techId.startsWith("work_env") || techId.startsWith("materials")
                || techId.startsWith("tools") || techId.startsWith("power")
                || techId.startsWith("lockpick_craft") || techId.startsWith("smelt")
                || techId.startsWith("fuel") || techId.startsWith("horse")
                || techId.startsWith("vehicle") || techId.startsWith("sacrifice")
                || techId.startsWith("undying") || techId.startsWith("revival")) {
            return Category.TOOLS;
        }
        if (techId.startsWith("backpack")) {
            return Category.ARMOR;
        }
        if (techId.startsWith("agri") || techId.startsWith("planter") || techId.startsWith("fertilizer")
                || techId.startsWith("tobacco") || techId.startsWith("cooking") || techId.startsWith("waste")
                || techId.startsWith("water_") || techId.equals("tea") || techId.equals("drinks")) {
            return Category.FOOD;
        }
        if (techId.startsWith("door") || techId.startsWith("vault") || techId.startsWith("mob_defense")
                || techId.startsWith("base_")) {
            return Category.BUILD;
        }
        if (techId.startsWith("med_") || techId.startsWith("drugs") || techId.startsWith("tonics")
                || techId.startsWith("sanity") || techId.startsWith("decontam") || techId.equals("omni_tonic")
                || techId.startsWith("brew") || techId.equals("potion_purify")) {
            return Category.MEDICAL;
        }
        if (techId.startsWith("armor") || techId.startsWith("func_armor")) {
            return Category.ARMOR;
        }
        if (techId.startsWith("melee") || techId.startsWith("bullets") || techId.startsWith("firearms")
                || techId.startsWith("throwables") || techId.startsWith("archery")
                || techId.startsWith("arrow")) {
            return Category.WEAPONS;
        }
        return Category.TOOLS;
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
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsRpgItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem
                || out instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem) {
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
        if (recipe.outputFactory() != null) {
            return recipe.outputFactory().get();
        }
        return new ItemStack(recipe.output(), recipe.outputCount());
    }

    // ── 「任意 X」配料组 ──────────────────────────────────────────

    private static List<Item> fruits() {
        return List.of(Items.APPLE, Items.MELON_SLICE, Items.MELON, Items.SWEET_BERRIES, Items.GLOW_BERRIES);
    }

    private static List<Item> vegetables() {
        return List.of(Items.POTATO, Items.CARROT, Items.BEETROOT, Items.PUMPKIN, ModItems.SIXTY_SECONDS_FRESH_VEGETABLES);
    }

    private static List<Item> mushrooms() {
        return List.of(Items.RED_MUSHROOM, Items.BROWN_MUSHROOM);
    }

    private static List<Item> rawMeat() {
        return List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT,
                Items.COD, Items.SALMON);
    }

    private static List<Item> fish() {
        return List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH,
                Items.COOKED_COD, Items.COOKED_SALMON);
    }

    private static List<Item> monsterDrops() {
        return List.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.BONE);
    }

    private static List<Item> seedPacks() {
        return List.of(ModItems.SIXTY_SECONDS_SEEDS_PACK, ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS,
                ModItems.SIXTY_SECONDS_HEMP_SEEDS);
    }

    // ── 药水产物（原版药水物品 + 自定义效果时长）────────────────────────

    private static Supplier<ItemStack> potion(Item base, Holder<MobEffect> effect, int duration, int amplifier) {
        return () -> {
            ItemStack stack = new ItemStack(base);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(),
                    List.of(new MobEffectInstance(effect, duration, amplifier))));
            String langKey = effect.value().getDescriptionId();
            String formatKey = "message.noellesroles.sixty_seconds.potion_format";
            if (base == Items.SPLASH_POTION) {
                formatKey = "message.noellesroles.sixty_seconds.potion_splash_format";
            } else if (base == Items.LINGERING_POTION) {
                formatKey = "message.noellesroles.sixty_seconds.potion_linger_format";
            }
            stack.set(DataComponents.ITEM_NAME,
                    Component.translatable(formatKey,
                            Component.translatable(langKey)));
            return stack;
        };
    }

    /** 第三方物品产物（watheextraitems 香烟/雪茄等）：运行时按 id 查，未装该 mod 时返回 null（跳过配方）。 */
    private static Item external(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return item == Items.AIR ? null : item;
    }

    // ── 配方表 ───────────────────────────────────────────────────

    private static List<Recipe> build() {
        List<Recipe> list = new ArrayList<>();
        Item oak = Items.OAK_PLANKS;
        Item iron = Items.IRON_INGOT;
        Item charcoal = Items.CHARCOAL;
        Item scrap = ModItems.SIXTY_SECONDS_SCRAP;
        Item steel = ModItems.SIXTY_SECONDS_STEEL_INGOT;
        Item alloy = ModItems.SIXTY_SECONDS_ALLOY_PLATE;
        Item plastic = ModItems.SIXTY_SECONDS_PLASTIC;
        Item nails = ModItems.SIXTY_SECONDS_NAILS;
        Item gear = ModItems.SIXTY_SECONDS_GEAR;
        Item wire = ModItems.SIXTY_SECONDS_WIRE;
        Item elec = ModItems.SIXTY_SECONDS_ELECTRONICS;
        Item chem = ModItems.SIXTY_SECONDS_CHEMICALS;
        Item rag = ModItems.SIXTY_SECONDS_RAG;
        Item clothRoll = ModItems.SIXTY_SECONDS_CLOTH_ROLL;
        Item tape = ModItems.SIXTY_SECONDS_DUCT_TAPE;
        Item glassShard = ModItems.SIXTY_SECONDS_GLASS_SHARD;
        Item glassPlate = ModItems.SIXTY_SECONDS_GLASS_PLATE;
        Item copper = ModItems.SIXTY_SECONDS_COPPER_SCRAP;
        Item precious = ModItems.SIXTY_SECONDS_PRECIOUS_METAL;
        Item hemp = ModItems.SIXTY_SECONDS_HEMP;
        Item rice = ModItems.SIXTY_SECONDS_WILD_RICE;
        Item tea = ModItems.SIXTY_SECONDS_WILD_TEA_LEAF;
        Item waterS = ModItems.SIXTY_SECONDS_WATER_SMALL;
        Item waterM = ModItems.SIXTY_SECONDS_WATER_MEDIUM;
        Item dirty = ModItems.SIXTY_SECONDS_DIRTY_WATER;
        Item alcohol = ModItems.SIXTY_SECONDS_ALCOHOL;
        Item gunpowder = ModItems.SIXTY_SECONDS_GUNPOWDER_PACK;
        Item battery = ModItems.SIXTY_SECONDS_BATTERY;

        // ══ 生存与工具 ═══════════════════════════════════════════════
        // ── 更好的工作环境-I（简易工作台）────────────────────────────────
        add(list, "station_tailor", Station.WORKBENCH, "work_env_1", false,
                List.of(in(oak, 3), in(scrap, 4), in(rag, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_TAILOR_TABLE.asItem(), 1);
        add(list, "station_sterile", Station.WORKBENCH, "work_env_1", false,
                List.of(in(oak, 4), in(nails, 3), in(chem, 2), in(scrap, 4)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_STERILE_TABLE.asItem(), 1);
        add(list, "generator", Station.WORKBENCH, "work_env_1", false,
                List.of(in(scrap, 10), in(iron, 3), in(gear, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_GENERATOR.asItem(), 1);
        add(list, "station_workbench", Station.WORKBENCH, "work_env_1", false,
                List.of(in(oak, 6), in(scrap, 8)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_WORKBENCH.asItem(), 1);
        add(list, "station_stove", Station.WORKBENCH, "work_env_1", false,
                List.of(in(iron, 5), in(scrap, 8), in(charcoal, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_STOVE.asItem(), 1);
        add(list, "station_purifier", Station.WORKBENCH, "work_env_1", false,
                List.of(in(iron, 3), in(plastic, 3), in(scrap, 5)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PURIFIER.asItem(), 1);
        add(list, "shower", Station.WORKBENCH, "work_env_1", false,
                List.of(in(plastic, 2), in(scrap, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SHOWER.asItem(), 1);
        add(list, "station_research", Station.WORKBENCH, "work_env_1", false,
                List.of(in(oak, 4), in(scrap, 4), in(plastic, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RESEARCH_TABLE.asItem(), 1);
        // ── 更好的工作环境-II ──────────────────────────────────────────
        add(list, "station_adv_workbench", Station.WORKBENCH, "work_env_2", false,
                List.of(in(nails, 3), in(plastic, 2), in(gear, 1), in(wire, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ADV_WORKBENCH.asItem(), 1);
        add(list, "station_smelter", Station.WORKBENCH, "work_env_2", false,
                List.of(in(iron, 3), in(charcoal, 2), in(nails, 2), in(elec, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SMELTER.asItem(), 1);
        add(list, "station_brewery", Station.WORKBENCH, "work_env_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_BREWING_PARTS, 2), in(steel, 2), in(glassPlate, 1), in(tape, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BREWERY.asItem(), 1);
        // ── 更好的工作环境-III ─────────────────────────────────────────
        add(list, "station_armor_forge", Station.WORKBENCH, "work_env_3", false,
                List.of(in(nails, 4), in(iron, 4), in(plastic, 4)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ARMOR_FORGE.asItem(), 1);
        add(list, "station_weapon_forge", Station.WORKBENCH, "work_env_3", false,
                List.of(in(nails, 4), in(scrap, 16), in(glassShard, 4)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_WEAPON_FORGE.asItem(), 1);
        add(list, "station_arsenal", Station.WORKBENCH, "work_env_3", false,
                List.of(in(nails, 4), in(gear, 2), in(tape, 2), in(elec, 4)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ARSENAL_TABLE.asItem(), 1);
        // ── 更好的工作环境-IV ──────────────────────────────────────────
        add(list, "station_lathe", Station.WORKBENCH, "work_env_4", false,
                List.of(in(steel, 6), in(nails, 4), in(elec, 4), in(gear, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_LATHE.asItem(), 1);
        add(list, "station_melting_pot", Station.WORKBENCH, "work_env_4", false,
                List.of(in(steel, 2), in(nails, 2), in(gear, 2), in(scrap, 6)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_MELTING_POT.asItem(), 1);

        // ── 材料工艺-I ────────────────────────────────────────────────
        add(list, "duct_tape", Station.WORKBENCH, "materials_1", false,
                List.of(in(clothRoll, 1), in(chem, 1)), ModItems.SIXTY_SECONDS_DUCT_TAPE, 1);
        add(list, "sticks", Station.WORKBENCH, "materials_1", false,
                List.of(in(oak, 2)), Items.STICK, 4);
        add(list, "bowl", Station.WORKBENCH, "materials_1", false,
                List.of(in(oak, 1)), Items.BOWL, 1);
        add(list, "glass_bottle", Station.WORKBENCH, "materials_1", false,
                List.of(in(glassShard, 3)), Items.GLASS_BOTTLE, 1);
        // ── 材料工艺-II ───────────────────────────────────────────────
        add(list, "nails", Station.WORKBENCH, "materials_2", false,
                List.of(in(iron, 2)), ModItems.SIXTY_SECONDS_NAILS, 2);
        add(list, "gear_powered", Station.WORKBENCH, "materials_2", true,
                List.of(in(iron, 1), in(scrap, 3)), ModItems.SIXTY_SECONDS_GEAR, 1);
        add(list, "electronics", Station.WORKBENCH, "materials_2", true,
                List.of(in(wire, 2), in(plastic, 1), in(scrap, 3)), ModItems.SIXTY_SECONDS_ELECTRONICS, 1);
        add(list, "gear", Station.WORKBENCH, "materials_2", false,
                List.of(in(iron, 2), in(scrap, 3)), ModItems.SIXTY_SECONDS_GEAR, 1);
        add(list, "wire", Station.WORKBENCH, "materials_2", false,
                List.of(in(iron, 1), in(scrap, 2)), ModItems.SIXTY_SECONDS_WIRE, 1);
        // 纸：野米×2，不通电
        add(list, "paper_materials", Station.WORKBENCH, "materials_2", false,
                List.of(in(rice, 2)), Items.PAPER, 3);
        // 皮革：任意蘑菇×2，通电
        add(list, "leather_materials", Station.WORKBENCH, "materials_2", true,
                List.of(any("mushroom", 2, mushrooms())), Items.LEATHER, 1);
        // ── 材料工艺-III ──────────────────────────────────────────────
        add(list, "station_dismantler", Station.WORKBENCH, "materials_3", true,
                List.of(in(plastic, 3), in(wire, 6), in(scrap, 6)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DISMANTLER.asItem(), 1);
        add(list, "gunpowder_pack", Station.WORKBENCH, "materials_3", true,
                List.of(in(chem, 2), in(charcoal, 1)), ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 1);

        // ── 工具-I ───────────────────────────────────────────────────
        add(list, "umbrella", Station.WORKBENCH, "tools_1", false,
                List.of(in(clothRoll, 1), in(Items.STICK, 1)), ModItems.SIXTY_SECONDS_UMBRELLA, 1);
        add(list, "torch", Station.WORKBENCH, "tools_1", false,
                List.of(in(charcoal, 1), in(Items.STICK, 1)), ModItems.SIXTY_SECONDS_TORCH, 1);
        add(list, "clock", Station.WORKBENCH, "tools_1", false,
                List.of(in(plastic, 2), in(wire, 2)), ModItems.SIXTY_SECONDS_CLOCK, 1);
        add(list, "note", Station.WORKBENCH, "tools_1", false,
                List.of(in(Items.PAPER, 2), in(Items.LEATHER, 1)), Items.WRITABLE_BOOK, 1);
        add(list, "trainmurdermystery_note", Station.WORKBENCH, "tools_1", false,
                List.of(in(Items.PAPER, 2)),
                BuiltInRegistries.ITEM.get(ResourceLocation.tryBuild("trainmurdermystery", "note")), 1);
        add(list, "radio", Station.WORKBENCH, "tools_1", false,
                List.of(in(elec, 2), in(wire, 3), in(battery, 2)), ModItems.SIXTY_SECONDS_RADIO, 1);
        add(list, "compass", Station.WORKBENCH, "tools_1", false,
                List.of(in(iron, 2), in(wire, 2)), ModItems.SIXTY_SECONDS_COMPASS, 1);
        // ── 工具-II ──────────────────────────────────────────────────
        add(list, "harmonica", Station.WORKBENCH, "tools_2", false,
                List.of(in(elec, 1), in(iron, 1), in(tape, 1)), ModItems.SIXTY_SECONDS_HARMONICA, 1);
        add(list, "flashlight", Station.WORKBENCH, "tools_2", true,
                List.of(in(elec, 1), in(scrap, 2)), ModItems.SIXTY_SECONDS_FLASHLIGHT, 1);
        add(list, "rope", Station.WORKBENCH, "tools_2", false,
                List.of(in(tape, 1), in(Items.STRING, 2), in(clothRoll, 1)), ModItems.SIXTY_SECONDS_ROPE, 1);
        add(list, "magnet", Station.WORKBENCH, "tools_2", true,
                List.of(in(iron, 2), in(copper, 2)), ModItems.MAGNET, 1);
        add(list, "fishing_rod", Station.WORKBENCH, "tools_2", true,
                List.of(in(Items.STICK, 3), in(tape, 1), in(rag, 3)), Items.FISHING_ROD, 1);
        add(list, "box_pry", Station.WORKBENCH, "tools_2", false,
                List.of(in(Items.STICK, 3), in(iron, 2), in(scrap, 3)), ModItems.SIXTY_SECONDS_BOX_PRY, 1);
        // 电话：电子元件×2 + 电线×2 + 铁锭×3，通电
        add(list, "phone", Station.WORKBENCH, "tools_2", true,
                List.of(in(elec, 2), in(wire, 2), in(iron, 3)), ModItems.SIXTY_SECONDS_PHONE, 1);
        // 快递包裹：皮革×1 + 胶带×1，通电
        add(list, "express_package", Station.WORKBENCH, "tools_2", true,
                List.of(in(Items.LEATHER, 1), in(tape, 1)), ModItems.SIXTY_SECONDS_EXPRESS_PACKAGE, 1);
        // ── 工具-III ─────────────────────────────────────────────────
        add(list, "grappling_hook", Station.WORKBENCH, "tools_3", true,
                List.of(in(Items.STICK, 2), in(iron, 1), in(hemp, 2)),
                ModItems.SIXTY_SECONDS_GRAPPLING_HOOK, 1);
        add(list, "big_note", Station.WORKBENCH, "tools_3", true,
                List.of(in(Items.PAPER, 8)), ModItems.GIANT_NOTE, 1);
        add(list, "pliers", Station.WORKBENCH, "tools_3", true,
                List.of(in(Items.STICK, 2), in(steel, 2), in(hemp, 1)), ModItems.SIXTY_SECONDS_PLIERS, 1);
        add(list, "claw_hook", Station.WORKBENCH, "tools_3", true,
                List.of(in(iron, 3), in(gear, 1), in(hemp, 3)), ModItems.SIXTY_SECONDS_CLAW_HOOK, 1);
        // 稿纸：Paper*5 + 木炭*1，需通电
        add(list, "draft_paper", Station.WORKBENCH, "tools_3", true,
                List.of(in(Items.PAPER, 5), in(Items.CHARCOAL, 1)), ModItems.SIXTY_SECONDS_DRAFT_PAPER, 1);

        // ── 背包（裁缝台）─────────────────────────────────────────────
        add(list, "backpack_small", Station.TAILOR, "backpack_1", false,
                List.of(in(Items.LEATHER, 2), in(Items.STRING, 1)), ModItems.SIXTY_SECONDS_BACKPACK_SMALL, 1);
        add(list, "backpack_medium", Station.TAILOR, "backpack_2", false,
                List.of(in(Items.LEATHER, 3), in(Items.STRING, 2)), ModItems.SIXTY_SECONDS_BACKPACK_MEDIUM, 1);
        add(list, "backpack_large", Station.TAILOR, "backpack_3", true,
                List.of(in(plastic, 5), in(Items.LEATHER, 4), in(Items.STRING, 4)),
                ModItems.SIXTY_SECONDS_BACKPACK_LARGE, 1);
        add(list, "backpack_military", Station.TAILOR, "backpack_4", true,
                List.of(in(plastic, 5), in(Items.LEATHER, 4), in(hemp, 4)),
                ModItems.SIXTY_SECONDS_BACKPACK_MILITARY, 1);
        // 收纳袋（原版Bundle，裁缝台）：皮革×5 + 线×2，通电
        add(list, "bundle", Station.TAILOR, "backpack_4", true,
                List.of(in(Items.LEATHER, 5), in(Items.STRING, 2)), Items.BUNDLE, 1);
        add(list, "backpack_traveler", Station.TAILOR, "backpack_5", true,
                List.of(in(steel, 1), in(Items.LEATHER, 5), in(hemp, 6)),
                ModItems.SIXTY_SECONDS_BACKPACK_TRAVELER, 1);

        // ══ 农业 ═══════════════════════════════════════════════════
        add(list, "seeds_pack", Station.WORKBENCH, "agri_1", false,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 1)), ModItems.SIXTY_SECONDS_SEEDS_PACK, 2);
        add(list, "wheat_seeds", Station.WORKBENCH, "agri_1", false,
                List.of(in(Items.WHEAT, 1)), Items.WHEAT_SEEDS, 2);
        add(list, "wild_rice_seeds", Station.WORKBENCH, "agri_2", false,
                List.of(in(rice, 1)), ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS, 2);
        add(list, "beetroot_seeds", Station.WORKBENCH, "agri_2", false,
                List.of(in(Items.BEETROOT, 1)), Items.BEETROOT_SEEDS, 1);
        add(list, "melon_seeds", Station.WORKBENCH, "agri_2", false,
                List.of(in(Items.MELON, 1)), Items.MELON_SEEDS, 1);
        add(list, "pumpkin_seeds", Station.WORKBENCH, "agri_2", false,
                List.of(in(Items.PUMPKIN, 1)), Items.PUMPKIN_SEEDS, 1);
        add(list, "wild_tea_seed", Station.WORKBENCH, "agri_2", false,
                List.of(in(tea, 1)), ModItems.SIXTY_SECONDS_WILD_TEA_SEED, 1);
        add(list, "hemp_seeds", Station.WORKBENCH, "agri_3", false,
                List.of(in(hemp, 1)), ModItems.SIXTY_SECONDS_HEMP_SEEDS, 2);
        add(list, "torchflower_seeds", Station.WORKBENCH, "agri_3", false,
                List.of(in(ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS, 5), in(chem, 1)), Items.TORCHFLOWER_SEEDS, 1);
        add(list, "pitcher_pod", Station.WORKBENCH, "agri_3", false,
                List.of(in(ModItems.SIXTY_SECONDS_HEMP_SEEDS, 2), in(chem, 1)), Items.PITCHER_POD, 1);
        // ── 培育箱 ───────────────────────────────────────────────────
        add(list, "planter", Station.WORKBENCH, "planter_1", false,
                List.of(in(oak, 4), in(scrap, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PLANTER.asItem(), 1);
        add(list, "mushroom_box", Station.WORKBENCH, "misc_planter_1", false,
                List.of(in(oak, 2), any("mushroom", 1, mushrooms()), in(scrap, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_MUSHROOM_BOX.asItem(), 1);
        add(list, "advanced_planter", Station.WORKBENCH, "planter_2", false,
                List.of(in(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PLANTER.asItem(), 1),
                        in(glassPlate, 1), in(nails, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ADVANCED_PLANTER.asItem(), 1);
        // 园丁培育箱：橡木木板x4 + 铁锭x2 + 电子元件x1 + 废料x3
        add(list, "gardener_planter", Station.WORKBENCH, "misc_planter_2", true,
                List.of(in(oak, 4), in(iron, 2), in(elec, 1), in(scrap, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_GARDENER_PLANTER.asItem(), 1);
        // ── 肥料 ─────────────────────────────────────────────────────
        add(list, "fertilizer", Station.WORKBENCH, "fertilizer_1", false,
                List.of(in(Items.ROTTEN_FLESH, 1), in(Items.BONE_MEAL, 1)), ModItems.SIXTY_SECONDS_FERTILIZER, 1);
        add(list, "bone_meal", Station.WORKBENCH, "fertilizer_1", false,
                List.of(in(Items.BONE, 1)), Items.BONE_MEAL, 2);
        add(list, "nutrient_fertilizer", Station.WORKBENCH, "fertilizer_2", true,
                List.of(in(Items.POISONOUS_POTATO, 1), in(ModItems.SIXTY_SECONDS_FERTILIZER, 1)),
                ModItems.SIXTY_SECONDS_NUTRIENT_FERTILIZER, 1);
        // ── 烟草 ─────────────────────────────────────────────────────
        add(list, "tobacco_seeds", Station.WORKBENCH, "tobacco", false,
                List.of(in(ModItems.SIXTY_SECONDS_TOBACCO, 1)), ModItems.SIXTY_SECONDS_TOBACCO_SEEDS, 1);
        Item cigarette = external("watheextraitems:cigarette");
        if (cigarette != null) {
            add(list, "cigarette", Station.WORKBENCH, "tobacco", false,
                    List.of(in(ModItems.SIXTY_SECONDS_TOBACCO, 1), in(Items.PAPER, 1)), cigarette, 1);
        }
        Item cigar = external("watheextraitems:cigar");
        if (cigar != null) {
            add(list, "cigar", Station.WORKBENCH, "tobacco", false,
                    List.of(in(ModItems.SIXTY_SECONDS_TOBACCO, 1), in(chem, 1), in(oak, 1)), cigar, 1);
        }

        // ══ 炊事（厨房灶台）═══════════════════════════════════════════
        // ── 烹饪-I：生食→熟食（全部通电）+ 果干/面包/糖 ─────────────────────
        cook(list, "cook_potato", Items.POTATO, Items.BAKED_POTATO);
        cook(list, "cook_kelp", Items.KELP, Items.DRIED_KELP);
        cook(list, "cook_beef", Items.BEEF, Items.COOKED_BEEF);
        cook(list, "cook_porkchop", Items.PORKCHOP, Items.COOKED_PORKCHOP);
        cook(list, "cook_mutton", Items.MUTTON, Items.COOKED_MUTTON);
        cook(list, "cook_chicken", Items.CHICKEN, Items.COOKED_CHICKEN);
        cook(list, "cook_rabbit", Items.RABBIT, Items.COOKED_RABBIT);
        cook(list, "cook_cod", Items.COD, Items.COOKED_COD);
        cook(list, "cook_salmon", Items.SALMON, Items.COOKED_SALMON);
        add(list, "dried_fruit", Station.STOVE, "cooking_1", false,
                List.of(any("fruit", 2, fruits())), ModItems.SIXTY_SECONDS_DRIED_FRUIT, 3);
        add(list, "bread", Station.STOVE, "cooking_1", false,
                List.of(in(Items.WHEAT, 3)), Items.BREAD, 1);
        add(list, "sugar", Station.STOVE, "cooking_1", false,
                List.of(in(Items.BEETROOT, 1)), Items.SUGAR, 1);
        // ── 烹饪-II ──────────────────────────────────────────────────
        add(list, "canned_food", Station.STOVE, "cooking_2", false,
                List.of(in(scrap, 1), any("raw_meat", 1, rawMeat())), ModItems.SIXTY_SECONDS_CANNED_FOOD, 1);
        add(list, "mushroom_stew", Station.STOVE, "cooking_2", true,
                List.of(any("mushroom", 2, mushrooms()), in(Items.BOWL, 1)), Items.MUSHROOM_STEW, 1);
        add(list, "canned_soup", Station.STOVE, "cooking_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_CANNED_SOUP, 1);
        add(list, "trail_mix", Station.STOVE, "cooking_2", false,
                List.of(in(ModItems.SIXTY_SECONDS_DRIED_FRUIT, 1), any("seed_pack", 2, seedPacks())),
                ModItems.SIXTY_SECONDS_TRAIL_MIX, 2);
        add(list, "biscuit", Station.STOVE, "cooking_2", false,
                List.of(in(rice, 3)), ModItems.SIXTY_SECONDS_BISCUIT, 1);
        add(list, "rice_soup", Station.STOVE, "cooking_2", true,
                List.of(in(rice, 2), in(Items.BOWL, 1), in(waterS, 1)), ModItems.SIXTY_SECONDS_RICE_SOUP, 1);
        // ── 烹饪-III ─────────────────────────────────────────────────
        add(list, "mre", Station.STOVE, "cooking_3", false,
                List.of(in(ModItems.SIXTY_SECONDS_CANNED_SOUP, 1), any("raw_meat", 1, rawMeat()), in(rice, 1)),
                ModItems.SIXTY_SECONDS_MRE, 2);
        add(list, "cooked_noodles", Station.STOVE, "cooking_3", true,
                List.of(in(ModItems.SIXTY_SECONDS_INSTANT_NOODLES, 1), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_COOKED_NOODLES, 1);
        add(list, "rabbit_stew", Station.STOVE, "cooking_3", true,
                List.of(in(Items.COOKED_RABBIT, 1), any("vegetable", 1, vegetables()), in(Items.BOWL, 1)),
                Items.RABBIT_STEW, 1);
        add(list, "pumpkin_pie", Station.STOVE, "cooking_3", true,
                List.of(in(Items.PUMPKIN, 2), in(rice, 1), in(Items.SUGAR, 1)), Items.PUMPKIN_PIE, 1);
        // ── 烹饪-IV ──────────────────────────────────────────────────
        add(list, "stew", Station.STOVE, "cooking_4", true,
                List.of(in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 2), in(rice, 2),
                        in(ModItems.SIXTY_SECONDS_JERKY, 1), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_STEW, 1);
        add(list, "doomsday_cake", Station.STOVE, "cooking_4", true,
                List.of(in(Items.PUMPKIN, 2), in(Items.SUGAR, 4), in(rice, 2), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_DOOMSDAY_CAKE, 1);
        add(list, "luxury_stew", Station.STOVE, "cooking_4", true,
                List.of(in(Items.BOWL, 1), any("mushroom", 4, mushrooms()), in(rice, 4),
                        in(Items.PUMPKIN, 2), any("raw_meat", 2, rawMeat())),
                ModItems.SIXTY_SECONDS_LUXURY_STEW, 1);
        // ── 变废为宝 ─────────────────────────────────────────────────
        add(list, "jerky", Station.STOVE, "waste_1", false,
                List.of(in(Items.ROTTEN_FLESH, 3)), ModItems.SIXTY_SECONDS_JERKY, 1);
        add(list, "sushi", Station.STOVE, "waste_2", false,
                List.of(any("fish", 1, fish()), in(Items.DRIED_KELP, 1), in(rice, 1)),
                ModItems.SIXTY_SECONDS_SUSHI, 1);
        add(list, "bone_soup", Station.STOVE, "waste_3", true,
                List.of(any("vegetable", 1, vegetables()), any("monster_drop", 2, monsterDrops()),
                        in(Items.BOWL, 1)),
                ModItems.SIXTY_SECONDS_BONE_SOUP, 1);
        add(list, "one_pot_stew", Station.STOVE, "waste_3", false,
                List.of(in(Items.BOWL, 1), in(Items.ROTTEN_FLESH, 2), in(Items.BONE, 2),
                        in(rice, 2)),
                ModItems.SIXTY_SECONDS_ONE_POT_STEW, 1);
        // ── 变废为宝-IV ──────────────────────────────────────────────
        add(list, "haiman_meatball", Station.STOVE, "waste_4", true,
                List.of(any("monster_drop", 3, monsterDrops()), in(Items.POISONOUS_POTATO, 1),
                        any("raw_meat", 1, rawMeat())),
                ModItems.SIXTY_SECONDS_HAIMAN_MEATBALL, 1);
        add(list, "catmooncake", Station.STOVE, "waste_4", true,
                List.of(in(ModItems.SIXTY_SECONDS_WILD_RICE, 3), any("mushroom", 2, mushrooms()),
                        in(Items.SUGAR, 2)),
                ModItems.SIXTY_SECONDS_CATMOONCAKE, 1);
        // ── 袋装食品（灶台，无需通电）────────────────────────────────────
        add(list, "bagged_dried_fruit", Station.STOVE, "cooking_2", false,
                List.of(in(ModItems.SIXTY_SECONDS_DRIED_FRUIT, 3), in(Items.PAPER, 1)),
                ModItems.SIXTY_SECONDS_BAGGED_DRIED_FRUIT, 1);
        add(list, "bagged_biscuit", Station.STOVE, "cooking_2", false,
                List.of(in(ModItems.SIXTY_SECONDS_BISCUIT, 2), in(Items.PAPER, 1)),
                ModItems.SIXTY_SECONDS_BAGGED_BISCUIT, 1);

        // ══ 电力（简易工作台）═══════════════════════════════════════════
        add(list, "battery", Station.WORKBENCH, "power_1", false,
                List.of(in(scrap, 8), in(iron, 2)), ModItems.SIXTY_SECONDS_BATTERY, 1);
        add(list, "battery_large", Station.WORKBENCH, "power_2", true,
                List.of(in(battery, 2), in(elec, 1)), ModItems.SIXTY_SECONDS_BATTERY_LARGE, 1);
        add(list, "solar_panel", Station.WORKBENCH, "power_3", false,
                List.of(in(ModItems.SIXTY_SECONDS_BATTERY_LARGE, 1), in(glassPlate, 1), in(wire, 3)),
                ModItems.SIXTY_SECONDS_SOLAR_PANEL, 1);
        // ── 电力设施-I ──────────────────────────────────────────────────
        add(list, "power_battery", Station.WORKBENCH, "power_facility_1", true,
                List.of(in(elec, 3), in(ModItems.SIXTY_SECONDS_BATTERY_LARGE, 1), in(glassPlate, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_POWER_BATTERY.asItem(), 1);
        add(list, "portable_battery", Station.WORKBENCH, "power_facility_1", true,
                List.of(in(wire, 3), in(battery, 2), in(plastic, 1)),
                ModItems.SIXTY_SECONDS_PORTABLE_BATTERY, 1);
        add(list, "power_amplifier", Station.WORKBENCH, "power_facility_2", true,
                List.of(in(elec, 5), in(glassPlate, 5), in(steel, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_POWER_AMPLIFIER.asItem(), 1);

        // ══ 净化（净化台）═══════════════════════════════════════════════
        // ── 集水装置 ─────────────────────────────────────────────────
        add(list, "rain_barrel", Station.BATHTUB, "water_collect_1", false,
                List.of(in(oak, 4), in(rag, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RAIN_BARREL.asItem(), 1);
        add(list, "rain_collector", Station.BATHTUB, "water_collect_2", false,
                List.of(in(oak, 3), in(plastic, 4), in(tape, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RAIN_COLLECTOR.asItem(), 1);
        add(list, "condenser", Station.BATHTUB, "water_collect_3", false,
                List.of(in(iron, 4), in(plastic, 3), in(elec, 2), in(glassPlate, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_CONDENSER.asItem(), 1);
        // ── 净化水 ───────────────────────────────────────────────────
        add(list, "purify_small", Station.BATHTUB, "water_purify_1", false,
                List.of(in(dirty, 2)), ModItems.SIXTY_SECONDS_WATER_SMALL, 1);
        add(list, "purify_medium", Station.BATHTUB, "water_purify_1", true,
                List.of(in(dirty, 3)), ModItems.SIXTY_SECONDS_WATER_MEDIUM, 1);
        add(list, "charcoal_filter", Station.BATHTUB, "water_purify_2", false,
                List.of(in(charcoal, 2), in(rag, 2)), ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1);
        add(list, "purified_water", Station.BATHTUB, "water_purify_2", false,
                List.of(in(dirty, 2), in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1)),
                ModItems.SIXTY_SECONDS_PURIFIED_WATER, 1);
        add(list, "thermos", Station.BATHTUB, "water_purify_2", true,
                List.of(in(plastic, 2), in(waterM, 1)), ModItems.SIXTY_SECONDS_THERMOS, 1);
        add(list, "purify_batch", Station.BATHTUB, "water_purify_2", true,
                List.of(in(dirty, 4)), ModItems.SIXTY_SECONDS_WATER_SMALL, 3);
        add(list, "purify_large", Station.BATHTUB, "water_purify_3", false,
                List.of(in(dirty, 5), in(ModItems.SIXTY_SECONDS_CHARCOAL_FILTER, 1)),
                ModItems.SIXTY_SECONDS_WATER_HIGH, 1);
        // ── 茶艺 ─────────────────────────────────────────────────────
        add(list, "herbal_tea", Station.BATHTUB, "tea", true,
                List.of(in(tea, 1), in(waterS, 1)), ModItems.SIXTY_SECONDS_HERBAL_TEA, 1);
        add(list, "detox_tea", Station.BATHTUB, "tea", true,
                List.of(in(tea, 1), in(chem, 1), in(waterS, 1)), ModItems.SIXTY_SECONDS_DETOX_TEA, 1);
        add(list, "soothing_tea", Station.BATHTUB, "tea", true,
                List.of(in(tea, 1), in(rice, 3), in(waterS, 1)), ModItems.SIXTY_SECONDS_SOOTHING_TEA, 1);
        // ── 饮料工艺 ─────────────────────────────────────────────────
        add(list, "sports_drink", Station.BATHTUB, "drinks", false,
                List.of(in(waterS, 2), in(chem, 2)), ModItems.SIXTY_SECONDS_SPORTS_DRINK, 1);
        add(list, "juice", Station.BATHTUB, "drinks", false,
                List.of(in(waterS, 1), any("fruit", 1, fruits())), ModItems.SIXTY_SECONDS_JUICE, 1);

        // ══ 防御工事（高级工作台）═════════════════════════════════════════
        // ── 房门维护 ─────────────────────────────────────────────────
        add(list, "door_lock", Station.ADV_WORKBENCH, "door_1", false,
                List.of(in(iron, 2), in(copper, 1)), ModItems.SIXTY_SECONDS_DOOR_LOCK, 1);
        add(list, "door_trap", Station.ADV_WORKBENCH, "door_1", false,
                List.of(in(plastic, 2), in(glassShard, 2), in(copper, 1)), ModItems.SIXTY_SECONDS_DOOR_TRAP, 1);
        add(list, "wrench", Station.ADV_WORKBENCH, "door_1", false,
                List.of(in(iron, 4), in(copper, 1)), ModItems.SIXTY_SECONDS_WRENCH, 1);
        add(list, "door_lock_reinforced", Station.ADV_WORKBENCH, "door_2", true,
                List.of(in(steel, 2), in(glassPlate, 2)), ModItems.SIXTY_SECONDS_DOOR_LOCK_REINFORCED, 1);
        add(list, "repair_kit", Station.ADV_WORKBENCH, "door_2", false,
                List.of(in(oak, 6), in(iron, 3), in(tape, 2)), ModItems.SIXTY_SECONDS_REPAIR_KIT, 1);
        add(list, "door_lock_ultimate", Station.ADV_WORKBENCH, "door_3", true,
                List.of(in(alloy, 1), in(elec, 1)), ModItems.SIXTY_SECONDS_DOOR_LOCK_ULTIMATE, 1);
        add(list, "door_lock_alloy", Station.ADV_WORKBENCH, "door_4", true,
                List.of(in(alloy, 2), in(glassPlate, 2), in(elec, 2), in(wire, 3)),
                ModItems.SIXTY_SECONDS_DOOR_LOCK_ALLOY, 1);
        // ── 保险库 ───────────────────────────────────────────────────
        add(list, "vault_small", Station.ADV_WORKBENCH, "vault_1", true,
                List.of(in(iron, 4), in(elec, 1), in(gear, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_VAULT_SMALL.asItem(), 1);
        add(list, "vault_medium", Station.ADV_WORKBENCH, "vault_2", true,
                List.of(in(steel, 2), in(elec, 2), in(gear, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_VAULT_MEDIUM.asItem(), 1);
        add(list, "vault_large", Station.ADV_WORKBENCH, "vault_3", true,
                List.of(in(alloy, 2), in(elec, 4), in(gear, 5)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_VAULT_LARGE.asItem(), 1);
        // ── 怪物防御 ─────────────────────────────────────────────────
        add(list, "barricade", Station.ADV_WORKBENCH, "mob_defense_1", false,
                List.of(in(oak, 4), in(iron, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BARRICADE.asItem(), 1);
        add(list, "barbed_wire", Station.ADV_WORKBENCH, "mob_defense_1", true,
                List.of(in(nails, 1), in(wire, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BARBED_WIRE.asItem(), 1);
        add(list, "lamp", Station.ADV_WORKBENCH, "mob_defense_1", true,
                List.of(in(elec, 2), in(wire, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_LAMP.asItem(), 1);
        add(list, "alarm_item", Station.ADV_WORKBENCH, "mob_defense_1", true,
                List.of(in(iron, 3), in(wire, 2), in(battery, 2)), ModItems.SIXTY_SECONDS_ALARM, 1);
        add(list, "heavy_barricade", Station.ADV_WORKBENCH, "mob_defense_2", false,
                List.of(in(oak, 4), in(steel, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_HEAVY_BARRICADE.asItem(), 1);
        add(list, "spike_trap", Station.ADV_WORKBENCH, "mob_defense_2", true,
                List.of(in(nails, 4), in(iron, 4), in(glassPlate, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SPIKE_TRAP.asItem(), 1);
        add(list, "floodlight", Station.ADV_WORKBENCH, "mob_defense_2", true,
                List.of(in(elec, 5), in(wire, 5), in(steel, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_FLOODLIGHT.asItem(), 1);
        add(list, "rescue_beacon", Station.ADV_WORKBENCH, "mob_defense_2", true,
                List.of(in(battery, 3), in(elec, 3), in(wire, 3), in(scrap, 14)),
                ModItems.SIXTY_SECONDS_RESCUE_BEACON, 1);
        add(list, "lure_item", Station.ADV_WORKBENCH, "mob_defense_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_JERKY, 4), in(chem, 3)), ModItems.SIXTY_SECONDS_LURE, 1);
        add(list, "reinforced_barricade", Station.ADV_WORKBENCH, "mob_defense_3", false,
                List.of(in(oak, 4), in(alloy, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_REINFORCED_BARRICADE.asItem(), 1);
        add(list, "turret", Station.ADV_WORKBENCH, "mob_defense_3", true,
                List.of(in(elec, 5), in(wire, 10), in(steel, 8), in(glassPlate, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_TURRET.asItem(), 1);

        // ══ 抄家技术（高级工作台）═════════════════════════════════════════
        add(list, "crowbar", Station.ADV_WORKBENCH, "lockpick_craft_1", false,
                List.of(in(iron, 2), in(scrap, 2)), ModItems.SIXTY_SECONDS_CROWBAR, 1);
        add(list, "crowbar2", Station.ADV_WORKBENCH, "lockpick_craft_1", true,
                List.of(in(ModItems.SIXTY_SECONDS_CROWBAR, 1), in(iron, 5), in(copper, 2)),
                ModItems.SIXTY_SECONDS_CROWBAR_STEEL, 1);
        add(list, "crowbar3", Station.ADV_WORKBENCH, "lockpick_craft_1", true,
                List.of(in(ModItems.SIXTY_SECONDS_CROWBAR_STEEL, 1), in(steel, 2), in(glassPlate, 1)),
                ModItems.SIXTY_SECONDS_CROWBAR_HYDRAULIC, 1);
        add(list, "lockpick", Station.ADV_WORKBENCH, "lockpick_craft_2", true,
                List.of(in(iron, 4), in(scrap, 4)), ModItems.SIXTY_SECONDS_LOCKPICK, 1);
        add(list, "lockpick2", Station.ADV_WORKBENCH, "lockpick_craft_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_LOCKPICK, 1), in(steel, 2), in(scrap, 6)),
                ModItems.SIXTY_SECONDS_LOCKPICK_PRO, 1);
        add(list, "lockpick3", Station.ADV_WORKBENCH, "lockpick_craft_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_LOCKPICK_PRO, 1), in(precious, 1), in(hemp, 4)),
                ModItems.SIXTY_SECONDS_LOCKPICK_MASTER, 1);
        add(list, "vault_pick_kit", Station.ADV_WORKBENCH, "lockpick_craft_3", true,
                List.of(in(steel, 4), in(plastic, 3), in(hemp, 4)), ModItems.SIXTY_SECONDS_VAULT_PICK_KIT, 1);

        // ══ 医疗（无菌台）═══════════════════════════════════════════════
        // ── 药品材料 ─────────────────────────────────────────────────
        add(list, "rag", Station.STERILE, "med_materials_1", false,
                List.of(in(Items.STRING, 3)), ModItems.SIXTY_SECONDS_RAG, 1);
        add(list, "cloth_roll", Station.STERILE, "med_materials_1", false,
                List.of(in(rag, 3)), ModItems.SIXTY_SECONDS_CLOTH_ROLL, 1);
        add(list, "band_aid", Station.STERILE, "drugs_1", true,
                List.of(in(Items.PAPER, 2), in(chem, 1), in(ModItems.SIXTY_SECONDS_RAG, 1)),
                ModItems.SIXTY_SECONDS_BAND_AID, 1);
        add(list, "alcohol", Station.STERILE, "med_materials_2", true,
                List.of(in(waterM, 1), in(Items.POTATO, 2)), ModItems.SIXTY_SECONDS_ALCOHOL, 1);
        // ── 药品 ─────────────────────────────────────────────────────
        add(list, "simple_bandage", Station.STERILE, "drugs_1", false,
                List.of(in(clothRoll, 1)), ModItems.SIXTY_SECONDS_SIMPLE_BANDAGE, 1);
        add(list, "medicine", Station.STERILE, "drugs_1", true,
                List.of(in(waterS, 1), in(chem, 1)), ModItems.SIXTY_SECONDS_MEDICINE, 1);
        add(list, "painkillers", Station.STERILE, "drugs_1", false,
                List.of(in(chem, 2)), ModItems.SIXTY_SECONDS_PAINKILLERS, 1);
        add(list, "splint", Station.STERILE, "drugs_1", true,
                List.of(in(Items.STICK, 2)), ModItems.SIXTY_SECONDS_SPLINT, 1);
        add(list, "bandage", Station.STERILE, "drugs_2", true,
                List.of(in(alcohol, 1), in(ModItems.SIXTY_SECONDS_SIMPLE_BANDAGE, 1)),
                ModItems.SIXTY_SECONDS_BANDAGE, 1);
        add(list, "blood_bag", Station.STERILE, "drugs_3", true,
                List.of(in(clothRoll, 2), in(chem, 2)), ModItems.SIXTY_SECONDS_BLOOD_BAG, 1);
        add(list, "medkit", Station.STERILE, "drugs_4", true,
                List.of(in(tape, 2), in(alcohol, 2), in(chem, 3)), ModItems.SIXTY_SECONDS_MEDKIT, 1);
        add(list, "medical_box", Station.STERILE, "drugs_5", true,
                List.of(in(ModItems.SIXTY_SECONDS_MEDKIT, 1), in(tape, 2), in(alcohol, 2)),
                ModItems.SIXTY_SECONDS_MEDICAL_BOX, 1);
        // ── 强化补剂 ─────────────────────────────────────────────────
        add(list, "antibiotics", Station.STERILE, "tonics_1", false,
                List.of(in(chem, 3), in(rag, 2)), ModItems.SIXTY_SECONDS_ANTIBIOTICS, 1);
        add(list, "adrenaline", Station.STERILE, "tonics_2", true,
                List.of(in(chem, 3), in(alcohol, 1)), ModItems.SIXTY_SECONDS_ADRENALINE, 1);
        // ── 理智恢复 ─────────────────────────────────────────────────
        add(list, "sanity_pill", Station.STERILE, "sanity_1", false,
                List.of(in(waterS, 1), in(rice, 1)), ModItems.SIXTY_SECONDS_SANITY_PILL, 1);
        add(list, "sedative", Station.STERILE, "sanity_2", true,
                List.of(in(rice, 2), in(alcohol, 1)), ModItems.SIXTY_SECONDS_SEDATIVE, 1);
        add(list, "sanity_med", Station.STERILE, "sanity_4", true,
                List.of(in(Items.PITCHER_PLANT, 1), in(chem, 3), in(alcohol, 1)),
                ModItems.SIXTY_SECONDS_SANITY_MED, 1);
        // ── 污染净化 ─────────────────────────────────────────────────
        add(list, "charcoal_pill", Station.STERILE, "decontam_1", false,
                List.of(in(charcoal, 2)), ModItems.SIXTY_SECONDS_CHARCOAL_PILL, 1);
        add(list, "purification_tablet", Station.STERILE, "decontam_2", false,
                List.of(in(charcoal, 2), in(chem, 1), in(rice, 1)),
                ModItems.SIXTY_SECONDS_PURIFICATION_TABLET, 1);
        add(list, "anti_infection", Station.STERILE, "decontam_2", false,
                List.of(in(chem, 2), in(tea, 2)), ModItems.SIXTY_SECONDS_ANTI_INFECTION, 1);
        add(list, "anti_pollution_serum", Station.STERILE, "decontam_3", false,
                List.of(in(Items.TORCHFLOWER, 1), in(chem, 3), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_ANTI_POLLUTION_SERUM, 1);
        // ── 综合补剂 ─────────────────────────────────────────────────
        add(list, "omni_tonic", Station.STERILE, "omni_tonic", false,
                List.of(in(Items.TORCHFLOWER, 1), in(Items.PITCHER_PLANT, 1), in(alcohol, 1)),
                ModItems.SIXTY_SECONDS_OMNI_TONIC, 4);

        // ══ 酿造（酿造台，全通电，持续 1 分半 = 1800 ticks）═══════════════════
        Item bottle = Items.GLASS_BOTTLE;
        brew(list, "potion_strength", "brew_1", List.of(in(bottle, 1), in(waterS, 1), in(rice, 2)),
                Items.POTION, potion(Items.POTION, MobEffects.DAMAGE_BOOST, 1800, 0));
        brew(list, "potion_speed", "brew_1", List.of(in(bottle, 1), in(waterS, 1), in(Items.SUGAR, 2)),
                Items.POTION, potion(Items.POTION, MobEffects.MOVEMENT_SPEED, 1800, 0));
        brew(list, "potion_leaping", "brew_1",
                List.of(in(bottle, 1), in(waterS, 1), in(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, 1)),
                Items.POTION, potion(Items.POTION, MobEffects.JUMP, 1800, 0));
        brew(list, "potion_regen", "brew_1", List.of(in(bottle, 1), in(waterS, 1), in(chem, 5)),
                Items.POTION, potion(Items.POTION, MobEffects.REGENERATION, 1800, 0));
        brew(list, "potion_water_breathing", "brew_1",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.PUFFERFISH, 1)),
                Items.POTION, potion(Items.POTION, MobEffects.WATER_BREATHING, 1800, 0));
        brew(list, "potion_slow_falling", "brew_1",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.LILY_PAD, 1)),
                Items.POTION, potion(Items.POTION, MobEffects.SLOW_FALLING, 1800, 0));
        brew(list, "potion_night_vision", "brew_2", List.of(in(bottle, 1), in(waterS, 1), in(tea, 3)),
                Items.POTION, potion(Items.POTION, MobEffects.NIGHT_VISION, 1800, 0));
        brew(list, "splash_slowness", "brew_2", List.of(in(bottle, 1), in(waterS, 1), in(scrap, 2)),
                Items.SPLASH_POTION, potion(Items.SPLASH_POTION, MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
        brew(list, "splash_harming", "brew_2", List.of(in(bottle, 1), in(waterS, 1), in(scrap, 2)),
                Items.SPLASH_POTION, potion(Items.SPLASH_POTION, MobEffects.HARM, 1, 0));
        brew(list, "splash_healing", "brew_2", List.of(in(bottle, 1), in(waterS, 1), in(chem, 2)),
                Items.SPLASH_POTION, potion(Items.SPLASH_POTION, MobEffects.HEAL, 1, 0));
        brew(list, "splash_poison", "brew_2", List.of(in(bottle, 1), in(waterS, 1), in(Items.SPIDER_EYE, 3)),
                Items.SPLASH_POTION, potion(Items.SPLASH_POTION, MobEffects.POISON, 200, 0));
        brew(list, "splash_weakness", "brew_2",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.POISONOUS_POTATO, 1)),
                Items.SPLASH_POTION, potion(Items.SPLASH_POTION, MobEffects.WEAKNESS, 600, 0));
        brew(list, "potion_invisibility", "brew_3",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.PITCHER_PLANT, 1)),
                Items.POTION, potion(Items.POTION, MobEffects.INVISIBILITY, 1800, 0));
        brew(list, "lingering_slowness", "brew_3", List.of(in(bottle, 1), in(waterS, 1), in(scrap, 6)),
                Items.LINGERING_POTION, potion(Items.LINGERING_POTION, MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        brew(list, "lingering_blindness", "brew_3",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.TORCHFLOWER, 1)),
                Items.LINGERING_POTION, potion(Items.LINGERING_POTION, MobEffects.BLINDNESS, 400, 0));
        brew(list, "lingering_weakness", "brew_3",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.POISONOUS_POTATO, 4)),
                Items.LINGERING_POTION, potion(Items.LINGERING_POTION, MobEffects.WEAKNESS, 400, 1));
        brew(list, "lingering_poison", "brew_3",
                List.of(in(bottle, 1), in(waterS, 1), in(Items.SPIDER_EYE, 8)),
                Items.LINGERING_POTION, potion(Items.LINGERING_POTION, MobEffects.POISON, 400, 0));
        // ── 药剂净化 ─────────────────────────────────────────────────
        add(list, "potion_cleanser", Station.BREWING, "potion_purify", true,
                List.of(any("fruit", 2, fruits()), in(rice, 2), in(tea, 2), in(waterS, 1)),
                ModItems.SIXTY_SECONDS_POTION_CLEANSER, 1);

        // ══ 冶金（冶金炉，全通电，每件 4 秒）════════════════════════════════
        add(list, "smelt_iron", Station.SMELTER, "smelt_1", true,
                List.of(in(ModItems.SIXTY_SECONDS_SCRAP_METAL, 3)), Items.IRON_INGOT, 1);
        add(list, "smelt_plastic", Station.SMELTER, "smelt_1", true,
                List.of(in(Items.DISC_FRAGMENT_5, 2)), ModItems.SIXTY_SECONDS_PLASTIC, 1);
        add(list, "smelt_charcoal", Station.SMELTER, "smelt_1", true,
                List.of(in(oak, 4)), Items.CHARCOAL, 1);
        add(list, "smelt_copper", Station.SMELTER, "smelt_1", true,
                List.of(in(wire, 3)), ModItems.SIXTY_SECONDS_COPPER_SCRAP, 1);
        add(list, "smelt_glass_plate", Station.SMELTER, "smelt_2", true,
                List.of(in(glassShard, 4)), ModItems.SIXTY_SECONDS_GLASS_PLATE, 1);
        add(list, "smelt_steel", Station.SMELTER, "smelt_2", true,
                List.of(in(iron, 4), in(nails, 2)), ModItems.SIXTY_SECONDS_STEEL_INGOT, 1);
        add(list, "smelt_precious", Station.SMELTER, "smelt_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_PRECIOUS_PARTS, 1), in(copper, 2)),
                ModItems.SIXTY_SECONDS_PRECIOUS_METAL, 1);
        add(list, "smelt_alloy", Station.SMELTER, "smelt_3", true,
                List.of(in(steel, 1), in(glassPlate, 1), in(precious, 1)),
                ModItems.SIXTY_SECONDS_ALLOY_PLATE, 1);

        // ══ 军械装备 ═══════════════════════════════════════════════════
        // ── 盔甲（盔甲锻造台）──────────────────────────────────────────
        armorSet(list, "leather", "armor_1", false,
                List.of(in(Items.LEATHER, 2)), List.of(in(Items.LEATHER, 4)),
                List.of(in(Items.LEATHER, 3)), List.of(in(Items.LEATHER, 2)),
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        armorSet(list, "scrap", "armor_1", false,
                List.of(in(scrap, 9)), List.of(in(scrap, 14)), List.of(in(scrap, 12)), List.of(in(scrap, 9)),
                ModItems.SIXTY_SECONDS_SCRAP_HELMET, ModItems.SIXTY_SECONDS_SCRAP_CHESTPLATE,
                ModItems.SIXTY_SECONDS_SCRAP_LEGGINGS, ModItems.SIXTY_SECONDS_SCRAP_BOOTS);
        armorSet(list, "plastic", "armor_2", true,
                List.of(in(plastic, 3), in(scrap, 4)), List.of(in(plastic, 6), in(scrap, 8)),
                List.of(in(plastic, 4), in(scrap, 6)), List.of(in(plastic, 3), in(scrap, 4)),
                ModItems.SIXTY_SECONDS_PLASTIC_HELMET, ModItems.SIXTY_SECONDS_PLASTIC_CHESTPLATE,
                ModItems.SIXTY_SECONDS_PLASTIC_LEGGINGS, ModItems.SIXTY_SECONDS_PLASTIC_BOOTS);
        armorSet(list, "iron", "armor_2", true,
                List.of(in(iron, 6), in(copper, 1)), List.of(in(iron, 9), in(copper, 3)),
                List.of(in(iron, 8), in(copper, 4)), List.of(in(iron, 6), in(copper, 1)),
                ModItems.SIXTY_SECONDS_IRON_HELMET, ModItems.SIXTY_SECONDS_IRON_CHESTPLATE,
                ModItems.SIXTY_SECONDS_IRON_LEGGINGS, ModItems.SIXTY_SECONDS_IRON_BOOTS);
        armorSet(list, "steel", "armor_3", true,
                List.of(in(steel, 5)), List.of(in(steel, 8)), List.of(in(steel, 6)), List.of(in(steel, 5)),
                ModItems.SIXTY_SECONDS_STEEL_HELMET, ModItems.SIXTY_SECONDS_STEEL_CHESTPLATE,
                ModItems.SIXTY_SECONDS_STEEL_LEGGINGS, ModItems.SIXTY_SECONDS_STEEL_BOOTS);
        armorSet(list, "alloy", "armor_4", true,
                List.of(in(alloy, 3)), List.of(in(alloy, 6)), List.of(in(alloy, 4)), List.of(in(alloy, 3)),
                ModItems.SIXTY_SECONDS_ALLOY_HELMET, ModItems.SIXTY_SECONDS_ALLOY_CHESTPLATE,
                ModItems.SIXTY_SECONDS_ALLOY_LEGGINGS, ModItems.SIXTY_SECONDS_ALLOY_BOOTS);
        // ── 功能性防具 ────────────────────────────────────────────────
        add(list, "night_goggles", Station.ARMOR_FORGE, "func_armor_1", true,
                List.of(in(glassPlate, 1), in(elec, 2), in(battery, 2)), ModItems.SIXTY_SECONDS_NIGHT_GOGGLES, 1);
        add(list, "turtle_helmet", Station.ARMOR_FORGE, "func_armor_1", true,
                List.of(in(copper, 5), in(Items.PUFFERFISH, 1), in(elec, 3)), Items.TURTLE_HELMET, 1);
        add(list, "gas_mask", Station.ARMOR_FORGE, "func_armor_1", true,
                List.of(in(rag, 3), in(glassShard, 2), in(chem, 3)), ModItems.SIXTY_SECONDS_GAS_MASK, 1);
        add(list, "hazmat_suit", Station.ARMOR_FORGE, "func_armor_2", true,
                List.of(in(rag, 6), in(plastic, 3), in(tape, 3)), ModItems.SIXTY_SECONDS_HAZMAT_SUIT, 1);
        add(list, "ballistic_vest", Station.ARMOR_FORGE, "func_armor_2", true,
                List.of(in(steel, 3), in(plastic, 3), in(clothRoll, 3)), ModItems.SIXTY_SECONDS_BALLISTIC_VEST, 1);
        add(list, "horse_armor_relic", Station.ARMOR_FORGE, "func_armor_2", true,
                List.of(in(steel, 2), in(plastic, 3)), ModItems.PREDECESSOR_HORSE_ARMOR, 1);
        add(list, "riot_shield", Station.ARMOR_FORGE, "func_armor_2", true,
                List.of(in(iron, 8), in(oak, 3)), ModItems.SIXTY_SECONDS_RIOT_SHIELD, 1);
        // ── 冷兵器（武器锻造台）────────────────────────────────────────
        add(list, "pipe_club", Station.WEAPON_FORGE, "melee_1", false,
                List.of(in(iron, 2)), ModItems.SIXTY_SECONDS_PIPE, 1);
        add(list, "spiked_bat", Station.WEAPON_FORGE, "melee_1", false,
                List.of(in(oak, 3), in(scrap, 5)), ModItems.SIXTY_SECONDS_SPIKED_BAT, 1);
        add(list, "saber", Station.WEAPON_FORGE, "melee_1", false,
                List.of(in(iron, 2), in(scrap, 6)), ModItems.SIXTY_SECONDS_SABER, 1);
        add(list, "fire_axe", Station.WEAPON_FORGE, "melee_1", true,
                List.of(in(iron, 5), in(oak, 4)), ModItems.SIXTY_SECONDS_FIRE_AXE, 1);
        add(list, "cleaver", Station.WEAPON_FORGE, "melee_1", true,
                List.of(in(iron, 3), in(oak, 1), in(plastic, 2)), ModItems.SIXTY_SECONDS_CLEAVER, 1);
        add(list, "machete", Station.WEAPON_FORGE, "melee_2", true,
                List.of(in(iron, 5), in(oak, 2)), ModItems.SIXTY_SECONDS_MACHETE, 1);
        add(list, "sledgehammer", Station.WEAPON_FORGE, "melee_2", true,
                List.of(in(steel, 1), in(iron, 5), in(oak, 3)), ModItems.SIXTY_SECONDS_SLEDGEHAMMER, 1);
        add(list, "steel_spear", Station.WEAPON_FORGE, "melee_2", true,
                List.of(in(steel, 2), in(Items.STICK, 2)), ModItems.SIXTY_SECONDS_STEEL_SPEAR, 1);
        add(list, "hatchet", Station.WEAPON_FORGE, "melee_2", true,
                List.of(in(iron, 8), in(Items.STICK, 4)), ModItems.SIXTY_SECONDS_HATCHET, 1);
        add(list, "chainsaw", Station.WEAPON_FORGE, "melee_3", true,
                List.of(in(steel, 2), in(gear, 3), in(battery, 2)), ModItems.SIXTY_SECONDS_CHAINSAW, 1);
        add(list, "stun_baton", Station.WEAPON_FORGE, "melee_3", true,
                List.of(in(iron, 3), in(wire, 3), in(battery, 2)), ModItems.SIXTY_SECONDS_STUN_BATON, 1);
        add(list, "steel_sword", Station.WEAPON_FORGE, "melee_3", true,
                List.of(in(steel, 3), in(glassPlate, 1), in(Items.STICK, 2)), ModItems.SIXTY_SECONDS_STEEL_SWORD, 1);
        // ── 子弹（军械台，全通电）──────────────────────────────────────
        add(list, "ammo_pack", Station.ARSENAL, "bullets_1", true,
                List.of(in(gunpowder, 2), in(scrap, 4)), ModItems.SIXTY_SECONDS_AMMO, 6);
        add(list, "rifle_ammo", Station.ARSENAL, "bullets_2", true,
                List.of(in(gunpowder, 4), in(scrap, 5)), ModItems.SIXTY_SECONDS_RIFLE_AMMO, 6);
        add(list, "smg_ammo", Station.ARSENAL, "bullets_2", true,
                List.of(in(gunpowder, 6), in(scrap, 8)), ModItems.SIXTY_SECONDS_SMG_AMMO, 12);
        add(list, "shotgun_ammo", Station.ARSENAL, "bullets_2", true,
                List.of(in(gunpowder, 6), in(glassShard, 2), in(scrap, 5)), ModItems.SIXTY_SECONDS_SHOTGUN_AMMO, 2);
        add(list, "magnum_ammo", Station.ARSENAL, "bullets_3", true,
                List.of(in(gunpowder, 12), in(steel, 4), in(copper, 8)), ModItems.SIXTY_SECONDS_MAGNUM_AMMO, 1);
        add(list, "rocket", Station.ARSENAL, "bullets_3", true,
                List.of(in(gunpowder, 12), in(alloy, 1)), ModItems.SIXTY_SECONDS_ROCKET, 1);
        // ── 热兵器（军械台，全通电）────────────────────────────────────
        add(list, "pistol", Station.ARSENAL, "firearms_1", true,
                List.of(in(iron, 4), in(scrap, 6), in(wire, 2)), ModItems.SIXTY_SECONDS_PISTOL, 1);
        add(list, "hunting_shotgun", Station.ARSENAL, "firearms_1", true,
                List.of(in(iron, 8), in(ModItems.SIXTY_SECONDS_PIPE, 2), in(scrap, 8), in(tape, 1)),
                ModItems.SIXTY_SECONDS_HUNTING_SHOTGUN, 1);
        add(list, "rifle", Station.ARSENAL, "firearms_2", true,
                List.of(in(iron, 12), in(ModItems.SIXTY_SECONDS_PIPE, 4), in(scrap, 16), in(plastic, 6)),
                ModItems.SIXTY_SECONDS_RIFLE, 1);
        add(list, "smg", Station.ARSENAL, "firearms_2", true,
                List.of(in(steel, 10), in(ModItems.SIXTY_SECONDS_PIPE, 10), in(scrap, 20), in(glassPlate, 5)),
                ModItems.SIXTY_SECONDS_SMG, 1);
        add(list, "combat_shotgun", Station.ARSENAL, "firearms_2", true,
                List.of(in(steel, 15), in(precious, 3), in(hemp, 10), in(gear, 5)),
                ModItems.SIXTY_SECONDS_COMBAT_SHOTGUN, 1);
        add(list, "sniper", Station.ARSENAL, "firearms_3", true,
                List.of(in(alloy, 3), in(steel, 15), in(hemp, 8), in(oak, 10), in(gear, 5)),
                ModItems.SIXTY_SECONDS_SNIPER, 1);
        add(list, "rpg", Station.ARSENAL, "firearms_3", true,
                List.of(in(alloy, 6), in(steel, 20), in(hemp, 12), in(elec, 6), in(gear, 6), in(oak, 10)),
                ModItems.SIXTY_SECONDS_RPG, 1);
        // ── 投掷物（军械台，全通电）────────────────────────────────────
        add(list, "molotov", Station.ARSENAL, "throwables_1", true,
                List.of(in(alcohol, 2), in(rag, 2), in(glassShard, 2)), ModItems.SIXTY_SECONDS_MOLOTOV, 1);
        add(list, "pipe_bomb", Station.ARSENAL, "throwables_1", true,
                List.of(in(gunpowder, 3), in(scrap, 8)), ModItems.SIXTY_SECONDS_PIPE_BOMB, 1);
        add(list, "flashbang", Station.ARSENAL, "throwables_1", true,
                List.of(in(gunpowder, 2), in(chem, 2), in(scrap, 5)), ModItems.SIXTY_SECONDS_FLASHBANG, 1);
        add(list, "decoy_flare", Station.ARSENAL, "throwables_1", true,
                List.of(in(gunpowder, 1), in(ModItems.SIXTY_SECONDS_SCRAP_METAL, 4)),
                ModItems.SIXTY_SECONDS_DECOY_FLARE, 1);
        add(list, "incendiary_grenade", Station.ARSENAL, "throwables_2", true,
                List.of(in(alcohol, 2), in(ModItems.SIXTY_SECONDS_FUEL_CAN, 2), in(gunpowder, 2)),
                ModItems.SIXTY_SECONDS_INCENDIARY_GRENADE, 1);
        add(list, "frag_grenade", Station.ARSENAL, "throwables_2", true,
                List.of(in(gunpowder, 3), in(scrap, 6), in(nails, 4)), ModItems.SIXTY_SECONDS_FRAG_GRENADE, 1);
        add(list, "sixty_smoke", Station.ARSENAL, "throwables_3", true,
                List.of(in(steel, 2), in(wire, 2), in(scrap, 10)), ModItems.SIXTY_SECONDS_SMOKE_GRENADE, 1);
        add(list, "sixty_marking", Station.ARSENAL, "throwables_3", true,
                List.of(in(steel, 1), in(hemp, 2), in(copper, 3)), ModItems.SIXTY_SECONDS_MARKING_GRENADE, 1);
        add(list, "sixty_flare", Station.ARSENAL, "throwables_1", false,
                List.of(in(Items.PAPER, 1), in(Items.CHARCOAL, 1), in(copper, 1)), org.agmas.noellesroles.init.ModItems.FLARE, 1);
        // ── 弓 / 弩（武器锻造台）──────────────────────────────────────
        add(list, "crude_bow", Station.WEAPON_FORGE, "archery_1", false,
                List.of(in(Items.STICK, 3), in(Items.STRING, 3)), ModItems.SIXTY_SECONDS_CRUDE_BOW, 1);
        add(list, "hunting_bow", Station.WEAPON_FORGE, "archery_1", false,
                List.of(in(Items.STICK, 3), in(hemp, 3), in(iron, 1)), ModItems.SIXTY_SECONDS_HUNTING_BOW, 1);
        add(list, "recurve_bow", Station.WEAPON_FORGE, "archery_2", true,
                List.of(in(oak, 2), in(steel, 2), in(hemp, 4), in(tape, 1)),
                ModItems.SIXTY_SECONDS_RECURVE_BOW, 1);
        add(list, "hand_crossbow", Station.WEAPON_FORGE, "archery_2", true,
                List.of(in(iron, 3), in(gear, 1), in(hemp, 2), in(Items.STICK, 2)),
                ModItems.SIXTY_SECONDS_HAND_CROSSBOW, 1);
        add(list, "compound_bow", Station.WEAPON_FORGE, "archery_3", true,
                List.of(in(alloy, 1), in(steel, 3), in(gear, 2), in(hemp, 6), in(plastic, 3)),
                ModItems.SIXTY_SECONDS_COMPOUND_BOW, 1);
        add(list, "heavy_crossbow", Station.WEAPON_FORGE, "archery_3", true,
                List.of(in(steel, 5), in(gear, 3), in(elec, 1), in(hemp, 4), in(oak, 3)),
                ModItems.SIXTY_SECONDS_HEAVY_CROSSBOW, 1);
        // ── 箭矢（军械台）────────────────────────────────────────────
        add(list, "crude_arrow", Station.ARSENAL, "arrow_craft_1", false,
                List.of(in(Items.STICK, 2), in(glassShard, 1), in(Items.FEATHER, 1)),
                ModItems.SIXTY_SECONDS_CRUDE_ARROW, 4);
        add(list, "iron_arrow", Station.ARSENAL, "arrow_craft_1", false,
                List.of(in(Items.STICK, 2), in(iron, 1), in(Items.FEATHER, 1)),
                ModItems.SIXTY_SECONDS_IRON_ARROW, 6);
        add(list, "steel_arrow", Station.ARSENAL, "arrow_craft_2", true,
                List.of(in(Items.STICK, 2), in(steel, 1), in(Items.FEATHER, 1)),
                ModItems.SIXTY_SECONDS_STEEL_ARROW, 6);
        add(list, "fire_arrow", Station.ARSENAL, "arrow_craft_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_IRON_ARROW, 4), in(chem, 1), in(Items.CHARCOAL, 1)),
                ModItems.SIXTY_SECONDS_FIRE_ARROW, 4);
        add(list, "poison_arrow", Station.ARSENAL, "arrow_craft_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_IRON_ARROW, 4), in(Items.SPIDER_EYE, 2)),
                ModItems.SIXTY_SECONDS_POISON_ARROW, 4);
        add(list, "explosive_arrow", Station.ARSENAL, "arrow_craft_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_STEEL_ARROW, 2), in(gunpowder, 2)),
                ModItems.SIXTY_SECONDS_EXPLOSIVE_ARROW, 2);

        // ══ 基地设施（车床，全通电）═══════════════════════════════════════
        add(list, "expansion_key_1", Station.LATHE, "base_expand_1", true,
                List.of(in(iron, 8), in(plastic, 8), in(wire, 3)), ModItems.SIXTY_SECONDS_EXPANSION_KEY_1, 1);
        add(list, "expansion_key_2", Station.LATHE, "base_expand_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_EXPANSION_KEY_1, 1), in(steel, 8), in(glassPlate, 3),
                        in(elec, 3)),
                ModItems.SIXTY_SECONDS_EXPANSION_KEY_2, 1);
        add(list, "expansion_key_3", Station.LATHE, "base_expand_3", true,
                List.of(in(ModItems.SIXTY_SECONDS_EXPANSION_KEY_2, 1), in(alloy, 1), in(scrap, 32),
                        in(gear, 5), in(hemp, 16)),
                ModItems.SIXTY_SECONDS_EXPANSION_KEY_3, 1);
        add(list, "detach_wrench", Station.LATHE, "base_facility_1", true,
                List.of(in(iron, 2), in(copper, 1)), ModItems.SIXTY_SECONDS_DETACH_WRENCH, 1);
        add(list, "base_chest_small", Station.LATHE, "base_facility_1", true,
                List.of(in(oak, 5), in(plastic, 3)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BASE_CHEST_SMALL.asItem(), 1);
        add(list, "base_chest_large", Station.LATHE, "base_facility_2", true,
                List.of(in(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BASE_CHEST_SMALL.asItem(), 1),
                        in(oak, 5), in(glassPlate, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BASE_CHEST_LARGE.asItem(), 1);
        add(list, "base_alarm", Station.LATHE, "base_facility_2", true,
                List.of(in(elec, 4), in(wire, 3), in(glassPlate, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_BASE_ALARM.asItem(), 1);
        add(list, "doll", Station.LATHE, "base_facility_2", true,
                List.of(in(clothRoll, 3), in(scrap, 5), in(plastic, 1)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DOLL.asItem(), 1);
        add(list, "subwoofer", Station.LATHE, "base_facility_3", true,
                List.of(in(steel, 6), in(plastic, 6), in(elec, 4), in(hemp, 2)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUBWOOFER.asItem(), 1);

        // ══ 交通工具 ═══════════════════════════════════════════════════
        // ── 燃油（液体融锅）────────────────────────────────────────────
        add(list, "fuel_can", Station.MELTING_POT, "fuel_1", true,
                List.of(in(alcohol, 3), in(chem, 2)), ModItems.SIXTY_SECONDS_FUEL_CAN, 1);
        add(list, "diesel_can", Station.MELTING_POT, "fuel_2", true,
                List.of(in(ModItems.SIXTY_SECONDS_FUEL_CAN, 2), in(plastic, 4), in(chem, 4)),
                ModItems.SIXTY_SECONDS_DIESEL_CAN, 1);
        // ── 马匹（车床）───────────────────────────────────────────────
        add(list, "horseshoe_rainbow", Station.LATHE, "horse_1", true,
                List.of(in(iron, 20), in(Items.LEATHER, 4), in(plastic, 6), in(scrap, 8), in(chem, 1)),
                org.agmas.noellesroles.init.FunnyItems.RAINBOW_HORSESHOE, 1);
        add(list, "horseshoe_canyuesa", Station.LATHE, "horse_1", true,
                List.of(in(iron, 20), in(Items.LEATHER, 4), in(plastic, 6), in(scrap, 8), in(gear, 1)),
                org.agmas.noellesroles.init.FunnyItems.CANYUESA_HORSESHOE, 1);
        add(list, "horseshoe_superpig", Station.LATHE, "horse_2", true,
                List.of(in(steel, 10), in(Items.LEATHER, 6), in(scrap, 12), in(plastic, 8), in(hemp, 6)),
                org.agmas.noellesroles.init.FunnyItems.SUPER_PIG_HORSESHOE, 1);
        // ── 载具（车床）───────────────────────────────────────────────
        add(list, "wheelchair", Station.LATHE, "vehicle_1", true,
                List.of(in(steel, 6), in(gear, 5), in(elec, 5), in(wire, 3), in(hemp, 5)),
                ModItems.WHEELCHAIR, 1);
        add(list, "motorcycle", Station.LATHE, "vehicle_2", true,
                List.of(in(steel, 12), in(elec, 10), in(gear, 10), in(wire, 10), in(battery, 2), in(hemp, 20)),
                ModItems.SIXTY_SECONDS_MOTORCYCLE, 1);
        add(list, "car", Station.LATHE, "vehicle_3", true,
                List.of(in(alloy, 10), in(steel, 15), in(glassPlate, 4), in(elec, 20), in(gear, 20),
                        in(wire, 20), in(battery, 6), in(hemp, 32)),
                ModItems.SIXTY_SECONDS_CAR, 1);
        add(list, "vehicle_repair_tool", Station.LATHE, "vehicle_repair", true,
                List.of(in(steel, 2), in(wire, 3), in(glassPlate, 1)),
                ModItems.SIXTY_SECONDS_VEHICLE_REPAIR_TOOL, 1);

        // ══ 神秘技术 ═══════════════════════════════════════════════════
        // 祭坛本体在高级工作台合成；其余在祭坛合成
        add(list, "altar", Station.ADV_WORKBENCH, "sacrifice_1", false,
                List.of(in(alloy, 2), in(steel, 10), in(chem, 10), in(ModItems.SIXTY_SECONDS_BREWING_PARTS, 6),
                        in(Items.LEATHER, 10)),
                org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_ALTAR.asItem(), 1);
        add(list, "filthy_jar", Station.ALTAR, "sacrifice_2", false,
                List.of(in(Items.GLASS_BOTTLE, 10), in(glassPlate, 2), in(chem, 5)),
                ModItems.SIXTY_SECONDS_FILTHY_JAR, 1);
        add(list, "undying_totem", Station.ALTAR, "undying_totem", true,
                List.of(in(ModItems.SIXTY_SECONDS_FUEL_CAN, 2), in(alloy, 2), in(glassPlate, 5),
                        in(chem, 10), in(scrap, 20)),
                Items.TOTEM_OF_UNDYING, 1);
        add(list, "revival_totem", Station.ALTAR, "revival_totem", true,
                List.of(in(ModItems.SIXTY_SECONDS_BLOOD_JAR, 1), in(alloy, 5), in(glassPlate, 5),
                        in(hemp, 10), in(chem, 10), in(scrap, 32)),
                ModItems.SIXTY_SECONDS_REVIVAL_TOTEM, 1);

        return list;
    }

    // ── 构建辅助 ─────────────────────────────────────────────────

    private static void add(List<Recipe> list, String id, Station station, String techId, boolean needsPower,
            List<Ingredient> inputs, Item output, int outputCount) {
        list.add(new Recipe(id, station, techId, needsPower, inputs, output, outputCount, null));
    }

    /** 酿造台药水配方（全通电，产物用工厂生成带效果的原版药水）。 */
    private static void brew(List<Recipe> list, String id, String techId, List<Ingredient> inputs,
            Item base, Supplier<ItemStack> factory) {
        list.add(new Recipe(id, Station.BREWING, techId, true, inputs, base, 1, factory));
    }

    /** 生食 → 熟食（烹饪-I，全通电）。 */
    private static void cook(List<Recipe> list, String id, Item raw, Item cooked) {
        add(list, id, Station.STOVE, "cooking_1", true, List.of(in(raw, 1)), cooked, 1);
    }

    /** 一套四件盔甲（盔甲锻造台）。 */
    private static void armorSet(List<Recipe> list, String prefix, String techId, boolean needsPower,
            List<Ingredient> helmet, List<Ingredient> chest, List<Ingredient> leggings, List<Ingredient> boots,
            Item helmetItem, Item chestItem, Item leggingsItem, Item bootsItem) {
        add(list, prefix + "_helmet", Station.ARMOR_FORGE, techId, needsPower, helmet, helmetItem, 1);
        add(list, prefix + "_chestplate", Station.ARMOR_FORGE, techId, needsPower, chest, chestItem, 1);
        add(list, prefix + "_leggings", Station.ARMOR_FORGE, techId, needsPower, leggings, leggingsItem, 1);
        add(list, prefix + "_boots", Station.ARMOR_FORGE, techId, needsPower, boots, bootsItem, 1);
    }

    private static Ingredient in(Item item, int count) {
        return new Ingredient(List.of(item), count, null);
    }

    /** 「任意 X」组配料：groupKey 对应 {@code group.noellesroles.sixty_seconds.<key>} 展示名。 */
    private static Ingredient any(String groupKey, int count, List<Item> items) {
        return new Ingredient(items, count, groupKey);
    }
}

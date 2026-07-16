package net.exmo.sre.sixtyseconds.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局共享 loot 表：类别 → 加权条目列表。物资箱按自身 category 从此表加权抽取。
 * 存本地 JSON（{@link SixtySecondsLootStore}），可经 GUI 编辑并 C2S 上传落盘。
 */
public class SixtySecondsLootTable {
    /** 类别名 → 条目列表（保持插入顺序）。Gson 直接序列化本字段。 */
    public LinkedHashMap<String, List<Entry>> categories = new LinkedHashMap<>();

    public static class Entry {
        public String itemId = "minecraft:bread";
        public int count = 1;
        public float weight = 1.0F;

        public Entry() {
        }

        public Entry(String itemId, int count, float weight) {
            this.itemId = itemId;
            this.count = count;
            this.weight = weight;
        }
    }

    public List<String> categoryNames() {
        return new ArrayList<>(categories.keySet());
    }

    /** 类别当前是否有可抽条目（存在正权重条目）。 */
    public boolean canRoll(String category) {
        List<Entry> list = categories.get(category);
        if (list == null) {
            return false;
        }
        for (Entry entry : list) {
            if (entry.weight > 0) {
                return true;
            }
        }
        return false;
    }

    /** 从某类别加权抽取一件物资；空/无效返回 {@link ItemStack#EMPTY}。 */
    public ItemStack roll(String category, RandomSource random) {
        return roll(category, random, 1.0);
    }

    /**
     * 带「稀有度压平」的加权抽取：有效权重 = {@code weight^exponent}（exponent∈(0,1]，
     * 越小则低权重稀有条目相对越容易被抽中）。区域危险等级/Boss 掉落用
     * {@code SixtySecondsAreaLevels.lootExponent} 算指数；exponent=1 与原行为一致。
     */
    public ItemStack roll(String category, RandomSource random, double exponent) {
        List<Entry> list = categories.get(category);
        if (list == null || list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        double total = 0;
        for (Entry entry : list) {
            total += entry.weight > 0 ? Math.pow(entry.weight, exponent) : 0;
        }
        if (total <= 0) {
            return ItemStack.EMPTY;
        }
        double r = random.nextDouble() * total;
        double cumulative = 0;
        // 严格小于：r 恰落在边界（如 r=0 且首条目权重为 0）时不能选中 0 权重条目
        for (Entry entry : list) {
            if (entry.weight <= 0) {
                continue;
            }
            cumulative += Math.pow(entry.weight, exponent);
            if (r < cumulative) {
                return makeStack(entry);
            }
        }
        // 浮点累加误差兜底：返回最后一个正权重条目
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).weight > 0) {
                return makeStack(list.get(i));
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack makeStack(Entry entry) {
        ResourceLocation rl = ResourceLocation.tryParse(entry.itemId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        // 未注册的 item（get 返回 AIR）也视为空，防止显示「空气」
        if (item == Items.AIR && !entry.itemId.equals("minecraft:air")) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, entry.count));
    }

    /** 内置默认表（首次运行时写盘）。 */
    public static SixtySecondsLootTable defaultTable() {
        SixtySecondsLootTable table = new SixtySecondsLootTable();
        table.categories.put("food", new ArrayList<>(List.of(
                new Entry("minecraft:bread", 1, 3.0F),
                new Entry("minecraft:cooked_beef", 1, 2.0F),
                new Entry("minecraft:apple", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_canned_food", 1, 2.5F),
                new Entry("noellesroles:sixty_seconds_biscuit", 2, 2.5F),
                new Entry("noellesroles:sixty_seconds_jerky", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_instant_noodles", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_chocolate_bar", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_energy_bar", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_seeds_pack", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_dried_fruit", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_trail_mix", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_fresh_vegetables", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_canned_soup", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_mre", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_stew", 1, 1.0F))));
        table.categories.put("water", new ArrayList<>(List.of(
                new Entry("minecraft:potion", 1, 3.0F),
                new Entry("minecraft:glass_bottle", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_juice", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_coffee", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_sports_drink", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_water_pack", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_purified_water", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_thermos", 1, 0.8F))));
        table.categories.put("medicine", new ArrayList<>(List.of(
                new Entry("minecraft:golden_apple", 1, 1.0F),
                new Entry("minecraft:honey_bottle", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_painkillers", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_antibiotics", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_sedative", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_vitamin", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_purification_tablet", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_charcoal_pill", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_detox_tea", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_medkit", 1, 0.6F),
                new Entry("noellesroles:sixty_seconds_herbal_tea", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_blood_bag", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_anti_pollution_serum", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_adrenaline", 1, 0.6F))));
        table.categories.put("tool", new ArrayList<>(List.of(
                new Entry("noellesroles:sixty_seconds_torch", 2, 3.0F),
                new Entry("minecraft:iron_ingot", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_wrench", 1, 0.5F),
                new Entry("noellesroles:sixty_seconds_toolbox", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_compass", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_repair_kit", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_grappling_hook", 1, 0.6F),
                new Entry("noellesroles:sixty_seconds_claw_hook", 1, 0.5F),
                new Entry("noellesroles:sixty_seconds_blueprint", 1, 0.4F),
                // 专用合成台（可携带的工作台/灶台/净化台，稀有）
                new Entry("noellesroles:sixty_seconds_turret", 1, 0.25F),
                new Entry("noellesroles:sixty_seconds_workbench", 1, 0.4F),
                new Entry("noellesroles:sixty_seconds_stove", 1, 0.3F),
                new Entry("noellesroles:sixty_seconds_purifier", 1, 0.3F),
                // 娱乐物品（右键给周围玩家回理智，恢复量/耐久按类型不同）
                new Entry("noellesroles:sixty_seconds_poker", 1, 0.5F),
                new Entry("noellesroles:sixty_seconds_chess", 1, 0.4F),
                new Entry("noellesroles:sixty_seconds_harmonica", 1, 0.5F),
                new Entry("noellesroles:sixty_seconds_guitar", 1, 0.3F),
                new Entry("noellesroles:sixty_seconds_teddy_bear", 1, 0.3F))));
        // 科技树/合成材料（废料解锁科技；破布+酒精→绷带；污染水→净化）
        table.categories.put("material", new ArrayList<>(List.of(
                new Entry("noellesroles:sixty_seconds_scrap", 2, 4.0F),
                new Entry("minecraft:oak_planks", 2, 2.5F),
                new Entry("minecraft:iron_ingot", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_rag", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_alcohol", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_dirty_water", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_duct_tape", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_battery", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_plastic", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_glass_shard", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_wire", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_chemicals", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_electronics", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_gear", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_gunpowder_pack", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_steel_ingot", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_nails", 3, 2.0F),
                new Entry("noellesroles:sixty_seconds_fertilizer", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_charcoal_filter", 1, 1.0F),
                new Entry("minecraft:charcoal", 2, 1.5F),
                new Entry("minecraft:potato", 2, 1.5F))));
        // 枪械与弹药（稀有；命中怪物即死/玩家扣血，见 SixtySecondsGunItem）
        table.categories.put("weapon", new ArrayList<>(List.of(
                new Entry("noellesroles:sixty_seconds_ammo", 4, 4.0F),
                new Entry("noellesroles:sixty_seconds_pistol", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_hunting_shotgun", 1, 0.6F),
                new Entry("noellesroles:sixty_seconds_rifle", 1, 0.4F),
                new Entry("noellesroles:sixty_seconds_sniper", 1, 0.25F),
                new Entry("noellesroles:sixty_seconds_rpg", 1, 0.15F),
                new Entry("noellesroles:sixty_seconds_hatchet", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_crude_bow", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_hunting_bow", 1, 0.7F),
                new Entry("noellesroles:sixty_seconds_hand_crossbow", 1, 0.4F),
                new Entry("noellesroles:sixty_seconds_crude_arrow", 6, 2.5F),
                new Entry("noellesroles:sixty_seconds_iron_arrow", 4, 1.5F),
                new Entry("noellesroles:sixty_seconds_cleaver", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_incendiary_grenade", 1, 0.8F),
                new Entry("noellesroles:sixty_seconds_frag_grenade", 1, 0.7F))));
        // 空投专属（高价值物资，各队争抢焦点）：枪械/高级材料/药品/食物混编
        table.categories.put("airdrop", new ArrayList<>(List.of(
                new Entry("noellesroles:sixty_seconds_ammo", 8, 5.0F),
                new Entry("noellesroles:sixty_seconds_rifle", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_sniper", 1, 0.6F),
                new Entry("noellesroles:sixty_seconds_rpg", 1, 0.3F),
                new Entry("noellesroles:sixty_seconds_hunting_shotgun", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_frag_grenade", 2, 1.5F),
                new Entry("noellesroles:sixty_seconds_steel_ingot", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_electronics", 2, 1.5F),
                new Entry("noellesroles:sixty_seconds_gear", 2, 1.5F),
                new Entry("noellesroles:sixty_seconds_gunpowder_pack", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_charcoal_filter", 2, 1.2F),
                new Entry("noellesroles:sixty_seconds_canned_soup", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_mre", 2, 2.0F),
                new Entry("noellesroles:sixty_seconds_purified_water", 2, 1.5F),
                new Entry("noellesroles:sixty_seconds_bandage", 1, 1.5F),
                new Entry("noellesroles:sixty_seconds_medicine", 1, 1.0F),
                new Entry("noellesroles:sixty_seconds_anti_pollution_serum", 1, 0.8F),
                new Entry("minecraft:golden_apple", 1, 0.6F),
                new Entry("noellesroles:sixty_seconds_fertilizer", 2, 1.5F))));
        // 野外专属（只在探索区/避难所外的箱子额外掉：见 SupplyBoxBlockEntity.claim）——
        // 冶金/酿造原料 + 信号枪，是回家科技链的关键输入
        table.categories.put("field", new ArrayList<>(List.of(
                new Entry("noellesroles:sixty_seconds_scrap_metal", 2, 4.0F),
                new Entry("noellesroles:sixty_seconds_precious_parts", 1, 1.2F),
                new Entry("noellesroles:sixty_seconds_brewing_parts", 1, 1.5F),
                new Entry("minecraft:disc_fragment_5", 1, 2.0F),
                new Entry("noellesroles:sixty_seconds_flare_gun", 1, 0.4F))));
        return table;
    }

    // ── 网络序列化 ──────────────────────────────────────────────
    public void writeTo(FriendlyByteBuf buf) {
        buf.writeVarInt(categories.size());
        for (Map.Entry<String, List<Entry>> e : categories.entrySet()) {
            buf.writeUtf(e.getKey());
            List<Entry> list = e.getValue() == null ? List.of() : e.getValue();
            buf.writeVarInt(list.size());
            for (Entry entry : list) {
                buf.writeUtf(entry.itemId == null ? "" : entry.itemId);
                buf.writeVarInt(entry.count);
                buf.writeFloat(entry.weight);
            }
        }
    }

    public static SixtySecondsLootTable readFrom(FriendlyByteBuf buf) {
        SixtySecondsLootTable table = new SixtySecondsLootTable();
        int cats = buf.readVarInt();
        for (int i = 0; i < cats; i++) {
            String category = buf.readUtf();
            int n = buf.readVarInt();
            List<Entry> list = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                list.add(new Entry(buf.readUtf(), buf.readVarInt(), buf.readFloat()));
            }
            table.categories.put(category, list);
        }
        return table;
    }
}

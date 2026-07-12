package net.exmo.sre.sixtyseconds.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    /** 从某类别加权抽取一件物资；空/无效返回 {@link ItemStack#EMPTY}。 */
    public ItemStack roll(String category, RandomSource random) {
        List<Entry> list = categories.get(category);
        if (list == null || list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        double total = 0;
        for (Entry entry : list) {
            total += Math.max(0, entry.weight);
        }
        if (total <= 0) {
            return ItemStack.EMPTY;
        }
        double r = random.nextDouble() * total;
        double cumulative = 0;
        for (Entry entry : list) {
            cumulative += Math.max(0, entry.weight);
            if (r <= cumulative) {
                return makeStack(entry);
            }
        }
        return makeStack(list.get(list.size() - 1));
    }

    private static ItemStack makeStack(Entry entry) {
        ResourceLocation rl = ResourceLocation.tryParse(entry.itemId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(item, Math.max(1, entry.count));
    }

    /** 内置默认表（首次运行时写盘）。 */
    public static SixtySecondsLootTable defaultTable() {
        SixtySecondsLootTable table = new SixtySecondsLootTable();
        table.categories.put("food", new ArrayList<>(List.of(
                new Entry("minecraft:bread", 1, 3.0F),
                new Entry("minecraft:cooked_beef", 1, 2.0F),
                new Entry("minecraft:apple", 2, 2.0F))));
        table.categories.put("water", new ArrayList<>(List.of(
                new Entry("minecraft:potion", 1, 3.0F),
                new Entry("minecraft:glass_bottle", 1, 2.0F))));
        table.categories.put("medicine", new ArrayList<>(List.of(
                new Entry("minecraft:golden_apple", 1, 1.0F),
                new Entry("minecraft:honey_bottle", 1, 2.0F))));
        table.categories.put("tool", new ArrayList<>(List.of(
                new Entry("minecraft:torch", 4, 3.0F),
                new Entry("minecraft:iron_ingot", 1, 1.0F))));
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

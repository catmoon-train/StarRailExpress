package net.exmo.sre.sixtyseconds.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC 商人的商店表：档案名 → 商品条目列表。商人按自身 {@code shopProfile} 取货架。
 * 存本地 JSON（{@link SixtySecondsShopStore}），可经创造模式 GUI 编辑并 C2S 上传落盘
 * （照抄 loot 表那套：CCA sync 不落盘，必须服务端显式写文件）。
 * <p>
 * 与 loot 表的差别：loot 是「加权随机抽一件」，商店是「按顺序列出、按价买」，
 * 故条目字段是 价格/库存/日回补/价格浮动 而不是权重。
 */
public class SixtySecondsShopTable {
    /** 档案名 → 商品列表（保持插入顺序）。Gson 直接序列化本字段。 */
    public LinkedHashMap<String, List<Entry>> profiles = new LinkedHashMap<>();

    public static class Entry {
        public String itemId = "minecraft:bread";
        public int count = 1;
        /** 基准价（代币）；实际售价 = 基准价 × (1 ± priceJitter)，每商人每日一掷。 */
        public int price = 5;
        /**
         * 结算货币。当前只实现 {@code MINIGAME_TOKEN}（= SREPlayerMinigameTaskComponent 代币，
         * 亦即 60s 的实体币 sixty_seconds_coin）。留字符串口子给以后接 ShopEntry.Currency.MONEY。
         */
        public String currency = "MINIGAME_TOKEN";
        /** 每日库存上限。 */
        public int stock = 8;
        /** 每日回补量（不超过 stock 上限）。 */
        public int restockPerDay = 4;
        /** 价格浮动幅度（0=不浮动，0.25=±25%）。 */
        public float priceJitter = 0.25F;

        public Entry() {
        }

        public Entry(String itemId, int count, int price, int stock, int restockPerDay, float priceJitter) {
            this.itemId = itemId;
            this.count = count;
            this.price = price;
            this.stock = stock;
            this.restockPerDay = restockPerDay;
            this.priceJitter = priceJitter;
        }
    }

    public List<String> profileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    /** 取某档案的商品列表；不存在则回退 {@code default}，再不存在给空表。 */
    public List<Entry> entriesOf(String profile) {
        List<Entry> list = profiles.get(profile);
        if (list == null) {
            list = profiles.get("default");
        }
        return list == null ? List.of() : list;
    }

    /** 条目对应的展示物品堆；itemId 无效返回 EMPTY。 */
    public static ItemStack makeStack(Entry entry) {
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

    /** 内置默认表（首次运行时写盘）：一个通用 default 档案 + 军火/药品两个特色档案。 */
    public static SixtySecondsShopTable defaultTable() {
        SixtySecondsShopTable table = new SixtySecondsShopTable();

        List<Entry> general = new ArrayList<>();
        general.add(new Entry("noellesroles:sixty_seconds_canned_food", 1, 6, 8, 4, 0.25F));
        general.add(new Entry("noellesroles:sixty_seconds_water_small", 1, 5, 8, 4, 0.25F));
        general.add(new Entry("noellesroles:sixty_seconds_scrap", 4, 4, 10, 6, 0.20F));
        general.add(new Entry("noellesroles:sixty_seconds_bandage", 1, 8, 4, 2, 0.30F));
        general.add(new Entry("noellesroles:sixty_seconds_flashlight", 1, 14, 2, 1, 0.20F));
        table.profiles.put("default", general);

        List<Entry> arms = new ArrayList<>();
        arms.add(new Entry("noellesroles:sixty_seconds_ammo", 8, 10, 6, 3, 0.30F));
        arms.add(new Entry("noellesroles:sixty_seconds_pistol", 1, 40, 1, 1, 0.15F));
        arms.add(new Entry("noellesroles:sixty_seconds_scrap", 6, 5, 8, 4, 0.20F));
        table.profiles.put("arms", arms);

        List<Entry> medic = new ArrayList<>();
        medic.add(new Entry("noellesroles:sixty_seconds_bandage", 1, 7, 6, 3, 0.25F));
        medic.add(new Entry("noellesroles:sixty_seconds_medicine", 1, 12, 4, 2, 0.30F));
        medic.add(new Entry("noellesroles:sixty_seconds_canned_food", 1, 6, 4, 2, 0.25F));
        table.profiles.put("medic", medic);

        return table;
    }

    // ── 网络序列化（照抄 SixtySecondsLootTable.writeTo/readFrom）──────────────

    public void writeTo(FriendlyByteBuf buf) {
        buf.writeVarInt(profiles.size());
        for (Map.Entry<String, List<Entry>> e : profiles.entrySet()) {
            buf.writeUtf(e.getKey());
            List<Entry> list = e.getValue() == null ? List.of() : e.getValue();
            buf.writeVarInt(list.size());
            for (Entry entry : list) {
                buf.writeUtf(entry.itemId == null ? "" : entry.itemId);
                buf.writeVarInt(entry.count);
                buf.writeVarInt(entry.price);
                buf.writeUtf(entry.currency == null ? "MINIGAME_TOKEN" : entry.currency);
                buf.writeVarInt(entry.stock);
                buf.writeVarInt(entry.restockPerDay);
                buf.writeFloat(entry.priceJitter);
            }
        }
    }

    public static SixtySecondsShopTable readFrom(FriendlyByteBuf buf) {
        SixtySecondsShopTable table = new SixtySecondsShopTable();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            String profile = buf.readUtf();
            int n = buf.readVarInt();
            List<Entry> list = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                Entry entry = new Entry();
                entry.itemId = buf.readUtf();
                entry.count = buf.readVarInt();
                entry.price = buf.readVarInt();
                entry.currency = buf.readUtf();
                entry.stock = buf.readVarInt();
                entry.restockPerDay = buf.readVarInt();
                entry.priceJitter = buf.readFloat();
                list.add(entry);
            }
            table.profiles.put(profile, list);
        }
        return table;
    }
}

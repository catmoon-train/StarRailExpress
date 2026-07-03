package io.wifi.starrailexpress.shop;

import io.netty.buffer.Unpooled;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/**
 * 商店价格同步表：把每个职业（role id）解析后的商店条目压缩成 {@code (itemId, price)} 列表。
 * Shop price-sync table: the resolved shop entries of every role flattened to {@code (itemId, price)}
 * lists, keyed by role id (stringified).
 *
 * <p>之所以只同步「价格 + 物品 id」而非整条 {@link ShopEntry}：条目的 {@code canDisplay/canBuy/onBuy}
 * 等行为在两端是同一份代码，无需也无法序列化；真正会因服务器配置不同而产生差异的只有价格。客户端拿到本表后，
 * 按「与服务端完全一致的下标顺序」用 itemId 校验后覆盖显示用的基础价，从而与服务端实际扣费保持一致。
 * We sync only {@code (itemId, price)} rather than whole entries because the behavioural parts
 * ({@code canDisplay/canBuy/onBuy}) are identical code on both sides; the only thing that diverges by
 * server config is the price. The client overrides its displayed base price by index (item-id verified),
 * keeping it consistent with what the server actually charges.
 *
 * <p>整张表会被规范化序列化（role id 升序）后做 SHA-256，得到一个稳定哈希，用于客户端本地缓存命中判断。
 * The whole table is serialized canonically (role ids sorted) and SHA-256'd into a stable hash used as
 * the client-side cache key.
 */
public final class ShopPriceTable {

    /** 单条商店条目的可同步部分。 / The syncable part of one shop entry. */
    public record Entry(String itemId, int price) {
    }

    /** role id(字符串) -> 该职业商店的有序条目列表。 / role id (string) -> ordered entries of that shop. */
    private final Map<String, List<Entry>> shops;

    private byte[] cachedBytes;
    private String cachedHash;

    public ShopPriceTable(Map<String, List<Entry>> shops) {
        this.shops = shops;
    }

    /** 取某职业的条目列表，没有则返回 {@code null}。 / Entries for a role, or {@code null}. */
    public List<Entry> get(String roleId) {
        return shops.get(roleId);
    }

    public Map<String, List<Entry>> shops() {
        return shops;
    }

    // ------------------------------------------------------------------
    // 构建（服务端/通用）/ Build (server/common)
    // ------------------------------------------------------------------

    /**
     * 从当前已注册的 {@link ShopContent} 构建价格表（枚举所有职业，按与显示端一致的解析规则展开）。
     * Build the table from the currently-registered {@link ShopContent}, enumerating every role and
     * resolving its shop the same way the display side does.
     */
    public static ShopPriceTable build() {
        Map<String, List<Entry>> shops = new LinkedHashMap<>();
        for (var roleEntry : TMMRoles.ROLES.entrySet()) {
            SRERole role = roleEntry.getValue();
            if (role == null) {
                continue;
            }
            List<ShopEntry> resolved = resolveEntries(role);
            if (resolved.isEmpty()) {
                continue;
            }
            List<Entry> entries = new ArrayList<>(resolved.size());
            for (ShopEntry entry : resolved) {
                String itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString();
                entries.add(new Entry(itemId, entry.price()));
            }
            shops.put(roleEntry.getKey().toString(), entries);
        }
        return new ShopPriceTable(shops);
    }

    /**
     * 与 {@code SREPlayerShopComponent#getShopEntries} / {@code LimitedInventoryScreen#getRoleShopEntries}
     * 完全一致的解析顺序：先取职业自身/自定义条目，空则在可用刀的职业上回退到默认刀商店。
     */
    private static List<ShopEntry> resolveEntries(SRERole role) {
        List<ShopEntry> entries = ShopContent.getShopEntries(role.getIdentifier());
        if (entries != null && !entries.isEmpty()) {
            return entries;
        }
        if (role.canUseKiller()) {
            return ShopContent.defaultKnifeEntries;
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // 序列化 / 哈希 / Serialization & hashing
    // ------------------------------------------------------------------

    /** 规范化序列化（role id 升序，条目保持下标顺序）；结果被缓存。 / Canonical serialization (cached). */
    public byte[] toBytes() {
        if (cachedBytes != null) {
            return cachedBytes;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            List<String> roleIds = new ArrayList<>(shops.keySet());
            Collections.sort(roleIds);
            buf.writeVarInt(roleIds.size());
            for (String roleId : roleIds) {
                buf.writeUtf(roleId);
                List<Entry> entries = shops.get(roleId);
                buf.writeVarInt(entries.size());
                for (Entry entry : entries) {
                    buf.writeUtf(entry.itemId());
                    buf.writeVarInt(entry.price());
                }
            }
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            cachedBytes = out;
            return out;
        } finally {
            buf.release();
        }
    }

    public static ShopPriceTable fromBytes(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            int roleCount = buf.readVarInt();
            Map<String, List<Entry>> shops = new LinkedHashMap<>();
            for (int i = 0; i < roleCount; i++) {
                String roleId = buf.readUtf();
                int entryCount = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(entryCount);
                for (int j = 0; j < entryCount; j++) {
                    String itemId = buf.readUtf();
                    int price = buf.readVarInt();
                    entries.add(new Entry(itemId, price));
                }
                shops.put(roleId, entries);
            }
            ShopPriceTable table = new ShopPriceTable(shops);
            table.cachedBytes = data;
            return table;
        } finally {
            buf.release();
        }
    }

    /** 整表的稳定 SHA-256 哈希（十六进制），用作客户端缓存 key。 / Stable SHA-256 hash (hex), the cache key. */
    public String hash() {
        if (cachedHash == null) {
            cachedHash = SceneAssetCodec.sha256(toBytes());
        }
        return cachedHash;
    }
}

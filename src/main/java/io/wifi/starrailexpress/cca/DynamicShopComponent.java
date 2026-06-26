package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态商店组件 / Dynamic shop component.
 *
 * <p>按玩家维度存储「商品价格修正」与「购买次数」，让局内商店的商品可以拥有动态价格
 * （折扣、按比例打折、固定减价、甚至溢价）。修正以物品 ID（{@link ResourceLocation}）为键，
 * 因此同一件商品在不同玩家身上可以有不同的实时价格。
 *
 * <p>Stores per-player price modifiers and purchase counts, keyed by item id, so in-game shop
 * items can have dynamic prices (percentage discounts, flat reductions, multipliers, or surge
 * pricing). The component auto-syncs to its owner, so the client shop UI can display the same
 * effective price the server will charge.
 *
 * <p>价格计算 / Price formula:
 * {@code effective = max(0, round(basePrice * multiplier) - flatReduction)}.
 *
 * @author canyuesama
 */
public class DynamicShopComponent implements RoleComponent {
    public static final ComponentKey<DynamicShopComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("dynamic_shop"),
            DynamicShopComponent.class);

    private final Player player;
    /** 物品 ID -> 价格修正 / item id -> price modifier */
    private final Map<ResourceLocation, PriceModifier> modifiers = new HashMap<>();
    /** 物品 ID -> 购买次数 / item id -> times purchased */
    private final Map<ResourceLocation, Integer> purchaseCounts = new HashMap<>();

    public DynamicShopComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.modifiers.clear();
        this.purchaseCounts.clear();
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 仅服务端：把改动同步给所有者客户端。 / Server-only: push changes to the owner client. */
    public void sync() {
        if (!this.player.level().isClientSide) {
            KEY.sync(this.player);
        }
    }

    // ------------------------------------------------------------------
    // 价格修正 API / Price-modifier API
    // ------------------------------------------------------------------

    /**
     * 设置百分比折扣。 / Set a percentage discount.
     *
     * @param item    物品 ID / item id
     * @param percent 折扣百分比（0~100），如 50 表示降价 50% / discount percent (0~100); 50 means -50%
     */
    public void setPercentDiscount(ResourceLocation item, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        setModifier(item, (100 - clamped) / 100.0, 0);
    }

    /**
     * 设置固定减价。 / Set a flat price reduction.
     *
     * @param amount 减少的价格 / amount to subtract from the price
     */
    public void setFlatReduction(ResourceLocation item, int amount) {
        setModifier(item, 1.0, Math.max(0, amount));
    }

    /**
     * 设置价格乘数（{@code < 1} 打折，{@code > 1} 溢价）。
     * Set a price multiplier ({@code < 1} discounts, {@code > 1} surge-prices).
     */
    public void setMultiplier(ResourceLocation item, double multiplier) {
        setModifier(item, Math.max(0.0, multiplier), 0);
    }

    /** 同时设置乘数与固定减价。 / Set both a multiplier and a flat reduction. */
    public void setModifier(ResourceLocation item, double multiplier, int flatReduction) {
        if (item == null) {
            return;
        }
        this.modifiers.put(item, new PriceModifier(Math.max(0.0, multiplier), Math.max(0, flatReduction)));
        this.sync();
    }

    /** 移除某件商品的价格修正。 / Remove the modifier for a single item. */
    public void clearModifier(ResourceLocation item) {
        if (this.modifiers.remove(item) != null) {
            this.sync();
        }
    }

    /** 清空所有价格修正（保留购买次数）。 / Clear all price modifiers (keeps purchase counts). */
    public void clearAllModifiers() {
        if (!this.modifiers.isEmpty()) {
            this.modifiers.clear();
            this.sync();
        }
    }

    public boolean hasModifier(ResourceLocation item) {
        return this.modifiers.containsKey(item);
    }

    /** 计算某个基础价格经过修正后的实际价格。 / Compute the effective price for a base price. */
    public int effectivePrice(ResourceLocation item, int basePrice) {
        PriceModifier modifier = this.modifiers.get(item);
        if (modifier == null) {
            return basePrice;
        }
        double price = basePrice * modifier.multiplier - modifier.flatReduction;
        return Math.max(0, (int) Math.round(price));
    }

    /** 便捷方法：根据商店条目的物品与基础价格计算实际价格。 / Convenience: resolve a {@link ShopEntry}'s effective price. */
    public int effectivePrice(@NotNull ShopEntry entry) {
        return effectivePrice(itemId(entry.stack()), entry.price());
    }

    // ------------------------------------------------------------------
    // 购买次数 API / Purchase-count API
    // ------------------------------------------------------------------

    /** 某件商品已被购买的次数。 / How many times an item has been purchased. */
    public int getPurchaseCount(ResourceLocation item) {
        return this.purchaseCounts.getOrDefault(item, 0);
    }

    /** 记录一次购买并返回更新后的次数。 / Record a purchase and return the new count. */
    public int recordPurchase(ResourceLocation item) {
        if (item == null) {
            return 0;
        }
        int next = getPurchaseCount(item) + 1;
        this.purchaseCounts.put(item, next);
        this.sync();
        return next;
    }

    private static ResourceLocation itemId(@NotNull ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    // ------------------------------------------------------------------
    // 同步 / Sync (round state only; not persisted to disk)
    // ------------------------------------------------------------------

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        CompoundTag modifiersTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, PriceModifier> entry : this.modifiers.entrySet()) {
            CompoundTag modifierTag = new CompoundTag();
            modifierTag.putDouble("mul", entry.getValue().multiplier);
            modifierTag.putInt("flat", entry.getValue().flatReduction);
            modifiersTag.put(entry.getKey().toString(), modifierTag);
        }
        tag.put("modifiers", modifiersTag);

        CompoundTag purchasesTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : this.purchaseCounts.entrySet()) {
            purchasesTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("purchases", purchasesTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.modifiers.clear();
        CompoundTag modifiersTag = tag.getCompound("modifiers");
        for (String key : modifiersTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            CompoundTag modifierTag = modifiersTag.getCompound(key);
            this.modifiers.put(id, new PriceModifier(modifierTag.getDouble("mul"), modifierTag.getInt("flat")));
        }

        this.purchaseCounts.clear();
        CompoundTag purchasesTag = tag.getCompound("purchases");
        for (String key : purchasesTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                this.purchaseCounts.put(id, purchasesTag.getInt(key));
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 仅同步局内状态，不写入磁盘 / round-only state, not persisted to disk
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /** 单件商品的价格修正。 / Price modifier for a single item. */
    private record PriceModifier(double multiplier, int flatReduction) {
    }
}

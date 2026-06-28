package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 杀手刀「有限耐久」行为的工具类。 / Helper for the killer knife's limited-durability behaviour.
 *
 * <p>这是 {@link io.wifi.starrailexpress.cca.DynamicShopComponent} 动态价格系统的一个示例落地：
 * 在 murder 或继承 murder 的模式下，杀手通过商店购买的刀只有 {@link #MAX_DURABILITY} 点耐久，
 * 每次成功捅人消耗 1 点；耐久耗尽后刀不会消失，但无法继续使用，需要重新购买替换。
 *
 * <p>This is the concrete example backing the {@link io.wifi.starrailexpress.cca.DynamicShopComponent}
 * dynamic-pricing system. In murder (or murder-derived) modes the killer's shop-bought knife has only
 * {@link #MAX_DURABILITY} durability; each successful stab consumes one point. When depleted the knife
 * does not disappear but can no longer be used until the player re-buys to replace it.
 *
 * <p>耐久通过逐栈的 {@code MAX_DAMAGE}/{@code DAMAGE} 数据组件实现（不修改物品注册），因此只影响被
 * 标记过的刀，其它来源的刀（如初始物品、其它模式）不受影响。 / Durability is stored via per-stack
 * {@code MAX_DAMAGE}/{@code DAMAGE} data components (no item-registration change), so it only affects
 * knives that were stamped; knives from other sources/modes are untouched.
 */
public final class KillerKnifeDurability {
    /** 杀手刀的耐久点数。 / Durability points of the killer knife. */
    public static final int MAX_DURABILITY = 3;

    private KillerKnifeDurability() {
    }

    /** 当前世界是否处于 murder 或继承 murder 的模式。 / Whether the world runs murder (or a murder-derived) mode. */
    public static boolean isMurderMode(@NotNull Level level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        return game != null && game.getGameMode() instanceof SREMurderGameMode;
    }

    /**
     * 有限耐久模式是否生效：需配置开关开启，且处于 murder（及继承）模式。
     * Whether limited durability is active: the config toggle is on AND the world runs a murder(-derived) mode.
     */
    public static boolean isDurabilityModeEnabled(@NotNull Level level) {
        return SREConfig.instance().knifeDurabilityMode && isMurderMode(level);
    }

    /** 该物品栈是否为被本系统标记了耐久的杀手刀。 / Whether the stack is a knife stamped with our durability marker. */
    public static boolean isMarkedKnife(@NotNull ItemStack stack) {
        return stack.is(TMMItems.KNIFE) && stack.has(DataComponents.MAX_DAMAGE);
    }

    /** 标记的刀耐久是否已耗尽。 / Whether a marked knife is fully depleted. */
    public static boolean isDepleted(@NotNull ItemStack stack) {
        return isMarkedKnife(stack) && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** 把一把刀重置为满耐久。 / Stamp/reset a knife stack to full durability. */
    public static void applyFreshDurability(@NotNull ItemStack stack) {
        stack.set(DataComponents.MAX_DAMAGE, MAX_DURABILITY);
        stack.set(DataComponents.DAMAGE, 0);
    }

    /**
     * 消耗一点耐久（封顶为最大值，永不破坏/移除物品）。
     * Consume one durability point (capped at max; never breaks or removes the item).
     *
     * @return 消耗后是否已耗尽 / whether the knife is depleted after this consumption
     */
    public static boolean consumeOne(@NotNull ItemStack stack) {
        if (!isMarkedKnife(stack)) {
            return false;
        }
        int next = Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1);
        stack.setDamageValue(next);
        return next >= stack.getMaxDamage();
    }

    /**
     * 在玩家背包中查找一把「耐久耗尽」的杀手刀。 / Find a depleted killer knife in the player's inventory.
     *
     * @return 已耗尽的刀，没有则返回 {@code null} / the depleted knife, or {@code null} if none
     */
    @Nullable
    public static ItemStack findDepletedKnife(@NotNull Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isDepleted(stack)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 把背包内所有「耐久耗尽」的杀手刀整理为最多一把满耐久：原地刷新第一把，清除其余多余的耗尽刀，
     * 避免玩家重复购买后背包残留没耐久的刀。
     * Refresh the first depleted killer knife to full durability in place and remove every other depleted
     * knife, so re-buying never leaves a leftover unusable knife in the inventory.
     *
     * @return 是否找到并刷新了至少一把耗尽的刀 / whether at least one depleted knife was found and refreshed
     */
    public static boolean refreshDepletedKnives(@NotNull Player player) {
        var inventory = player.getInventory();
        ItemStack refreshed = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !isDepleted(stack)) {
                continue;
            }
            if (refreshed == null) {
                applyFreshDurability(stack);
                refreshed = stack;
            } else {
                // 清除多余的耗尽刀，确保背包内只保留一把刀。 / clear extra depleted knives, keeping only one.
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return refreshed != null;
    }
}

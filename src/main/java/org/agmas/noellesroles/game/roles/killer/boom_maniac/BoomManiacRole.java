package org.agmas.noellesroles.game.roles.killer.boom_maniac;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.Nullable;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 爆炸狂（杀手阵营，与护士绑定生成）。
 *
 * <p>职业规则全部集中在本类：
 * <ul>
 * <li>初始物品：一把弩；</li>
 * <li>商店：60 金币购买飞行时间为 3 的小型球状烟花火箭，
 *     其余为通用杀手商店去掉刀 / 左轮手枪 / 短管霰弹枪 / 疯狂模式（价格沿用配置项）；</li>
 * <li>技能：将主手物品切换至副手（参考网警，见 {@link #swapHeldItem(ServerPlayer)}）。</li>
 * </ul>
 */
public class BoomManiacRole extends NormalRole {

    /** 烟花火箭价格（金币）。 */
    public static final int FIREWORK_PRICE = 60;
    /** 烟花火箭飞行时间（Fireworks.Flight）。 */
    public static final int FIREWORK_FLIGHT = 3;

    public BoomManiacRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 初始物品：一把弩。 */
    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(new ItemStack(Items.CROSSBOW));
    }

    /**
     * 专属商店：小型球状烟花火箭（60 金币）+ 通用杀手商店条目
     * （去掉刀 / 左轮手枪 / 短管霰弹枪 / 疯狂模式，其余条目价格沿用配置项）。
     */
    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(createBoomFirework(), FIREWORK_PRICE, ShopEntry.Type.TOOL));

        for (ShopEntry entry : ShopContent.getDefaultKnifeEntries()) {
            if (isRemovedKillerEntry(entry)) {
                continue;
            }
            shop.add(entry);
        }
        return shop;
    }

    /** 通用杀手商店中本职业不卖的条目：刀 / 左轮手枪 / 短管霰弹枪 / 疯狂模式。 */
    private static boolean isRemovedKillerEntry(ShopEntry entry) {
        if (entry instanceof KillerKnifeShopEntry) {
            return true; // 刀（动态条目）
        }
        Item item = entry.stack().getItem();
        return item == TMMItems.REVOLVER          // 左轮手枪
                || item == ModItems.SHORT_SHOTGUN // 短管霰弹枪
                || item == TMMItems.PSYCHO_MODE;  // 疯狂模式
    }

    /** 飞行时间 3 的小型球状烟花火箭（参考网警商店的烟花构造）。 */
    public static ItemStack createBoomFirework() {
        ItemStack rocket = Items.FIREWORK_ROCKET.getDefaultInstance();
        rocket.set(DataComponents.FIREWORKS, new Fireworks(FIREWORK_FLIGHT, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                        new IntArrayList(new int[] { 0xFFFFFF }),
                        new IntArrayList(), false, false))));
        return rocket;
    }

    /**
     * 技能：将主手物品切换至副手（参考网警技能）。
     * 副手原有物品会放回快捷栏；主副手都为空、或背包没有空位时技能不生效。
     *
     * @return true = 切换成功（消耗冷却）
     */
    public static boolean swapHeldItem(ServerPlayer player) {
        if (player.isSpectator()
                || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        int selectedHotbarSlot = player.getInventory().selected;
        ItemStack mainHand = player.getInventory().getItem(selectedHotbarSlot).copy();
        ItemStack offHand = player.getOffhandItem().copy();
        if (mainHand.isEmpty() && offHand.isEmpty()) {
            return false;
        }

        if (!offHand.isEmpty()) {
            // 与网警技能相同：先清空当前手上的物品，再把副手物品插入快捷栏。
            // 这样即使两者是同一种物品，也不会先合并到主手后再被清空。
            if (!RoleUtils.isPlayerHasFreeSlot(player)) {
                return false;
            }
            player.getInventory().setItem(selectedHotbarSlot, ItemStack.EMPTY);
            if (!RoleUtils.insertStackInFreeSlot(player, offHand)) {
                player.getInventory().setItem(selectedHotbarSlot, mainHand);
                return false;
            }
        } else {
            player.getInventory().setItem(selectedHotbarSlot, ItemStack.EMPTY);
        }

        player.getInventory().offhand.set(0, mainHand);
        player.getInventory().setChanged();
        player.inventoryMenu.slotsChanged(player.getInventory());
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        return true;
    }
}

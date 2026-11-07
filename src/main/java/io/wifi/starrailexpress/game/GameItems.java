/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import io.wifi.starrailexpress.event.ShouldDropOnDeath;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.content.item.DesperadoGunItem;
import org.agmas.noellesroles.content.item.LetterItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static io.wifi.starrailexpress.game.GameUtils.cooldownItems;

/** 安全时间冷却、死亡掉落和暗器弹药，不持有第二份物品缓存。 */
final class GameItems {
    private GameItems() {
    }

    static void addItemCooldowns(ServerLevel world, int time) {
        if (cooldownItems.isEmpty()) {
            BuiltInRegistries.ITEM.forEach(item -> {
                // 共用 CUSTOM_ITEM 的每个自定义 id 有独立冷却键，不能对注册物品整体加冷却。
                if (item == io.wifi.starrailexpress.index.DevItems.CUSTOM_ITEM) return;
                if (!(item instanceof LetterItem)) {
                    String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                    if (namespace.equals(StarRailExpressID.MOD_ID) || namespace.equals(StarRailExpressID.STUPIDEXPRESS)
                            || namespace.equals(StarRailExpressID.NOELLESROLES_ROLE)) cooldownItems.add(item);
                }
            });
        }
        for (ServerPlayer player : world.players()) {
            var cooldowns = player.getCooldowns();
            var items = new ArrayList<>(MCItemsUtils.getItemsByTag(player.serverLevel(), TMMItemTags.GUNS));
            items.forEach(item -> cooldowns.addCooldown(item, time));
            cooldowns.addCooldown(Items.BOW, time);
            cooldowns.addCooldown(Items.CLOCK, time);
            cooldowns.addCooldown(Items.TRIDENT, time);
            cooldowns.addCooldown(TMMItems.MONITOR_BROKEN, time);
            cooldowns.addCooldown(ModItems.SHORT_SHOTGUN, time);
            cooldowns.addCooldown(TMMItems.GRENADE, time);
            cooldowns.addCooldown(TMMItems.PSYCHO_MODE, time);
            cooldowns.addCooldown(TMMItems.NUNCHUCK, time);
            cooldowns.addCooldown(TMMItems.BAT, time);
            cooldowns.addCooldown(TMMItems.KNIFE, time);
            cooldowns.addCooldown(TMMItems.SNIPER_RIFLE, time);
            cooldowns.addCooldown(TMMItems.BLACKOUT, time);
            cooldownItems.forEach(item -> cooldowns.addCooldown(item, time));
            CustomItemRuntime.applySafeTimeCooldown(player, time);
            if (!player.hasEffect(ModEffects.SAFE_TIME)) {
                player.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, time, 0, true, false, false));
            }
            if (!player.hasEffect(ModEffects.SKILL_BANED)) {
                player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, time, 0, true, false, false));
            }
        }
    }

    static boolean shouldDropOnDeath(@NotNull ItemStack stack) {
        return !stack.isEmpty() && (stack.is(TMMItems.REVOLVER) || stack.is(Items.SPYGLASS)
                || ShouldDropOnDeath.EVENT.invoker().shouldDrop(stack));
    }

    static boolean haveUsedoutDerringer(Player player) {
        for (List<ItemStack> list : player.getInventory().compartments) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (DesperadoGunItem.isDerringerWeapon(stack)) return stack.getOrDefault(SREDataComponentTypes.USED, false);
            }
        }
        return false;
    }

    static boolean refillDerringer(Player player, boolean haveBullet) {
        for (List<ItemStack> list : player.getInventory().compartments) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (DesperadoGunItem.isDerringerWeapon(stack)) {
                    stack.set(SREDataComponentTypes.USED, !haveBullet);
                    return true;
                }
            }
        }
        return false;
    }
}

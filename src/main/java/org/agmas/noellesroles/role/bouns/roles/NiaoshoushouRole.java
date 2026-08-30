/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.NiaoshoushouRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 鸟兽兽：使用火焰、巡飞弹与掩体猎杀目标的独立杀手职业。 */
public class NiaoshoushouRole extends EggRole {

    public NiaoshoushouRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean onUseKnifeHit(Player player, Player target) {
        return player.getMainHandItem().is(ModItems.NIAOSHOU_SHOU_KNIFE)
                && target != player
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target);
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
        super.onKill(victim, spawnBody, killer, deathReason);
        if (!(killer instanceof ServerPlayer serverPlayer)
                || serverPlayer.isCreative()
                || !serverPlayer.getMainHandItem().is(ModItems.NIAOSHOU_SHOU_KNIFE)) {
            return;
        }

        ItemStack knife = serverPlayer.getMainHandItem();
        knife.hurtAndBreak(1, serverPlayer, EquipmentSlot.MAINHAND);
        serverPlayer.getCooldowns().addCooldown(ModItems.NIAOSHOU_SHOU_KNIFE,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.NIAOSHOU_SHOU_KNIFE.getDefaultInstance(), 135, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.NIAOSHOU_SHOU_INCENDIARY_GRENADE.getDefaultInstance(), 275,
                ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.NIAOSHOU_SHOU_FAMILY_PHOTO.getDefaultInstance(), 100,
                ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                NiaoshoushouRoleData data = NiaoshoushouRoleData.get(player);
                return data != null && !data.isFamilyPhotoPurchased() && super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                NiaoshoushouRoleData data = NiaoshoushouRoleData.get(player);
                if (data == null || data.isFamilyPhotoPurchased()) {
                    return false;
                }
                if (!super.onBuy(player)) {
                    return false;
                }
                data.markFamilyPhotoPurchased();
                return true;
            }
        });
        shop.add(new ShopEntry(ModItems.NIAOSHOU_SHOU_MISSILE.getDefaultInstance(), 350,
                ShopEntry.Type.WEAPON));
        // 还是给个贵的要死的开锁器吧。。。
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 300,
                ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(ModItems.AREA_BLACKOUT.getDefaultInstance(), 175,
                ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(ModItems.AREA_BLACKOUT)) {
                    return false;
                }
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
                if (blackout.isBlackoutActive()) {
                    return false;
                }
                blackout.triggerBlackout(player.blockPosition(),
                        NoellesRolesConfig.HANDLER.instance().dreamBlackoutRadius, true,
                        SREWorldBlackoutComponent.getMaxDuration(player.level()));
                // 冷却与普通关灯一致
                player.getCooldowns().addCooldown(ModItems.AREA_BLACKOUT, 120 * 20);
                return true;
            }
        });
        return shop;
    }
}

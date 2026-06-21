package org.agmas.noellesroles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.game.roles.innocent.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.UUID;

public class AbilityHandler {

    public static void handler(ServerPlayer player) {
        // 通用技能服务端处理
        if (player.isSpectator())
            return;
        SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                .get(player);
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        if (player.hasEffect(ModEffects.TIME_STOP) && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }
        if (SpellbreakerPlayerComponent.consumePendingSkillFail(player)) {
            return;
        }
        if (player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.GLITCH_ROBOT)) {
            if (!RoleUtils.isPlayerHasFreeSlot(player)) {
                player.displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES)) {
                player.displayClientMessage(
                        Component.translatable("info.glitch_robot.noglasses_on_head").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            RoleUtils.insertStackInFreeSlot(player, player.getSlot(103).get().copy());
            // RoleUtils.removeStackItem(player, 103);
            player.getInventory().armor.set(3, ItemStack.EMPTY);
            player.displayClientMessage(
                    Component.translatable("info.glitch_robot.take_off_glasses.success")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            player.removeEffect(MobEffects.NIGHT_VISION);
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.DIVER)) {
            if (!RoleUtils.isPlayerHasFreeSlot(player)) {
                player.displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
                return;
            }

            boolean removedAny = false;

            // 检查并移除头盔
            ItemStack headItem = player.getSlot(103).get();
            if (!headItem.isEmpty()) {
                RoleUtils.insertStackInFreeSlot(player, headItem.copy());
                player.getInventory().armor.set(3, ItemStack.EMPTY);
                removedAny = true;
            }

            // 检查并移除靴子
            ItemStack feetItem = player.getSlot(100).get();
            if (!feetItem.isEmpty()) {
                RoleUtils.insertStackInFreeSlot(player, feetItem.copy());
                player.getInventory().armor.set(0, ItemStack.EMPTY);
                removedAny = true;
            }

            if (removedAny) {
                player.displayClientMessage(
                        Component.translatable("info.diver.remove_equipment.success")
                                .withStyle(ChatFormatting.GREEN),
                        true);
                player.removeEffect(MobEffects.WATER_BREATHING);
                player.removeEffect(MobEffects.DOLPHINS_GRACE);
            } else {
                player.displayClientMessage(
                        Component.translatable("info.diver.no_equipment")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return;
        }

        if (gameWorldComponent.isRole(player, ModRoles.RECALLER)
                && abilityPlayerComponent.cooldown <= 0) {
            RecallerPlayerComponent recallerPlayerComponent = RecallerPlayerComponent.KEY.get(player);
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
            if (!recallerPlayerComponent.placed) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerMarkCooldown);
                recallerPlayerComponent.setPosition();
            } else if (playerShopComponent.balance >= 100) {
                playerShopComponent.balance -= 100;
                playerShopComponent.sync();
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerTeleportCooldown);
                recallerPlayerComponent.teleport();
            }

        }
        if (gameWorldComponent.isRole(player, ModRoles.OLDMAN)) {
            if (player.getVehicle() != null && player.getVehicle() instanceof WheelchairEntity we) {
                if (player.getCooldowns().isOnCooldown(ModItems.WHEELCHAIR)) {
                    return;
                }
                var chairDurability = we.durability;
                we.discard();
                var it = ModItems.WHEELCHAIR.getDefaultInstance();
                it.setDamageValue(it.getMaxDamage() - chairDurability);
                RoleUtils.insertStackInFreeSlot(player, it);
                player.stopRiding();
                player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
                player.displayClientMessage(
                        Component.translatable("message.oldman.get_back").withStyle(ChatFormatting.GOLD), true);
            }
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.IMITATOR)) {
            ImitatorPlayerComponent comp = ModComponents.IMITATOR.get(player);
            if (player.isShiftKeyDown()) {
                comp.switchSlot();
            } else {
                comp.useActiveAbility(player, null);
            }
            return;
        }
        // 处理超级亡命徒技能
    }

    public static void handlerWithTarget(ServerPlayer player, UUID targetUUID) {
        if (player.isSpectator())
            return;
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        if (player.hasEffect(ModEffects.TIME_STOP) && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }
        if (SpellbreakerPlayerComponent.consumePendingSkillFail(player)) {
            return;
        }
        if (player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.IMITATOR)) {
            ImitatorPlayerComponent comp = ModComponents.IMITATOR.get(player);
            if (comp.isCopyMode) {
                comp.tryCopyAbility(player, targetUUID);
            } else {
                comp.useActiveAbility(player, targetUUID);
            }
            return;
        }
    }
}

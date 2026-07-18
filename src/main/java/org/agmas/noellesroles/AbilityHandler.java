package org.agmas.noellesroles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.game.roles.innocence.jade_general.JadeGeneralPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.UUID;

public class AbilityHandler {

    public static void handler(ServerPlayer player) {
        handler(player, false);
    }

    /**
     * 通用技能服务端处理。
     *
     * @param possessed 若为 true，则跳过 {@link ModEffects#SKILL_BANED} 拦截
     *                  （用于操纵师附身时以目标身份释放目标技能）。
     */
    public static void handler(ServerPlayer player, boolean possessed) {
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
        if (!possessed && player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.EXAMPLER)) {
            if (abilityPlayerComponent.hasCooldown()) {
                return;
            }
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < 300) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds_money", 300)
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            shop.addToBalance(-300);
            player.serverLevel().players().forEach(sp -> {
                if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                    ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 3));
                }
            });
            abilityPlayerComponent.setCooldown(240 * 20);
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

        if (gameWorldComponent.isRole(player, ModRoles.LEON)
                && abilityPlayerComponent.cooldown <= 0) {
            // 格斗体术：向面前玩家猛踹一脚，造成较远击退与减速
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            net.minecraft.world.phys.HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil
                    .getHitResultOnViewVector(player,
                            e -> e instanceof ServerPlayer p
                                    && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(p),
                            cfg.leonKickRange);
            if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr
                    && ehr.getEntity() instanceof ServerPlayer victim) {
                victim.knockback(cfg.leonKickKnockback,
                        player.getX() - victim.getX(), player.getZ() - victim.getZ());
                victim.hurtMarked = true;
                // 玩家受服务端击退需主动同步速度
                victim.connection
                        .send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(victim));
                int slowTicks = (int) (cfg.leonKickSlowSeconds * 20);
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 2));
                player.level().playSound(null, victim.blockPosition(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0, cfg.leonKickCooldown);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.leon.kick_hit")
                                .withStyle(ChatFormatting.AQUA),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.leon.kick_miss")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
            return;
        }

        if (gameWorldComponent.isRole(player, ModRoles.MORPHLING)
                && abilityPlayerComponent.cooldown <= 0) {
            // 召唤举刀假人向前突进
            if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent morphComp = org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent.KEY
                    .get(player);
            // 从所有存活玩家中随机选择一个作为皮肤（排除召唤者自身）
            List<ServerPlayer> aliveOthers = level.players().stream()
                    .filter(p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.getUUID().equals(player.getUUID()))
                    .toList();
            UUID skin;
            if (!aliveOthers.isEmpty()) {
                skin = aliveOthers.get(level.random.nextInt(aliveOthers.size())).getUUID();
            } else {
                // 无人可选时 fallback 到伪装对象或自身
                skin = (morphComp.morphTicks > 0 && morphComp.disguise != null)
                        ? morphComp.disguise
                        : player.getUUID();
            }
            float yaw = player.getYRot();
            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);
            org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity dummy = new org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity(
                    org.agmas.noellesroles.init.ModEntities.MORPHLING_KNIFE_DUMMY, level);
            dummy.setPos(player.getX() + dx * 1.5D, player.getY(), player.getZ() + dz * 1.5D);
            dummy.setup(player, skin, GameConstants.getInTicks(0, cfg.morphlingDummyLifetime), yaw);
            level.addFreshEntity(dummy);
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
            abilityPlayerComponent.cooldown = GameConstants.getInTicks(0, cfg.morphlingDummyCooldown);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.morphling.dummy_spawned")
                            .withStyle(ChatFormatting.GREEN),
                    true);
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
        if (gameWorldComponent.isRole(player, ModRoles.JADE_GENERAL)
                && abilityPlayerComponent.cooldown <= 0) {
            JadeGeneralPlayerComponent jadeGeneral = ModComponents.JADE_GENERAL.get(player);
            if (jadeGeneral.useSkill()) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0, 35);
                abilityPlayerComponent.sync();
            }
            return;
        }
        if (isGhostEyeRole(gameWorldComponent, player)
                && abilityPlayerComponent.cooldown <= 0) {
            GhostEyePlayerComponent ghostEye = ModComponents.GHOST_EYE.get(player);
            if (ghostEye.deployDomain()) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().ghostEyeDomainCooldown);
                abilityPlayerComponent.sync();
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ghost_eye.domain_deployed")
                                .withStyle(ChatFormatting.DARK_AQUA),
                        true);
            }
            return;
        }
        // 滞时鬼（Delayer）已迁移至统一技能系统（见 ModRolesInitialEventRegister），
        // 通过 RoleSkill.useUnified 分发并显示 HUD，此处不再单独处理。
        if (gameWorldComponent.isRole(player, ModRoles.WIZARD)) {
            WizardPlayerComponent wizard = ModComponents.WIZARD.get(player);
            wizard.castSelectedSpell();
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.RAVEN)) {
            RavenPlayerComponent raven = ModComponents.RAVEN.get(player);
            if (raven.isHunting()) {
                raven.returnFromHunt();
            } else {
                raven.useAbility();
            }
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.CAKE_MAKER)) {
            ModComponents.CAKE_MAKER.get(player).useSmoker();
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.ADVENTURER)) {
            ModComponents.ADVENTURER.get(player).useWaypointAbility();
            return;
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
        if (gameWorldComponent.isRole(player, ModRoles.NOSTALGIST)) {
            // 里世界中按技能键：主动让里世界崩塌并现身
            ModComponents.NOSTALGIST.get(player).tryManualCollapse(player);
            return;
        }
        // 处理超级亡命徒技能
    }

    public static void handlerWithTarget(ServerPlayer player, UUID targetUUID) {
        handlerWithTarget(player, targetUUID, false);
    }

    public static void handlerWithTarget(ServerPlayer player, UUID targetUUID, boolean possessed) {
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
        if (!possessed && player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.EXAMPLER)) {
            if (targetUUID == null)
                return;

            if (abilityPlayerComponent.hasCooldown()) {
                return;
            }
            Player target = player.level().getPlayerByUUID(targetUUID);
            if (!(target instanceof ServerPlayer sp))
                return;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < 100) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            shop.addToBalance(-100);
            ServerPlayNetworking.send(player, new ProblemScreenOpenC2SPacket(true, 2));
            ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 2));
            abilityPlayerComponent.setCooldown(90 * 20);
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

    private static boolean isGhostEyeRole(SREGameWorldComponent gameWorldComponent, Player player) {
        if (gameWorldComponent == null || player == null)
            return false;
        var role = gameWorldComponent.getRole(player);
        return role != null && role.identifier().equals(ModRoles.GHOST_EYE_ID);
    }
}

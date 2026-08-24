/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.role_data.vigilante.GhostEyeRoleData;
import org.agmas.noellesroles.role_data.innocence.AdventurerRoleData;
import org.agmas.noellesroles.role_data.innocence.JadeGeneralRoleData;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.role_data.killer.ImitatorRoleData;
import org.agmas.noellesroles.role_data.neutral.RavenRoleData;
import org.agmas.noellesroles.role_data.innocence.RecallerRoleData;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;
import org.agmas.noellesroles.role_data.killer.NostalgistRoleData;
import org.agmas.noellesroles.role_data.killer.SpellbreakerRoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role_data.vigilante.HoanMeirinRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.UUID;

public class AbilityHandler {

    public static void handler(ServerPlayer player) {
        handler(player, false);
    }

    /**
     * 在踢击者前方锥形范围内寻找最近的存活玩家目标。
     *
     * <p>
     * 相比射线检测（{@code getHitResultOnViewVector}），锥形检测在贴脸/近距离时更稳定，
     * 不会因准星未精确对上目标碰撞箱而踢空。
     *
     * @param player 踢击者
     * @param range  水平检测半径（格）
     * @return 最近的合法目标，若无则为 {@code null}
     */
    private static ServerPlayer findKickTarget(ServerPlayer player, double range) {
        net.minecraft.world.phys.Vec3 self = player.position();
        float yawRad = (float) Math.toRadians(player.getYRot());
        // 由 yaw 计算的水平前向单位向量，不受俯仰角影响
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(
                -Math.sin(yawRad), 0, Math.cos(yawRad));
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : player.serverLevel().players()) {
            if (p == player || !GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            double dx = p.getX() - self.x;
            double dz = p.getZ() - self.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range)
                continue;
            // 竖直方向限制，避免踢到楼上/楼下的人
            if (Math.abs(p.getY() - self.y) > 2.0)
                continue;
            // 贴脸（dist 极小）时跳过朝向判定直接命中；否则要求目标在前方锥形内
            if (dist > 1.0e-4) {
                double dot = (forward.x * dx + forward.z * dz) / dist;
                if (dot < 0.25D)
                    continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    /**
     * 返回踢击者的水平视线单位方向；若正对天/地（水平分量近 0），则退化为指向目标的方向。
     */
    private static net.minecraft.world.phys.Vec3 horizontalLookDirection(ServerPlayer player,
            ServerPlayer target) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        double dx = look.x;
        double dz = look.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            dx = target.getX() - player.getX();
            dz = target.getZ() - player.getZ();
            len = Math.sqrt(dx * dx + dz * dz);
        }
        if (len < 1.0e-4) {
            // 完全重合时给一个默认方向，避免除零
            return new net.minecraft.world.phys.Vec3(0, 0, 1);
        }
        return new net.minecraft.world.phys.Vec3(dx / len, 0, dz / len);
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
        if (SpellbreakerRoleData.consumePendingSkillFail(player)) {
            return;
        }
        if (!possessed && player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, THRedHouseRoles.HOAN_MEIRIN)) {
            var cca = RoleData.getOrCreate(HoanMeirinRoleData.class, player);
            if (cca != null) {
                if (player.hasEffect(MobEffects.LEVITATION)) {
                    player.removeEffect(MobEffects.LEVITATION);
                    player.displayClientMessage(
                            Component.translatable("hud.hoan_meirin.ability_stop").withStyle(ChatFormatting.AQUA),
                            true);
                    return;
                }
                if (cca.cooldown > 0) {
                    return;
                }
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                        10 * 20, 1, true, false, true));
                player.displayClientMessage(
                        Component.translatable("hud.hoan_meirin.ability_activated").withStyle(ChatFormatting.GREEN),
                        true);
                cca.setCooldown(60 * 20);
                ConfigWorldComponent.onPlayerUsedSkill(player);
            }

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
            abilityPlayerComponent.setCooldown(180 * 20);
            ConfigWorldComponent.onPlayerUsedSkill(player);
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
            // 播放踢击动画（挥击），无论是否命中都会播放
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            // 使用锥形最近目标检测：近距离也能稳定命中，避免射线在贴脸时踢空
            ServerPlayer victim = findKickTarget(player, cfg.leonKickRange);
            if (victim != null) {
                // 以踢击者的视线水平方向击退：贴脸时也有稳定方向，不会因两点重合而乱飞
                net.minecraft.world.phys.Vec3 dir = horizontalLookDirection(player, victim);
                // knockback(strength, x, z) 会把目标推向 -(x, z)，故传入反方向
                victim.knockback(cfg.leonKickKnockback, -dir.x, -dir.z);
                // 踹人击退的竖直分量削减 60%，避免把人踢飞上天
                net.minecraft.world.phys.Vec3 kickVel = victim.getDeltaMovement();
                victim.setDeltaMovement(kickVel.x, kickVel.y * 0.4D, kickVel.z);
                victim.setLastHurtByPlayer(player);
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
                abilityPlayerComponent.setCooldown(GameConstants.getInTicks(0, cfg.leonKickCooldown));
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.leon.kick_hit")
                                .withStyle(ChatFormatting.AQUA),
                        true);
                ConfigWorldComponent.onPlayerUsedSkill(player);
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
            MorphlingRoleData morphComp = RoleData.getNullable(MorphlingRoleData.class, player);
            // 从所有存活玩家中随机选择一个作为皮肤（排除召唤者自身）
            List<ServerPlayer> aliveOthers = level.players().stream()
                    .filter(p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.getUUID().equals(player.getUUID()))
                    .toList();
            UUID skin;
            if (!aliveOthers.isEmpty()) {
                skin = aliveOthers.get(level.random.nextInt(aliveOthers.size())).getUUID();
            } else {
                // 无人可选时 fallback 到伪装对象或自身
                skin = (morphComp != null && morphComp.morphTicks > 0 && morphComp.disguise != null)
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
            abilityPlayerComponent.setCooldown(GameConstants.getInTicks(0, cfg.morphlingDummyCooldown));
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.morphling.dummy_spawned")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            ConfigWorldComponent.onPlayerUsedSkill(player);
            return;
        }

        if (gameWorldComponent.isRole(player, ModRoles.RECALLER)
                && abilityPlayerComponent.cooldown <= 0) {
            RecallerRoleData recallerPlayerComponent = RoleData.getNullable(RecallerRoleData.class, player);
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
            if (recallerPlayerComponent != null && !recallerPlayerComponent.placed) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerMarkCooldown);
                recallerPlayerComponent.setPosition();
            } else if (recallerPlayerComponent != null && playerShopComponent.balance >= 100) {
                playerShopComponent.balance -= 100;
                playerShopComponent.sync();
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerTeleportCooldown);
                recallerPlayerComponent.teleport();
            }
            abilityPlayerComponent.sync();
        }
        if (gameWorldComponent.isRole(player, ModRoles.JADE_GENERAL)
                && abilityPlayerComponent.cooldown <= 0) {
            boolean used = RoleData.getOptional(JadeGeneralRoleData.class, player)
                    .map(JadeGeneralRoleData::useSkill).orElse(false);
            if (used) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0, 35);
                abilityPlayerComponent.sync();
                ConfigWorldComponent.onPlayerUsedSkill(player);
            }
            return;
        }
        if (isGhostEyeRole(gameWorldComponent, player)
                && abilityPlayerComponent.cooldown <= 0) {
            var ghostEye = RoleData.getNullable(GhostEyeRoleData.class, player);
            if (ghostEye != null && ghostEye.deployDomain()) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().ghostEyeDomainCooldown);
                abilityPlayerComponent.sync();
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ghost_eye.domain_deployed")
                                .withStyle(ChatFormatting.DARK_AQUA),
                        true);
                ConfigWorldComponent.onPlayerUsedSkill(player);
            }
            return;
        }
        // 滞时鬼（Delayer）已迁移至统一技能系统（见 ModRolesInitialEventRegister），
        // 通过 RoleSkill.useUnified 分发并显示 HUD，此处不再单独处理。
        if (gameWorldComponent.isRole(player, ModRoles.WIZARD)) {
            RoleData.getOptional(WizardRoleData.class, player)
                    .ifPresent(WizardRoleData::castSelectedSpell);
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.RAVEN)) {
            RavenRoleData raven = RoleData.getNullable(RavenRoleData.class, player);
            if (raven != null && raven.isHunting()) {
                raven.returnFromHunt();
            } else if (raven != null) {
                raven.useAbility();
            }
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.CAKE_MAKER)) {
            ModComponents.CAKE_MAKER.get(player).useSmoker();
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.ADVENTURER)) {
            RoleData.getOptional(AdventurerRoleData.class, player).ifPresent(AdventurerRoleData::useWaypointAbility);
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
            var comp = RoleData.getNullable(ImitatorRoleData.class, player);
            if (comp == null) {
                return;
            }
            if (player.isShiftKeyDown()) {
                comp.switchSlot();
            } else {
                comp.useActiveAbility(player, null);
            }
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.NOSTALGIST)) {
            // 里世界中按技能键：主动让里世界崩塌并现身
            var nostData = RoleData.getNullable(NostalgistRoleData.class, player);
            if (nostData != null) {
                nostData.tryManualCollapse(player);
            }
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
        if (SpellbreakerRoleData.consumePendingSkillFail(player)) {
            return;
        }
        if (!possessed && player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, THMiscRoles.DOREMY)) {
            if (targetUUID == null)
                return;
            var cca = RoleData.getNullable(DoremyRoleData.class, player);
            if (cca == null || cca.cooldownForDoremyDream > 0) {
                return;
            }
            Player target = player.level().getPlayerByUUID(targetUUID);
            if (!(target instanceof ServerPlayer sp))
                return;
            if (DoremyRoleData.tryDream(sp, 30 * 20)) {
                player.displayClientMessage(
                        Component.translatable("skill.noellesroles.doremy_dream.success", sp.getName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
                cca.cooldownForDoremyDream = 120 * 20;
                cca.sync();
            } else {

                player.displayClientMessage(
                        Component.translatable("skill.noellesroles.doremy_dream.failed", sp.getName())
                                .withStyle(ChatFormatting.RED),
                        true);
            }
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
            // 回放记录：小镇做题家发放习题
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.testmaker.assign_exam",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true),
                            GameReplayUtils.getReplayPlayerDisplayText(sp, true)));
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.IMITATOR)) {
            var comp = RoleData.getNullable(ImitatorRoleData.class, player);
            if (comp == null) {
                return;
            }
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

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

package org.agmas.noellesroles.handler;

import org.agmas.noellesroles.handler.utils.BeeFamilyManager;
import org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.bouns.roles.HengXingTiRole;
import org.agmas.noellesroles.role.bouns.roles.RabbitWansuiRole;
import org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

public class BounsHandlers {

    public static void register() {
        BeeFamilyManager.registerEvents();
        LinFamilyRoleData.registerEvents();
        RabbitWansuiRole.registerEvents();
        RoleSkill.register(BounsRoles.HENG_XING_TI,
                RoleSkill.skill(SRE.id("heng_xing_ti"), "skill.noellesroles.heng_xing_ti", (ctx) -> {
                    return HengXingTiRole.triggerSkill(ctx);
                }).showOnHud(true)
                        .recordReplay().cooldownSeconds(240).announceToSelf().build());
        // 避免恒星体导致玩家落入虚空
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReasosn, forceKill) -> {
            if (killer == null || RoleUtils.isPlayerTheJob(killer, BounsRoles.HENG_XING_TI)) {
                if (ForensicCategory.fromDeathReason(deathReasosn).equals(ForensicCategory.ENVIRONMENT)) {
                    if (victim.getKillCredit() instanceof ServerPlayer sp) {
                        if (RoleUtils.isPlayerTheJob(sp, BounsRoles.HENG_XING_TI)) {
                            victim.setLastHurtByMob(null);
                            victim.setLastHurtByPlayer(null);
                            GameUtils.teleportBackToRoom(victim);
                            return TrueFalseResult.FALSE;
                        }
                    }
                }
            }
            return TrueFalseResult.PASS;
        });
        // 领袖对恒星体释放技能后：恒星体技能冷却减半（OnRoleSkillUse.AFTER 在 markSkillUsed 之后触发，可覆盖完整冷却）
        OnRoleSkillUse.AFTER.register((player, role) -> {
            if (LeaderFollowerEffects.HENG_XING_TI_COOLDOWN_HALVED
                    && BounsRoles.HENG_XING_TI.equals(role)
                    && player instanceof ServerPlayer sp) {
                SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(sp);
                for (var def : RoleSkill.getDefinitions(role)) {
                    ability.setSkillCooldown(def.id(), def.cooldownTicks() / 2);
                }
            }
            return true;
        });

        RoleSkill.register(BounsRoles.LAO_DA,
                RoleSkill.skill(SRE.id("lao_da"), "skill.noellesroles.lao_da.zhouji", (ctx) -> {
                    final var serverPlayer = ctx.player();
                    // 坚守者式冲击波：眩晕（定身 + 禁止背包 + 禁止使用）并击退正前方扇形内的玩家
                    Vec3 lookFlat = new Vec3(serverPlayer.getLookAngle().x, 0, serverPlayer.getLookAngle().z);
                    if (lookFlat.lengthSqr() > 1.0e-4) {
                        lookFlat = lookFlat.normalize();
                        double swRange = 8f;
                        int stunTicks = GameConstants.getInTicks(0, 1);
                        for (final var target : serverPlayer.serverLevel().players()) {
                            if (target.equals(serverPlayer) || !GameUtils.isPlayerAliveAndSurvival(target))
                                continue;
                            Vec3 to = new Vec3(target.getX() - serverPlayer.getX(), 0,
                                    target.getZ() - serverPlayer.getZ());
                            double dist = to.length();
                            if (dist > swRange || dist < 1.0e-4)
                                continue;
                            // 仅作用于正前方（约 ±72° 扇形）
                            if (lookFlat.dot(to.scale(1.0 / dist)) < 0.3D)
                                continue;
                            double strength = 1.4;
                            target.push(to.x / dist * strength, 0.42D, to.z / dist * strength);
                            if (target instanceof ServerPlayer stp) {
                                stp.setLastHurtByMob(serverPlayer);
                                stp.hurtMarked = true;
                                stp.connection.send(
                                        new ClientboundSetEntityMotionPacket(stp.getId(), stp.getDeltaMovement()));
                            }
                            target.addEffect(
                                    new MobEffectInstance(ModEffects.MOVE_BANED, stunTicks, 0, false, true, true));
                            target.addEffect(
                                    new MobEffectInstance(ModEffects.INVENTORY_BANED, stunTicks, 0, false, true, true));
                            target.addEffect(
                                    new MobEffectInstance(ModEffects.USED_BANED, stunTicks, 0, false, true, true));
                        }
                    }
                    serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(),
                            serverPlayer.getZ(),
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.0F);
                    return true;
                }).showOnHud(true)
                        .recordReplay().cooldownSeconds(60).announceToSelf().build());
    }
}

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

package org.agmas.noellesroles.game.roles.killer.undead_lord;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 亡灵之主事件注册：
 * <ul>
 *   <li>右键尸体发动【亡者复苏】——将尸体转化为无意识亡灵（45 秒冷却，最多同时 3 个）。</li>
 *   <li>角色分配时初始化组件状态。</li>
 * </ul>
 */
public class UndeadLordHandler {

    public static void init() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role.equals(ModRoles.UNDEAD_LORD)) {
                UndeadLordRoleData data = RoleData.getNullable(UndeadLordRoleData.class, player);
                if (data != null) {
                    data.init();
                }
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (!gameWorldComponent.isRole(serverPlayer, ModRoles.UNDEAD_LORD)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 不能复活葬仪伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            UndeadLordRoleData comp = RoleData.getNullable(UndeadLordRoleData.class, serverPlayer);
            if (comp == null) {
                return InteractionResult.PASS;
            }

            // 数量上限（基于开局人数动态计算，最多 4 个）
            if (!comp.canRaiseFromCorpse()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.max_reached",
                                comp.maxActiveUndead()).withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            // 冷却
            SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(serverPlayer);
            if (cooldown.hasCooldown()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.cooldown",
                                cooldown.getCooldown() / 20).withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            boolean ok = comp.spawnUndeadAt(serverLevel, body.position(), body.getPlayerUuid(),
                    org.agmas.noellesroles.content.entity.UndeadEntity.DEFAULT_LIFETIME);
            if (!ok) {
                return InteractionResult.PASS;
            }

            cooldown.setCooldown(NoellesRolesConfig.HANDLER.instance().undeadLordReviveCooldownSeconds * 20);
            body.remove(Entity.RemovalReason.DISCARDED);
            serverLevel.playSound(null, serverPlayer.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.HOSTILE, 1.0f, 0.6f);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.undead_lord.raised")
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    true);
            return InteractionResult.CONSUME;
        });
    }
}

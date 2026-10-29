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
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;

/** 统一注册尸体复苏与局末清理；职业赋予/撤销由 RoleData 生命周期负责，避免重复初始化。 */
public class UndeadLordHandler {
    private static boolean registered;

    public static void init() {
        if (registered) return;
        registered = true;
        OnGameEnd.EVENT.register((level, world) -> {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                var data = RoleData.getNullable(UndeadLordRoleData.class, player);
                if (data != null) data.clear();
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer lord) || !(entity instanceof PlayerBodyEntity body)
                    || !lord.server.isSameThread() || !UndeadLordRole.canAct(lord)) {
                return InteractionResult.PASS;
            }
            if (!UndeadLordRole.canReachCorpse(lord, body) || body.getPlayerUuid() == null) {
                return InteractionResult.FAIL;
            }
            var bodyData = PlayerBodyEntityComponent.KEY.get(body);
            if (bodyData.isFakeBody) {
                return fail(lord, "message.noellesroles.undead_lord.fake_body");
            }
            if (bodyData.nonInteractable) {
                return fail(lord, "message.noellesroles.corpse.pending_revive");
            }
            // 已复活玩家遗留的尸体不能再使用；离线死者的合法尸体仍可复苏。
            ServerPlayer deceased = lord.server.getPlayerList().getPlayer(body.getPlayerUuid());
            if (deceased != null && GameUtils.isPlayerAliveAndSurvival(deceased)) return InteractionResult.FAIL;
            var data = RoleData.getNullable(UndeadLordRoleData.class, lord);
            if (data == null) return InteractionResult.PASS;
            if (data.getSouls() < 1) return fail(lord, "message.noellesroles.undead_lord.no_souls");
            if (!data.canRaiseFromCorpse()) {
                return fail(lord, "message.noellesroles.undead_lord.max_reached", data.maxActiveUndead());
            }
            if (data.getRaiseCooldownTicks() > 0) {
                return fail(lord, "message.noellesroles.undead_lord.cooldown",
                        (data.getRaiseCooldownTicks() + 19) / 20);
            }

            // 主线程原子事务：先占用尸体，生成失败则解锁；成功后销毁尸体并提交魂/CD。
            // 后续手/玩家的交互会被 removed / nonInteractable 拦截，不能重复刷同一尸体。
            bodyData.nonInteractable = true;
            boolean raised = false;
            try {
                if (!data.spawnUndeadAt(lord.serverLevel(), body.position(), body.getPlayerUuid(),
                        UndeadEntity.DEFAULT_LIFETIME)) return InteractionResult.FAIL;
                body.discard();
                data.finishCorpseRaise();
                raised = true;
            } finally {
                if (!raised && !body.isRemoved()) bodyData.nonInteractable = false;
            }
            level.playSound(null, lord.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.HOSTILE, 1.0f, 0.6f);
            lord.displayClientMessage(Component.translatable("message.noellesroles.undead_lord.raised")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResult.CONSUME;
        });
    }

    private static InteractionResult fail(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }
}

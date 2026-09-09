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

package org.agmas.noellesroles.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Panda;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaState;

/**
 * 黑白熊猫形态的伪装渲染器：把玩家渲染成熊猫。
 *
 * <p>与 {@link LeatherPigDisguiseRenderer} 同一套路——每个伪装玩家持有一只「不入世界」的客户端熊猫实体，
 * 逐帧复制玩家位置与姿态后交给熊猫渲染器绘制。不入世界就不会被换图清场、区块卸载清掉，
 * 也不会出现「缓存里留着失效引用导致熊猫再也不出现」的问题。
 */
public class PandaDisguiseRenderer {
    private static final Map<UUID, Panda> PANDAS = new HashMap<>();

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        return PandaState.isPanda(player);
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        if (player.isSpectator()) {
            return false;
        }
        Panda panda = getPanda(player);
        if (panda == null) {
            return false;
        }
        // 逐帧复制本体的位置、朝向与行走动画
        panda.setPos(player.getX(), player.getY(), player.getZ());
        panda.xo = player.xo;
        panda.yo = player.yo;
        panda.zo = player.zo;
        panda.setYRot(player.getYRot());
        panda.setXRot(player.getXRot());
        panda.yRotO = player.yRotO;
        panda.xRotO = player.xRotO;
        panda.setYHeadRot(player.getYHeadRot());
        panda.yHeadRotO = player.yHeadRotO;
        panda.setYBodyRot(player.yBodyRot);
        panda.yBodyRotO = player.yBodyRotO;
        panda.walkAnimation.position = player.walkAnimation.position;
        panda.walkAnimation.speedOld = player.walkAnimation.speedOld;
        panda.walkAnimation.speed = player.walkAnimation.speed;
        // 复制本体的 tickCount，让熊猫模型的年龄类动画（头部摆动等）继续推进
        panda.tickCount = player.tickCount;
        panda.setDeltaMovement(0.0, 0.0, 0.0);
        // 刻意不复制玩家的隐身状态：黑白形态常带隐身（用来隐藏药水气泡），
        // 熊猫本体必须照常显示，否则伪装直接消失。
        panda.setCustomName(null);
        panda.setCustomNameVisible(false);

        EntityRenderer<? super Panda> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(panda);
        if (renderer == null) {
            return false;
        }
        renderer.render(panda, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }

    private static Panda getPanda(AbstractClientPlayer player) {
        Panda panda = PANDAS.get(player.getUUID());
        if (panda == null || panda.level() != player.level()) {
            panda = EntityType.PANDA.create(player.level());
            if (panda != null) {
                panda.setNoAi(true);
                PANDAS.put(player.getUUID(), panda);
            }
        }
        return panda;
    }

    /** 伪装结束时丢弃缓存的客户端熊猫（实体从未入世界，无需 removeEntity）。 */
    public static void discard(UUID playerId) {
        PANDAS.remove(playerId);
    }

    public static void clear() {
        PANDAS.clear();
    }
}

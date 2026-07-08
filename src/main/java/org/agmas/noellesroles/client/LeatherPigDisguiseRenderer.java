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
import net.minecraft.world.entity.animal.Pig;
import org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent;

/**
 * 皮革噶的：把玩家渲染成一头猪。
 * 每个伪装玩家持有一只不入世界的客户端猪实体，逐帧复制玩家的位置与姿态后交给猪渲染器绘制。
 */
public class LeatherPigDisguiseRenderer {
    private static final Map<UUID, Pig> PIGS = new HashMap<>();

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        LeatherPigPlayerComponent component = LeatherPigPlayerComponent.KEY.maybeGet(player).orElse(null);
        return component != null && component.isDisguised();
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Pig pig = getPig(player);
        if (pig == null) {
            return false;
        }
        // 行走动画每 tick 只推进一次，其余状态逐帧复制
        if (pig.tickCount != player.tickCount) {
            pig.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            pig.tickCount = player.tickCount;
        }
        pig.setPos(player.getX(), player.getY(), player.getZ());
        pig.xo = player.xo;
        pig.yo = player.yo;
        pig.zo = player.zo;
        pig.yBodyRot = player.yBodyRot;
        pig.yBodyRotO = player.yBodyRotO;
        pig.yHeadRot = player.yHeadRot;
        pig.yHeadRotO = player.yHeadRotO;
        pig.setXRot(player.getXRot());
        pig.xRotO = player.xRotO;
        pig.setInvisible(player.isInvisible());
        pig.hurtTime = player.hurtTime;
        pig.setCustomName(player.getDisplayName());
        pig.setCustomNameVisible(true);

        EntityRenderer<? super Pig> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pig);
        renderer.render(pig, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }

    private static Pig getPig(AbstractClientPlayer player) {
        Pig pig = PIGS.get(player.getUUID());
        if (pig == null || pig.level() != player.level()) {
            pig = EntityType.PIG.create(player.level());
            if (pig != null) {
                PIGS.put(player.getUUID(), pig);
            }
        }
        return pig;
    }
}

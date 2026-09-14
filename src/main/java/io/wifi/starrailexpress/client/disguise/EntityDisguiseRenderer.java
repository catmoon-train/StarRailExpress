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

package io.wifi.starrailexpress.client.disguise;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把伪装中的玩家画成目标实体（通用版「皮革噶的的猪」）。
 * <p>
 * 每位伪装玩家持有一只**不入世界的客户端实体**，逐帧复制玩家的位置、朝向与动画状态后，
 * 交给该实体自己的渲染器绘制——因此不枚举实体类型：牛、羊、僵尸、末影人、物品实体、
 * 甚至其他模组的实体都走同一条路径。
 * <p>
 * 临时实体只在伪装状态变化（换实体类型 / 换外观 NBT / 换维度）时重建一次，之后逐帧只做拷贝，
 * 不做任何实体创建；未伪装玩家这里只有一次缓存查找。
 * <p>
 * 注意：不调用 {@code tick()}——客户端跑实体 AI 没有意义且有副作用；只推进 {@code tickCount}
 * 与 {@code walkAnimation} 让动画动起来。
 */
@Environment(EnvType.CLIENT)
public final class EntityDisguiseRenderer {

    private static final Map<UUID, Entity> DUMMIES = new HashMap<>();
    /** 每只临时实体是按哪份状态造出来的；状态变了就重建。 */
    private static final Map<UUID, EntityDisguiseState> BUILT_FROM = new HashMap<>();

    private EntityDisguiseRenderer() {
    }

    /**
     * 渲染伪装模型。返回 {@code true} 表示已接管本帧渲染，调用方应取消玩家本体的渲染。
     */
    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        UUID uuid = player.getUUID();
        if (DUMMIES.isEmpty() && ClientEntityDisguiseCache.isEmpty()) {
            return false;
        }
        EntityDisguiseState state = ClientEntityDisguiseCache.get(uuid);
        if (state.isNone()) {
            discard(uuid);
            return false;
        }
        Entity dummy = resolveDummy(player, uuid, state);
        if (dummy == null) {
            return false;
        }
        copyPlayerState(player, dummy);
        EntityRenderer<? super Entity> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(dummy);
        if (renderer == null) {
            return false;
        }
        poseStack.pushPose();
        renderer.render(dummy, yaw, tickDelta, poseStack, bufferSource, packedLight);
        poseStack.popPose();
        return true;
    }

    /** 丢弃已解除伪装者的临时实体。收到同步包时调一次即可。 */
    public static void prune() {
        if (DUMMIES.isEmpty()) {
            return;
        }
        Set<UUID> stale = null;
        for (UUID uuid : new HashSet<>(DUMMIES.keySet())) {
            if (ClientEntityDisguiseCache.get(uuid).isNone()) {
                if (stale == null) {
                    stale = new HashSet<>();
                }
                stale.add(uuid);
            }
        }
        if (stale != null) {
            for (UUID uuid : stale) {
                discard(uuid);
            }
        }
    }

    public static void clear() {
        DUMMIES.clear();
        BUILT_FROM.clear();
    }

    private static @Nullable Entity resolveDummy(AbstractClientPlayer player, UUID uuid, EntityDisguiseState state) {
        Level level = player.level();
        Entity existing = DUMMIES.get(uuid);
        if (existing != null && existing.level() == level && state.equals(BUILT_FROM.get(uuid))) {
            return existing;
        }
        discard(uuid);
        Entity created = create(level, state);
        if (created == null) {
            return null;
        }
        DUMMIES.put(uuid, created);
        BUILT_FROM.put(uuid, state);
        return created;
    }

    private static @Nullable Entity create(Level level, EntityDisguiseState state) {
        try {
            Entity dummy = state.type() == null ? null : state.type().create(level);
            if (dummy == null) {
                return null;
            }
            if (state.nbt() != null) {
                dummy.load(state.nbt());
            }
            // 伪装期间不显示名字：本体渲染已取消，名字只在临时实体上才会浮现。
            dummy.setCustomName(null);
            dummy.setCustomNameVisible(false);
            return dummy;
        } catch (Throwable throwable) {
            SRE.LOGGER.warn("EntityDisguise 创建渲染用临时实体失败: {}", state, throwable);
            return null;
        }
    }

    private static void copyPlayerState(AbstractClientPlayer player, Entity dummy) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.setYRot(player.getYRot());
        dummy.yRotO = player.yRotO;
        dummy.setXRot(player.getXRot());
        dummy.xRotO = player.xRotO;
        dummy.setInvisible(player.isInvisible());
        if (dummy instanceof LivingEntity living) {
            living.hurtTime = player.hurtTime;
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;
            if (living.tickCount != player.tickCount) {
                // 行走动画每 tick 只推进一次，其余状态逐帧复制。
                living.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
                living.tickCount = player.tickCount;
            }
        } else {
            dummy.tickCount = player.tickCount;
        }
    }

    private static void discard(UUID uuid) {
        DUMMIES.remove(uuid);
        BUILT_FROM.remove(uuid);
    }
}

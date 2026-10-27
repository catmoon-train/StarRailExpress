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

package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.packet.GravityFlipS2CPacket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反重力方块的重力翻转管理。
 *
 * <p>不经过药水效果（药水会被牛奶、清除类机制抹掉），而是由本类在双端直接维护
 * 「谁正处于反重力」：
 * <ul>
 *     <li>服务端权威：每 tick 扫描玩家是否接触 {@code ANTI_GRAVITY_BLOCK}，
 *     边沿触发 + 2.5 秒冷却，每次触发翻转该玩家的重力方向；</li>
 *     <li>保护机制：因反重力向天上坠落超过 30 格自动解除；</li>
 *     <li>清理：玩家死亡、游戏模式不是冒险/创造、游戏结束/开始时自动解除；</li>
 *     <li>客户端通过 {@link GravityFlipS2CPacket} 同步，供本地物理预测、
 *     模型倒立与相机翻转读取（见 {@code GravityFlipEffect#isFlipped}）。</li>
 * </ul>
 *
 * <p>接触判定用 AABB 扫描而非碰撞事件：方块本身无碰撞，玩家是「穿过」它的。
 * 采用边沿触发（离开后再次进入才算新接触），站在方块里不会反复翻转。
 */
public final class GravityFlipManager {

    /** 同一玩家两次触发反重力方块的最小间隔（tick）：2.5 秒。 */
    public static final int TOUCH_COOLDOWN_TICKS = 50;
    /** 向上坠落保护阈值（格）：因反重力上升超过该距离自动解除。 */
    public static final double MAX_UPWARD_FALL = 30.0;

    private static final class State {
        /** 当前是否处于反重力（重力方向已翻转）。 */
        boolean flipped;
        /** 本次反重力开始时的 Y，用于 30 格上抛保护。 */
        double startY;
        /** 上次成功触发（翻转）的游戏时间，用于 2.5 秒冷却。 */
        long lastTriggerTime = Long.MIN_VALUE;
        /** 上一 tick 是否接触方块（边沿触发用）。 */
        boolean touching;
    }

    /** 服务端权威状态。 */
    private static final Map<UUID, State> SERVER_STATES = new ConcurrentHashMap<>();
    /** 客户端同步来的反重力玩家集合（渲染 / 本地物理 / 相机）。 */
    private static final Set<UUID> CLIENT_FLIPPED = ConcurrentHashMap.newKeySet();

    private GravityFlipManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(GravityFlipManager::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SERVER_STATES.remove(handler.getPlayer().getUUID()));
        // 游戏结束 / 开始：清空所有反重力状态
        OnGameEnd.EVENT.register((world, game) -> clearAll(world));
        OnGameStarted.EVENT.register(GravityFlipManager::clearAll);
    }

    // ─────────────────────────── 客户端 ───────────────────────────

    /** 客户端收到同步包后更新集合。 */
    public static void setClientFlipped(UUID playerId, boolean flipped) {
        if (flipped) {
            CLIENT_FLIPPED.add(playerId);
        } else {
            CLIENT_FLIPPED.remove(playerId);
        }
    }

    public static void clearClient() {
        CLIENT_FLIPPED.clear();
    }

    // ─────────────────────────── 双端查询 ───────────────────────────

    /** 该实体是否正处于反重力（客户端查同步集合，服务端查权威状态）。 */
    public static boolean isFlipped(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.level().isClientSide()) {
            return entity instanceof Player player && CLIENT_FLIPPED.contains(player.getUUID());
        }
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        State state = SERVER_STATES.get(player.getUUID());
        return state != null && state.flipped;
    }

    // ─────────────────────────── 服务端 ───────────────────────────

    private static void serverTick(ServerLevel world) {
        if (world.players().isEmpty()) {
            return;
        }
        long now = world.getGameTime();
        for (ServerPlayer player : world.players()) {
            State state = SERVER_STATES.get(player.getUUID());
            if (state == null) {
                // 尚未接触过反重力方块的玩家：只有真正接触时才建立状态并首次触发
                if (isTouchingBlock(player)) {
                    state = new State();
                    state.touching = true;
                    state.lastTriggerTime = now;
                    SERVER_STATES.put(player.getUUID(), state);
                    setFlipped(world, player, state, true, now);
                }
                continue;
            }
            // 清理条件：死亡 / 实体移除 / 非冒险非创造模式
            if (!player.isAlive() || player.isRemoved() || !isSupportedGameMode(player)) {
                if (state.flipped) {
                    setFlipped(world, player, state, false, now);
                }
                state.touching = false;
                continue;
            }
            // 接触扫描：边沿触发（离开后再次进入才算新接触）
            boolean touching = isTouchingBlock(player);
            if (touching && !state.touching && now - state.lastTriggerTime >= TOUCH_COOLDOWN_TICKS) {
                state.lastTriggerTime = now;
                setFlipped(world, player, state, !state.flipped, now);
            }
            state.touching = touching;
            // 30 格上抛保护
            if (state.flipped && player.getY() - state.startY > MAX_UPWARD_FALL) {
                setFlipped(world, player, state, false, now);
            }
            // 没有任何进行中的状态（未反重力、未接触、冷却已过）时移除条目，避免堆积
            if (!state.flipped && !state.touching && now - state.lastTriggerTime >= TOUCH_COOLDOWN_TICKS) {
                SERVER_STATES.remove(player.getUUID(), state);
            }
        }
    }

    /** 切换（或设置）玩家的反重力状态并广播给全世界。 */
    private static void setFlipped(ServerLevel world, ServerPlayer player, State state, boolean flipped, long now) {
        if (state.flipped == flipped) {
            return;
        }
        state.flipped = flipped;
        state.startY = player.getY();
        for (ServerPlayer viewer : world.players()) {
            ServerPlayNetworking.send(viewer, new GravityFlipS2CPacket(player.getUUID(), flipped));
        }
    }

    /** 玩家的碰撞箱是否与反重力方块重叠（无碰撞方块用 AABB 扫描判定「接触」）。 */
    private static boolean isTouchingBlock(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(0.05D);
        Level level = player.level();
        int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
        int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
        int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(ModSceneBlocks.ANTI_GRAVITY_BLOCK)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 反重力只在冒险 / 创造模式下维持，其余模式（生存 / 旁观）立即解除。 */
    private static boolean isSupportedGameMode(ServerPlayer player) {
        GameType type = player.gameMode.getGameModeForPlayer();
        return type == GameType.ADVENTURE || type == GameType.CREATIVE;
    }

    /** 清空所有反重力状态（游戏结束 / 开始时调用），并广播给客户端。 */
    public static void clearAll(ServerLevel world) {
        for (Map.Entry<UUID, State> entry : SERVER_STATES.entrySet()) {
            if (entry.getValue().flipped) {
                for (ServerPlayer viewer : world.players()) {
                    ServerPlayNetworking.send(viewer, new GravityFlipS2CPacket(entry.getKey(), false));
                }
            }
        }
        SERVER_STATES.clear();
    }
}

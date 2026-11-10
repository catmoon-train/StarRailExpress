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

package org.agmas.noellesroles.game.moment;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.MomentMusicS2CPacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全项目「时刻」的统一管理器（服务端权威）。
 *
 * <p>
 * 负责三件事：
 * </p>
 * <ol>
 * <li><b>起止标记</b>：各时刻在开始/结束时调用 {@link #begin}/{@link #end}，管理器维护
 * 一个按触发先后排序的活动时刻栈。</li>
 * <li><b>进度记录</b>：{@link #tick} 每 level tick 遍历活动时刻累加进度（≤7 次 int 运算，
 * 完全在内存中，不做逐 tick 序列化，保证性能）；暂停中的时刻不推进。</li>
 * <li><b>音乐统一管理</b>：{@link #refreshMusic} 从栈顶（最新）向下取第一个「带音乐」的时刻
 * 作为当前音乐，仅在其变化时向客户端下发 {@link MomentMusicS2CPacket}，因此天然支持：
 * 新时刻音乐覆盖旧时刻；新时刻无音乐则保持；新时刻结束而旧时刻仍在则回退到旧时刻音乐；
 * 时刻结束立即停播。</li>
 * </ol>
 *
 * <p>
 * <b>亡命徒插队</b>：亡命徒时刻（{@link MomentType#DESPERADO}）通过 {@link #yieldToDesperado}
 * 压栈并置位，其它时刻在此期间被 {@link #isPaused} 判定为暂停（不推进进度、不做计时结算）。
 * 亡命徒时刻本身会做整局时间回溯，结束后调用 {@link #resumeFromDesperado} 出栈，被回溯
 * 还原的旧时刻继续推进——进度因暂停期间未推进，故与回溯前的快照一致。
 * </p>
 */
public final class MomentManager {

    /** 每个世界（ServerLevel）一份时刻状态。 */
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    private MomentManager() {
    }

    private static final class State {
        /** 活动时刻，按触发先后排序（越靠后越新）。 */
        final List<MomentType> order = new ArrayList<>();
        /** 各时刻已推进的 tick 数（暂停期间不增长）。 */
        final EnumMap<MomentType, Integer> progress = new EnumMap<>(MomentType.class);
        /** 当前已下发给客户端的音乐；{@code null} 表示无。 */
        MomentType currentMusic = null;
        /** 亡命徒时刻是否正插队。 */
        boolean desperado = false;
    }

    /** 注册全局钩子：每 world tick 推进进度、玩家加入补发当前音乐、局末/卸载清理。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(MomentManager::tick);
        OnGameStarted.EVENT.register(level -> clear(level, true));
        OnGameEnd.EVENT.register((level, game) -> clear(level, true));
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level, false));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            State state = STATES.get(player.serverLevel());
            sendMusic(player, state == null ? null : state.currentMusic);
        });
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new State());
    }

    // ==================== 起止标记 ====================

    /** 时刻开始：入栈（去重），必要时刷新音乐。 */
    public static void begin(ServerLevel level, MomentType type) {
        if (level == null || type == null) {
            return;
        }
        State state = state(level);
        if (!state.order.contains(type)) {
            state.order.add(type);
        }
        if (type == MomentType.DESPERADO) {
            state.desperado = true;
        }
        refreshMusic(level, state);
    }

    /** 时刻结束：出栈，必要时刷新音乐。 */
    public static void end(ServerLevel level, MomentType type) {
        if (level == null || type == null) {
            return;
        }
        State state = STATES.get(level);
        if (state == null) {
            return;
        }
        state.order.remove(type);
        state.progress.remove(type);
        if (type == MomentType.DESPERADO) {
            state.desperado = false;
        }
        refreshMusic(level, state);
    }

    /** 亡命徒时刻开始：插队并暂停其它时刻。 */
    public static void yieldToDesperado(ServerLevel level) {
        begin(level, MomentType.DESPERADO);
    }

    /** 亡命徒时刻结束（时间回溯完成后）：出队并恢复旧时刻。 */
    public static void resumeFromDesperado(ServerLevel level) {
        end(level, MomentType.DESPERADO);
    }

    public static boolean isActive(ServerLevel level, MomentType type) {
        State state = STATES.get(level);
        return state != null && state.order.contains(type);
    }

    public static boolean isDesperadoActive(ServerLevel level) {
        State state = STATES.get(level);
        return state != null && state.desperado;
    }

    /**
     * 某时刻此刻是否应暂停（亡命徒插队期间，除亡命徒外的时刻全部暂停）。
     * 各时刻在自己的 serverTick 中用它来跳过计时/结算。
     */
    public static boolean isPaused(ServerLevel level, MomentType type) {
        State state = STATES.get(level);
        return state != null && state.desperado && type != MomentType.DESPERADO;
    }

    // ==================== 进度记录（内存、暂停感知、开销 O(活动时刻数)） ====================

    /** 取某时刻已推进的 tick 数。 */
    public static int progress(ServerLevel level, MomentType type) {
        State state = STATES.get(level);
        Integer value = state == null ? null : state.progress.get(type);
        return value == null ? 0 : value;
    }

    /** 显式写入进度（例如事件驱动型时刻按事件跳进）。 */
    public static void setProgress(ServerLevel level, MomentType type, int value) {
        State state = STATES.get(level);
        if (state != null && state.order.contains(type)) {
            state.progress.put(type, value);
        }
    }

    /** 每 world tick 推进活动时刻进度；亡命徒期间的其它时刻不推进（即暂停）。 */
    public static void tick(ServerLevel level) {
        State state = STATES.get(level);
        if (state == null || state.order.isEmpty()) {
            return;
        }
        for (MomentType type : state.order) {
            if (state.desperado && type != MomentType.DESPERADO) {
                continue;
            }
            state.progress.merge(type, 1, Integer::sum);
        }
    }

    // ==================== 音乐统一管理 ====================

    /** 重新裁决当前音乐：取「最新且带音乐」的活动时刻；仅在变化时下发。 */
    private static void refreshMusic(ServerLevel level, State state) {
        MomentType resolved = null;
        for (int i = state.order.size() - 1; i >= 0; i--) {
            MomentType type = state.order.get(i);
            if (type.hasMusic()) {
                resolved = type;
                break;
            }
        }
        if (resolved == state.currentMusic) {
            return;
        }
        state.currentMusic = resolved;
        broadcastMusic(level, resolved);
    }

    private static void broadcastMusic(ServerLevel level, MomentType type) {
        for (ServerPlayer player : level.players()) {
            sendMusic(player, type);
        }
    }

    private static void sendMusic(ServerPlayer player, MomentType type) {
        ServerPlayNetworking.send(player, new MomentMusicS2CPacket(type == null ? -1 : type.ordinal()));
    }

    private static void clear(ServerLevel level, boolean notify) {
        State state = STATES.get(level);
        if (state == null) {
            return;
        }
        MomentType last = state.currentMusic;
        STATES.remove(level);
        if (notify && last != null) {
            broadcastMusic(level, null);
        }
    }
}

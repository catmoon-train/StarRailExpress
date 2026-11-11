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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.MomentMusicS2CPacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全项目「时刻」的统一管理器（服务端权威）。
 *
 * <p>
 * 负责四件事：
 * </p>
 * <ol>
 * <li><b>统一触发（一局一次）</b>：{@link #trigger} 是各时刻的唯一触发入口，负责记录
 * 「本局已触发过」并登记活动时刻。默认一局只有第一次调用返回 {@code true} 并做开场演出；
 * 之后无论场上条件是否又满足/不满足，都返回 {@code false}，调用方应继续保持已触发后的
 * 状态与效果，既不关闭效果，也不重新触发。仅 {@link MomentType#repeatable()} 的修饰符
 * 时刻（亡命徒）例外。记录随 {@link #clear} 在开局/局末/卸载时清空。</li>
 * <li><b>起止标记</b>：{@link #begin} 为低层入栈（供已预留名额后的延迟开场使用），
 * {@link #end} 为出栈；管理器维护一个按触发先后排序的活动时刻栈。</li>
 * <li><b>进度记录</b>：{@link #tick} 每 level tick 遍历活动时刻累加进度（≤7 次 int 运算，
 * 完全在内存中，不做逐 tick 序列化，保证性能）；暂停中的时刻不推进。</li>
 * <li><b>音乐与触发音效</b>：{@link #refreshMusic} 从栈顶（最新）向下取第一个「带音乐」的时刻
 * 作为当前音乐，仅在其变化时向客户端下发 {@link MomentMusicS2CPacket}，因此天然支持：
 * 新时刻音乐覆盖旧时刻；新时刻无音乐则保持；新时刻结束而旧时刻仍在则回退到旧时刻音乐；
 * 时刻结束立即停播。带 {@link MomentType#cue()} 的时刻（如 {@link MomentType#INFECTED}、
 * {@link MomentType#ARSONIST}）只在触发瞬间由 {@link #trigger} 播放一次该小段音效，
 * 不参与音乐裁决。</li>
 * </ol>
 *
 * <p>
 * <b>亡命徒插队</b>：亡命徒时刻（{@link MomentType#DESPERADO}）通过 {@link #yieldToDesperado}
 * 压栈并置位，其它时刻在此期间被 {@link #isPaused} 判定为暂停（不推进进度、不做计时结算）。
 * 亡命徒时刻本身会做整局时间回溯，结束后调用 {@link #resumeFromDesperado} 出栈，被回溯
 * 还原的旧时刻继续推进——进度因暂停期间未推进，故与回溯前的快照一致。修饰符时刻可反复
 * 插队，不受「一局一次」限制；其它时刻的触发记录也不会因回溯而清除，因此不会出现
 * 「回溯后条件重新满足又触发一遍」。
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
        /** 本局已经触发过的时刻（一局一次）；可重复触发的修饰符时刻不记录。 */
        final EnumSet<MomentType> triggered = EnumSet.noneOf(MomentType.class);
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
        // 开局/局末按服务器清掉所有世界的时刻状态，确保「本局已触发」记录不会带进下一局，
        // 也不受事件所给 level 与实际游玩 level 不一致的影响。
        OnGameStarted.EVENT.register(level -> clearAll(level.getServer()));
        OnGameEnd.EVENT.register((level, game) -> clearAll(level.getServer()));
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

    // ==================== 统一触发（一局一次） ====================

    /**
     * 本局该时刻是否已经触发过。可重复触发的修饰符时刻恒返回 {@code false}。
     * 注意「触发过」与「当前活动」是两回事：时刻即使已经结束，本局也不会再次触发。
     */
    public static boolean hasTriggered(ServerLevel level, MomentType type) {
        if (level == null || type == null || type.repeatable()) {
            return false;
        }
        State state = STATES.get(level);
        return state != null && state.triggered.contains(type);
    }

    /** 本局是否还能触发该时刻；修饰符触发的时刻始终可以。 */
    public static boolean canTrigger(ServerLevel level, MomentType type) {
        return type != null && (type.repeatable() || !hasTriggered(level, type));
    }

    /**
     * 预占本局唯一的一次触发名额，但不入栈。供「提示音效先响、音乐延迟开始」这类
     * 需要延迟开场的时刻使用：占位成功后稍后调用 {@link #begin} 完成入栈。
     * 返回 {@code false} 表示本局已触发过，调用方应保持已有状态与效果，不再开场。
     */
    public static boolean reserve(ServerLevel level, MomentType type) {
        if (level == null || type == null) {
            return false;
        }
        if (type.repeatable()) {
            return true;
        }
        return state(level).triggered.add(type);
    }

    /**
     * 统一的时刻触发入口：默认一局仅能触发一次。
     *
     * <p>
     * 第一次调用会记录本局已触发、把时刻登记为活动、播放一次性触发音效，并返回
     * {@code true}，调用方此时才做开场演出与效果初始化。之后（包括时刻因死亡/结算
     * 结束后、场上条件又重新满足时）一律返回 {@code false}，调用方应继续保持已触发
     * 后的状态与效果，不要关闭效果，也不要重复开场。
     * </p>
     *
     * <p>
     * 仅 {@link MomentType#repeatable()} 为 {@code true} 的修饰符时刻（亡命徒）可反复调用。
     * </p>
     */
    public static boolean trigger(ServerLevel level, MomentType type) {
        if (level == null || type == null || !reserve(level, type)) {
            return false;
        }
        begin(level, type);
        playCue(level, type);
        return true;
    }

    // ==================== 起止标记 ====================

    /**
     * 低层入栈：把时刻登记为活动（去重）并刷新音乐。一般应使用 {@link #trigger}；
     * 本方法只用于 {@link #reserve} 占位后的延迟开场，或回溯后的状态恢复。
     */
    public static void begin(ServerLevel level, MomentType type) {
        if (level == null || type == null) {
            return;
        }
        State state = state(level);
        // 直接入栈也计入本局已触发，避免绕过「一局一次」的统一判定。
        if (!type.repeatable()) {
            state.triggered.add(type);
        }
        if (!state.order.contains(type)) {
            state.order.add(type);
        }
        if (type == MomentType.DESPERADO) {
            state.desperado = true;
        }
        refreshMusic(level, state);
    }

    /** 时刻结束：出栈，必要时刷新音乐；不清空本局的触发记录。 */
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

    /** 亡命徒时刻开始：插队并暂停其它时刻；修饰符时刻不受一局一次限制。 */
    public static void yieldToDesperado(ServerLevel level) {
        trigger(level, MomentType.DESPERADO);
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

    // ==================== 音乐 / 触发音效 ====================

    /**
     * 播放触发瞬间的一次性音效：不是音乐，不循环、不参与裁决。
     * 给每名玩家各发一次，确保全员可闻且不会叠音。
     */
    private static void playCue(ServerLevel level, MomentType type) {
        MomentType.Cue cue = type.cue();
        if (cue == null) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.playNotifySound(cue.sound(), cue.source(), cue.volume(), cue.pitch());
        }
    }

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

    /** 开局/局末：清掉该服务器所有世界的时刻状态（含「本局已触发」记录）并停播音乐。 */
    private static void clearAll(MinecraftServer server) {
        for (Map.Entry<ServerLevel, State> entry : new ArrayList<>(STATES.entrySet())) {
            ServerLevel level = entry.getKey();
            if (level.getServer() != server) {
                continue;
            }
            clear(level, true);
        }
    }
}

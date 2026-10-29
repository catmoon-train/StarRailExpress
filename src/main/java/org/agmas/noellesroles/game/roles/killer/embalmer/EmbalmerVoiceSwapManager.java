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

package org.agmas.noellesroles.game.roles.killer.embalmer;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嬉命人·语音互换。
 *
 * <p>原理：不交换音频数据，只交换「声音发出的实体」。A 说话时，声音从 B 所在的位置发出
 * （语音 HUD 上显示的说话人仍是 A）。配对是双向的，因此 A、B 两人的语音发声位置互换。
 *
 * <p>服务端权威；客户端通过 {@code EmbalmerRoleData#voiceSwapCooldown} 读取冷却。
 *
 * <p>结束条件：游戏结束、持续时间到时、参与互换的任意一方死亡（变成旁观者 / 离开冒险模式）、
 * 任意一方离线或换维度。
 */
public final class EmbalmerVoiceSwapManager {

    /** 单次互换持续时间（tick）：2 分钟。 */
    public static final int DURATION_TICKS = 120 * 20;
    /** 互换后的技能冷却（tick）：80 秒。 */
    public static final int COOLDOWN_TICKS = 80 * 20;

    private record Link(UUID partner, long until) {
    }

    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    private EmbalmerVoiceSwapManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(EmbalmerVoiceSwapManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> remove(handler.getPlayer().getUUID()));
        OnGameEnd.EVENT.register((level, game) -> clear());
    }

    /** 建立双向配对。 */
    public static void swap(ServerPlayer first, ServerPlayer second, long now) {
        if (first == null || second == null || first.getUUID().equals(second.getUUID())) {
            return;
        }
        long until = now + DURATION_TICKS;
        remove(first.getUUID());
        remove(second.getUUID());
        LINKS.put(first.getUUID(), new Link(second.getUUID(), until));
        LINKS.put(second.getUUID(), new Link(first.getUUID(), until));
        Noellesroles.LOGGER.info("[Embalmer] Voice swap established: {} <-> {} for {} ticks",
                first.getName().getString(), second.getName().getString(), DURATION_TICKS);
    }

    /** 移除某人的配对（会成对移除，避免残留单向链接）。 */
    public static void remove(UUID player) {
        if (player == null) {
            return;
        }
        Link old = LINKS.remove(player);
        if (old == null) {
            return;
        }
        Link reverse = LINKS.get(old.partner());
        if (reverse != null && reverse.partner().equals(player)) {
            LINKS.remove(old.partner());
        }
    }

    public static void clear() {
        LINKS.clear();
    }

    public static boolean isSwapped(UUID player) {
        return player != null && LINKS.containsKey(player);
    }

    /**
     * 返回说话者的声音应当从哪个玩家身上发出；没有配对（或已过期）时返回 {@code null}，
     * 此时走原版默认广播。
     */
    @Nullable
    public static ServerPlayer voiceBody(ServerPlayer speaker) {
        // 说话者自己已经死亡/旁观：立即结束这一对互换
        if (!isSwapTargetValid(speaker.level(), speaker)) {
            remove(speaker.getUUID());
            return null;
        }
        Link link = LINKS.get(speaker.getUUID());
        if (link == null) {
            return null;
        }
        if (speaker.level().getGameTime() >= link.until()) {
            remove(speaker.getUUID());
            return null;
        }
        ServerPlayer partner = speaker.getServer().getPlayerList().getPlayer(link.partner());
        if (!isSwapTargetValid(speaker.level(), partner)) {
            // 对方死亡 / 离线 / 换维度：结束这一对互换
            remove(speaker.getUUID());
            return null;
        }
        return partner;
    }

    /** 参与语音互换者必须是在同一维度、且存活（冒险模式）的玩家。 */
    private static boolean isSwapTargetValid(net.minecraft.world.level.Level level, ServerPlayer player) {
        return player != null
                && !player.isRemoved()
                && player.level() == level
                && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static void tick(ServerLevel world) {
        if (LINKS.isEmpty()) {
            return;
        }
        long now = world.getGameTime();
        List<UUID> broken = new ArrayList<>();
        for (Map.Entry<UUID, Link> entry : LINKS.entrySet()) {
            UUID self = entry.getKey();
            Link link = entry.getValue();
            if (now >= link.until()) {
                broken.add(self);
                continue;
            }
            ServerPlayer selfPlayer = world.getServer().getPlayerList().getPlayer(self);
            ServerPlayer partner = world.getServer().getPlayerList().getPlayer(link.partner());
            if (!isSwapTargetValid(world, selfPlayer) || !isSwapTargetValid(world, partner)) {
                broken.add(self);
            }
        }
        for (UUID id : broken) {
            remove(id);
        }
    }
}

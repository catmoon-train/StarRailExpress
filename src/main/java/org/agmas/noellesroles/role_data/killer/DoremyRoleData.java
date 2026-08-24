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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;
import org.jetbrains.annotations.NotNull;

public class DoremyRoleData extends SimpleRoleData {
    public static final int REWIND_RESTORE_SMOOTH_TICKS = 20;

    public static record DoremyDreamInfo(long endTime, TimeRewindSnapshot snapshot, ServerPlayer player) {
        public void restoreSmoothly() {
            TimeRewind.smoothRestore(player, snapshot, REWIND_RESTORE_SMOOTH_TICKS);
        }

        public void restoreImmediately() {
            TimeRewind.restore(player, snapshot);
        }
    }

    // server-side only!
    public static Map<UUID, DoremyDreamInfo> REWIND_INFOS = new ConcurrentHashMap<>();

    public int cooldownForDoremyGhost = 100;
    public int cooldownForDoremyDream = 20 * 30;

    /**
     * 构造函数
     */
    public DoremyRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
    }

    @Override
    public void clear() {
        REWIND_INFOS.forEach((uuid, info) -> {
            info.restoreImmediately();
        });
        REWIND_INFOS.clear();
    }

    /**
     * 完全清除组件状态（游戏结束时调用）
     */
    public void clearAll() {
        clear();
    }

    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            DoremyRoleData.serverTickStatic(server.overworld());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            final var player = handler.player;
            server.execute(() -> {
                if (REWIND_INFOS.containsKey(player.getUUID())) {
                    REWIND_INFOS.remove(player.getUUID());
                }
            });
        });
    }

    public static boolean tryDream(ServerPlayer player, long ticks) {
        if (player == null)
            return false;
        if (REWIND_INFOS.containsKey(player.getUUID())) {
            return false;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        player.server.execute(() -> {
            REWIND_INFOS.put(player.getUUID(), new DoremyDreamInfo(now + ticks, TimeRewind.capture(player), player));

            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.doremy_dream",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true),
                            String.format("%.1f", ticks / 20f)));
        });
        return true;
    }

    public static void serverTickStatic(ServerLevel world) {
        long now = GameUtils.getTicksFromGameStart(world);
        final var it = REWIND_INFOS.entrySet().iterator();
        while (it.hasNext()) {
            final var entry = it.next();
            final DoremyDreamInfo info = entry.getValue();

            if (info.player == null) {
                it.remove();
                continue;
            }
            if (info.player.hasDisconnected()) {
                it.remove();
                continue;
            }
            if (now >= info.endTime) {
                info.restoreSmoothly();
                SRE.REPLAY_MANAGER.recordPlayerRevival(info.player().getUUID(), null);
                it.remove();
                continue;
            }

        }
    }

    @Override
    public void serverTick() {
        boolean shouldSync = player.level().getGameTime() % 400 == 5;
        if (cooldownForDoremyGhost > 0) {
            cooldownForDoremyGhost--;
            if (cooldownForDoremyGhost <= 0) {
                shouldSync = true;
            }
        }
        if (cooldownForDoremyDream > 0) {
            cooldownForDoremyDream--;
            if (cooldownForDoremyDream <= 0) {
                shouldSync = true;
            }
        }
        if (shouldSync)
            sync();
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("ghost", cooldownForDoremyGhost);
        tag.putInt("dream", cooldownForDoremyDream);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        cooldownForDoremyGhost = getIntTag(tag, "ghost", 0);
        cooldownForDoremyDream = getIntTag(tag, "dream", 0);
    }

    @Override
    public void clientTick() {

        if (cooldownForDoremyGhost > 0) {
            cooldownForDoremyGhost--;
        }
        if (cooldownForDoremyDream > 0) {
            cooldownForDoremyDream--;
        }
    }

    public static boolean isDreaming(Player player) {
        if (player == null)
            return false;
        return (REWIND_INFOS.containsKey(player.getUUID()));
    }
}

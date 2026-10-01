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

package io.wifi.starrailexpress.game.modes.funny.rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 职业选择「前置位」名单（指令 {@code /sre:draft_priority} 写入）。
 *
 * <p>
 * 名单中的玩家在<b>下一次</b>创建职业选择顺序时（志愿海选 / 职业轮选 / 单选职业轮选）
 * 会被移到序号最前（从 1 号开始）；消费后自动移出名单（一次性），不影响任何职业池与概率，
 * 只是排序提前。名单仅存于内存，服务器重启后清空。
 */
public final class DraftOrderPriority {
    /** 待前置的玩家（按标记先后排序，可跨局保留直到该玩家实际参与选择）。 */
    private static final ConcurrentLinkedDeque<UUID> PENDING = new ConcurrentLinkedDeque<>();

    private DraftOrderPriority() {
    }

    /** 标记一个玩家下次职业选择时处于前置位（重复标记会刷新到最新位置）。 */
    public static void add(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        PENDING.remove(playerUuid);
        PENDING.addLast(playerUuid);
    }

    /** 取消一个玩家的前置标记。 */
    public static boolean remove(UUID playerUuid) {
        return playerUuid != null && PENDING.remove(playerUuid);
    }

    /**
     * 取出名单中存在于 {@code participants}（本局参选玩家）内的前置玩家（按标记先后排序），
     * 并把它们从名单中消费掉（一次性）；不在本局参选名单里的标记保留，留待下次。
     */
    public static List<UUID> takePending(Collection<UUID> participants) {
        List<UUID> front = new ArrayList<>();
        if (PENDING.isEmpty() || participants == null || participants.isEmpty()) {
            return front;
        }
        for (Iterator<UUID> it = PENDING.iterator(); it.hasNext();) {
            UUID uuid = it.next();
            if (participants.contains(uuid)) {
                front.add(uuid);
                it.remove();
            }
        }
        return front;
    }

    /** 是否存在前置标记（诊断用）。 */
    public static boolean isPending(UUID playerUuid) {
        return playerUuid != null && PENDING.contains(playerUuid);
    }

    /** 名单当前大小（诊断用）。 */
    public static int pendingCount() {
        return PENDING.size();
    }

    /** 名单快照（诊断用）。 */
    public static Set<UUID> pendingSnapshot() {
        return Set.copyOf(PENDING);
    }
}

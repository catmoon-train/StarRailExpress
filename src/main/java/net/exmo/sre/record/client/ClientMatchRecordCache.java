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

package net.exmo.sre.record.client;

import net.exmo.sre.record.MatchRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的全局战绩缓存：以「虚拟列表」的方式按下标稀疏保存已收到的战绩摘要，
 * 记录数据库内总条数，并对已请求的分页去重，从而只在滚动到对应区间时才向服务端拉取。
 * 同时缓存已加载的完整回放记录。
 */
public final class ClientMatchRecordCache {

    private static volatile int total = 0;
    private static final Map<Integer, MatchRecord.Summary> BY_INDEX = new ConcurrentHashMap<>();
    private static final Set<Integer> REQUESTED_PAGES = ConcurrentHashMap.newKeySet();
    private static final Map<String, MatchRecord> LOADED = new ConcurrentHashMap<>();

    private ClientMatchRecordCache() {
    }

    /** 应用服务端返回的一页摘要：更新总数并按下标写入。 */
    public static void applyWindow(int offset, int total, List<MatchRecord.Summary> items) {
        ClientMatchRecordCache.total = Math.max(0, total);
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            BY_INDEX.put(offset + i, items.get(i));
        }
    }

    public static int getTotal() {
        return total;
    }

    public static MatchRecord.Summary getAt(int index) {
        return BY_INDEX.get(index);
    }

    /** 标记某一分页已被请求；返回 true 表示此前未请求过（需要真正发包）。 */
    public static boolean markPageRequested(int page) {
        return REQUESTED_PAGES.add(page);
    }

    /** 重置虚拟列表（刷新或重新打开时调用），强制重新拉取。 */
    public static void resetWindows() {
        total = 0;
        BY_INDEX.clear();
        REQUESTED_PAGES.clear();
    }

    public static void putRecord(MatchRecord record) {
        if (record != null && record.matchId != null) {
            LOADED.put(record.matchId, record);
        }
    }

    public static MatchRecord getRecord(String matchId) {
        return matchId == null ? null : LOADED.get(matchId);
    }
}

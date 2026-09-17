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

package io.wifi.starrailexpress.synccontent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 可同步的自定义内容通道。
 *
 * <p>
 * 每个通道对应「服务端存档里的一份 JSON 配置文件 + 客户端 config 目录的一份同步副本」，
 * 四个通道（自定义职业 / 自定义修饰符 / 自定义列车物品 / 自定义方块）共用同一套握手 + 按需
 * 下发的同步协议，见 {@link ContentSyncServer} 与
 * {@link io.wifi.starrailexpress.client.network.ContentSyncClient}。
 *
 * <p>
 * {@link #protocolVersion()} 参与哈希计算：协议（数据字段语义）变更时把它 +1，所有客户端
 * 的本地缓存自然失效，不需要客户端与服务端版本号对齐。
 *
 * <p>
 * 通道之间有解析依赖（见 {@link #LOAD_ORDER} 与 {@link #dependents()}）。
 */
public enum ContentChannel {

    /** 自定义职业（sre_custom_roles.json）。 */
    CUSTOM_ROLE("custom_role", 1, "sre_custom_roles.json"),
    /**
     * 自定义修饰符（sre_custom_modifiers.json）。
     *
     * <p>
     * 协议版本 2：触发内容从「一份扁平列表」改成「多组（条件 → 内容）」（{@code groups}）。
     * 客户端缓存按 id + 协议版本做哈希，改版本号能让老缓存失效、重新同步一次。
     */
    CUSTOM_MODIFIER("custom_modifier", 2, "sre_custom_modifiers.json"),
    /** 自定义列车物品（sre_custom_items.json）。 */
    CUSTOM_ITEM("custom_item", 1, "sre_custom_items.json"),
    /** 自定义方块（sre_custom_blocks.json）。 */
    CUSTOM_BLOCK("custom_block", 1, "sre_custom_blocks.json");

    /** 当前全部通道共用的协议版本（握手包里带上，仅用于排查）。 */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * 通道的加载顺序：<b>被依赖的通道排在前面</b>。
     *
     * <p>
     * 自定义职业的初始物品 / 任务奖励 / 商店条目、以及自定义修饰符的职业限制，都是在**注册时**
     * 按 id 去查已有索引的（见 {@code CustomRoleLoader#parseConfiguredItem}），所以物品索引必须
     * 先就绪；修饰符还要解析职业，因此排在职业之后。客户端按这个顺序应用同步下来的内容，
     * 服务端重载（{@code CustomContentReload}）与服务端启动也以它为准。
     *
     * <p>
     * 顺序错了的后果不是报错而是**静默丢条目**：注册时查不到自定义物品，职业商店里那条自定义
     * 物品就不会被加进去（不显示、也买不到）。
     */
    public static final List<ContentChannel> LOAD_ORDER = List.of(
            CUSTOM_ITEM, CUSTOM_BLOCK, CUSTOM_ROLE, CUSTOM_MODIFIER);

    private final String id;
    private final int protocolVersion;
    private final String fileName;

    ContentChannel(String id, int protocolVersion, String fileName) {
        this.id = id;
        this.protocolVersion = protocolVersion;
        this.fileName = fileName;
    }

    /** 通道标识（网络包里传输的字符串）。 */
    public String id() {
        return id;
    }

    /** 该通道的数据格式版本（参与哈希）。 */
    public int protocolVersion() {
        return protocolVersion;
    }

    /** 服务端存档根目录 / 客户端 config 目录下的配置文件名。 */
    public String fileName() {
        return fileName;
    }

    /**
     * **直接**依赖本通道的通道：本通道（重新）加载后必须重新注册它们。
     *
     * <p>
     * 例：自定义物品索引变了，已经注册过的职业必须重新解析一遍——否则那条「注册时物品还不存在」
     * 的商店条目会一直缺失。依赖链是 {@code 物品 → 职业 → 修饰符}；要「本通道变了，把下面这些都
     * 重来一遍」请用 {@link #dependentsTransitive()}（客户端与服务端都用它定序）。
     */
    public List<ContentChannel> dependents() {
        return switch (this) {
            // 职业的初始物品 / 任务奖励 / 商店条目按 id 查自定义物品索引
            case CUSTOM_ITEM -> List.of(CUSTOM_ROLE);
            // 修饰符的阵营 / 职业限制按 id 查已注册职业（持有 SRERole 实例，职业重注册后必须跟着重解析）
            case CUSTOM_ROLE -> List.of(CUSTOM_MODIFIER);
            case CUSTOM_BLOCK, CUSTOM_MODIFIER -> List.of();
        };
    }

    /**
     * 依赖本通道的全部通道（含间接依赖），按 {@link #LOAD_ORDER} 排列，每个只出现一次。
     *
     * <p>
     * 语义就是「本通道变了，这些都得重新注册」：客户端应用同步内容、服务端重载都用这个列表定序，
     * 避免各自写一遍递归遍历（顺序错了就是静默丢条目，值得只留一份实现）。自身不会出现在结果里。
     */
    public List<ContentChannel> dependentsTransitive() {
        Set<ContentChannel> visited = EnumSet.noneOf(ContentChannel.class);
        List<ContentChannel> pending = new ArrayList<>(dependents());
        List<ContentChannel> result = new ArrayList<>();
        while (!pending.isEmpty()) {
            ContentChannel next = pending.remove(0);
            if (!visited.add(next)) {
                continue;
            }
            result.add(next);
            pending.addAll(next.dependents());
        }
        result.sort(Comparator.comparingInt(LOAD_ORDER::indexOf));
        return List.copyOf(result);
    }

    /** 按网络包里的通道标识解析通道（未知返回 null）。 */
    public static ContentChannel byId(String id) {
        if (id == null) {
            return null;
        }
        for (ContentChannel channel : values()) {
            if (channel.id.equals(id)) {
                return channel;
            }
        }
        return null;
    }
}

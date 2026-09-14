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
 */
public enum ContentChannel {

    /** 自定义职业（sre_custom_roles.json）。 */
    CUSTOM_ROLE("custom_role", 1, "sre_custom_roles.json"),
    /** 自定义修饰符（sre_custom_modifiers.json）。 */
    CUSTOM_MODIFIER("custom_modifier", 1, "sre_custom_modifiers.json"),
    /** 自定义列车物品（sre_custom_items.json）。 */
    CUSTOM_ITEM("custom_item", 1, "sre_custom_items.json"),
    /** 自定义方块（sre_custom_blocks.json）。 */
    CUSTOM_BLOCK("custom_block", 1, "sre_custom_blocks.json");

    /** 当前全部通道共用的协议版本（握手包里带上，仅用于排查）。 */
    public static final int PROTOCOL_VERSION = 1;

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

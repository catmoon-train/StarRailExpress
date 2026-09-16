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

package io.wifi.starrailexpress.customcontent;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import io.wifi.starrailexpress.synccontent.ContentSyncServer;
import net.minecraft.server.MinecraftServer;

/**
 * 自定义内容的重载协调器：<b>被依赖的通道先重载，依赖它的通道随后重新注册</b>。
 *
 * <p>
 * 依赖关系见 {@link ContentChannel#dependents()}，实际链条是
 * {@code 自定义物品 → 自定义职业 → 自定义修饰符}：
 * 职业的初始物品 / 任务奖励 / 商店条目、修饰符的阵营与职业限制，都是在**注册时**按 id 去查已有
 * 索引的（见 {@code CustomRoleLoader#parseConfiguredItem}）。顺序错了不会抛异常，而是**静默丢
 * 条目**——职业商店里那条自定义物品根本不会加进去（不显示、也买不到），所以重载必须联动。
 *
 * <p>
 * 四个 {@code CustomXxxReloadCommand#reload(MinecraftServer)}、一键 {@code /sre:reload} 以及
 * 服务端启动都走这里，加载顺序只在 {@link ContentChannel#LOAD_ORDER} 定义一处。
 */
public final class CustomContentReload {

    private CustomContentReload() {
    }

    /**
     * 按 {@link ContentChannel#LOAD_ORDER} 重载全部通道（每个只重载一次，无需再联动，顺序本身
     * 就是依赖顺序）。
     *
     * @return 成功重载的通道数（失败项已记日志，不影响其余通道）
     */
    public static int all(MinecraftServer server) {
        int ok = 0;
        for (ContentChannel channel : ContentChannel.LOAD_ORDER) {
            if (reloadLogged(server, channel)) {
                ok++;
            }
        }
        return ok;
    }

    /**
     * 重载某通道，随后重新注册依赖它的通道（含间接依赖，按加载顺序，每个一次）。
     *
     * <p>
     * 该通道**自身**失败时异常会抛给调用方（命令据此回报失败）；依赖者的失败只记日志，不会挡住
     * 其余依赖者——尽力而为，总比整条链都不更新好。
     */
    public static void withDependents(MinecraftServer server, ContentChannel channel) {
        reloadOne(server, channel);
        for (ContentChannel dependent : channel.dependentsTransitive()) {
            reloadLogged(server, dependent);
        }
    }

    private static boolean reloadLogged(MinecraftServer server, ContentChannel channel) {
        try {
            reloadOne(server, channel);
            return true;
        } catch (Throwable e) {
            SRE.LOGGER.error("[CustomContent] Failed to reload {}", channel.id(), e);
            return false;
        }
    }

    /** 一个通道的重载：建索引 → 丢弃同步缓存 → 重新握手（顺序与依赖无关，依赖由调用方保证）。 */
    private static void reloadOne(MinecraftServer server, ContentChannel channel) {
        switch (channel) {
            case CUSTOM_ITEM -> {
                io.wifi.starrailexpress.customitem.CustomItemLoader.reload(server);
                io.wifi.starrailexpress.network.CustomItemServerNetwork.clearCache();
                io.wifi.starrailexpress.network.CustomItemServerNetwork.syncToAllPlayers(server);
            }
            case CUSTOM_BLOCK -> {
                io.wifi.starrailexpress.customblock.CustomBlockLoader.reload(server);
                ContentSyncServer.invalidate(ContentChannel.CUSTOM_BLOCK);
                ContentSyncServer.broadcastHandshake(server);
                // 冷却 / 一次性触发记录跟着配置一起失效
                io.wifi.starrailexpress.customblock.CustomBlockRuntime.invalidatePlayerState();
            }
            case CUSTOM_ROLE -> {
                io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server);
                io.wifi.starrailexpress.network.CustomRoleServerNetwork.clearCache();
                io.wifi.starrailexpress.network.CustomRoleServerNetwork.syncToAllPlayers(server);
            }
            case CUSTOM_MODIFIER -> {
                io.wifi.starrailexpress.custommodifier.CustomModifierLoader.reload(server);
                io.wifi.starrailexpress.network.CustomModifierServerNetwork.clearCache();
                io.wifi.starrailexpress.network.CustomModifierServerNetwork.syncToAllPlayers(server);
            }
        }
    }
}

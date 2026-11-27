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

package io.wifi.starrailexpress.game;

import io.wifi.ConfigCompact.network.RoleEnableInfoPacket;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * <b>本局覆盖（per-round overrides）</b>：游戏设置页里那些「只影响下一局」的数值。
 *
 * <h2>为什么需要这个类</h2>
 * 以前这两项都没有「按局设置」的地方：
 * <ul>
 * <li><b>局时长</b>只能写进 {@code /tmm:start <模式> [分钟]} 的临时参数里，开局以后谁也改不了，
 * 且默认值散落在各模式的构造函数（{@code defaultStartTime}）与配置项（{@code defaultStartTimeForMurder}）；</li>
 * <li><b>初始金币</b>只有全局配置 {@code SREConfig.startingMoney}，没有「这一局特殊一点」的表达方式。</li>
 * </ul>
 * 于是设置页只能显示配置默认值、改了也不生效。这里给它们补一层<b>服务端权威的本局状态</b>：
 * 设置页改的是它，开局时由它覆盖默认值，改完立刻同步回所有在线客户端。
 *
 * <h2>语义</h2>
 * <ul>
 * <li>时长用 {@code -1}、初始金币也用 {@code -1} 表示「跟随默认」（时长跟模式自带默认、金币跟配置）；
 * 金币特意不用 {@code 0} 当哨兵，因为「这一局一分钱不发」是个有意义的设置。
 * 设置页因此能显示「自动 (N)」并允许一键恢复。</li>
 * <li>时长只在<b>开局那一刻</b>生效（写入 {@code SREGameTimeComponent.resetTime}）；
 * 局内改它不会打断进行中的对局 —— 想改当前局请用 {@code /tmm:game time} 一类指令。</li>
 * <li>初始金币在<b>发钱那一刻</b>生效（各模式开局都会调 {@link #startingMoney()}），所以改完下一局立刻生效。</li>
 * <li>两者都<b>不落盘</b>：服务器重启后回到默认，避免「上次测试留下的 3 分钟局」一直生效。</li>
 * </ul>
 */
public final class RoundOverrides {

    /** 跟随模式默认时长。 */
    public static final int DEFAULT_MINUTES = -1;
    /**
     * 跟随配置里的初始金币。
     *
     * <p>
     * 用 {@code -1} 而不是 {@code 0}：{@code 0} 必须能表达「这一局一分钱都不发」，
     * 如果拿 0 当哨兵，就永远设不出真正的 0 金币局。
     */
    public static final int DEFAULT_COINS = -1;

    private static int minutes = DEFAULT_MINUTES;
    private static int coins = DEFAULT_COINS;

    private RoundOverrides() {
    }

    // ══════════════════════════════════════════════════════════════════
    // 读写
    // ══════════════════════════════════════════════════════════════════

    /** 本局时长覆盖（分钟）；{@link #DEFAULT_MINUTES} = 跟随模式默认。 */
    public static int minutes() {
        return minutes;
    }

    /** 本局初始金币覆盖；{@link #DEFAULT_COINS} = 跟随配置。 */
    public static int coins() {
        return coins;
    }

    /** 设置本局时长（{@code -1} = 跟随默认）。返回是否真的变了。 */
    public static boolean setMinutes(int value) {
        int clamped = value <= DEFAULT_MINUTES ? DEFAULT_MINUTES : Math.min(value, 180);
        boolean changed = clamped != minutes;
        minutes = clamped;
        return changed;
    }

    /** 设置本局初始金币（{@code -1} = 跟随配置；{@code 0} = 真的不发钱）。返回是否真的变了。 */
    public static boolean setCoins(int value) {
        int clamped = value < 0 ? DEFAULT_COINS : Math.min(value, 100000);
        boolean changed = clamped != coins;
        coins = clamped;
        return changed;
    }

    /** 两个覆盖一起清空（新开的服务器 / 重载配置时用）。 */
    public static void reset() {
        minutes = DEFAULT_MINUTES;
        coins = DEFAULT_COINS;
    }

    // ══════════════════════════════════════════════════════════════════
    // 开局时的解析
    // ══════════════════════════════════════════════════════════════════

    /**
     * 本局实际使用的时长（tick）。
     *
     * @param defaultMinutes 模式自带的默认分钟数（{@code GameMode.defaultStartTime}）
     */
    public static int resolveStartTicks(int defaultMinutes) {
        int minutesToUse = minutes > 0 ? minutes : Math.max(1, defaultMinutes);
        return GameConstants.getInTicks(minutesToUse, 0);
    }

    /**
     * 本局实际使用的初始金币。
     *
     * <p>
     * 各模式开局发钱时应调用它而不是直接读 {@code GameConstants.getMoneyStart()}：
     * 后者只看配置，看不到本局覆盖。
     */
    public static int startingMoney() {
        return coins >= 0 ? coins : Math.max(0, GameConstants.getMoneyStart());
    }

    // ══════════════════════════════════════════════════════════════════
    // 同步
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把本局覆盖写进开关快照并下发。
     *
     * <p>
     * 复用 {@code RoleEnableInfoPacket} 而不是新开一个包：设置页打开时本来就会请求一份
     * 「职业 / 修饰符开关 + 人数」快照，把这两个数顺路带上，省一个包、也省一次往返。
     * 老客户端收到不认识的 JSON 字段会直接忽略，不会报错。
     */
    public static void fill(RoleManageConfigUI.RoleAndModifierSyncInfo info) {
        info.roundMinutes = minutes;
        info.roundStartingMoney = coins;
        info.defaultStartingMoney = Math.max(0, GameConstants.getMoneyStart());
    }

    /** 给一个玩家补发一次快照（改完覆盖后立刻让他的界面生效）。 */
    public static void syncTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var packet = org.agmas.harpymodloader.commands.ListRolesCommand
                    .getRoleAndModifierEnableInfoPacket(false, player.server.getPlayerList().getPlayerCount());
            ServerPlayNetworking.send(player, packet);
        } catch (Throwable error) {
            // 同步失败不该影响「设置已经改了」这件事，记日志即可
            SRE.LOGGER.warn("Failed to sync round overrides to {}", player.getName().getString(), error);
        }
    }

    /** 广播给所有在线玩家。 */
    public static void syncToAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }
}

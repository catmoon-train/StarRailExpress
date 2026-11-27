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

package io.wifi.starrailexpress.content.vote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.roster.pool.RolePool;
import io.wifi.starrailexpress.roster.pool.RolePoolRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「职业池」投票阶段——地图投票之前的那一步。
 *
 * <p>
 * 复用通用投票系统（{@link VoteManager}），投票类型为 {@link #TYPE_ID}，
 * 选项是全部可用池子，由 {@link SREConfig#rolePoolVoteSelectCount} 决定每人可选几个。
 * 投票结束后取票数最高的前 N 个池子<b>合并</b>写入职业轮换名单
 * （{@link RolePoolRegistry#applyToRoster(java.util.Collection)}），再执行
 * {@code onFinished}（通常是接着开地图投票）。
 *
 * <p>
 * 客户端不依赖这个类：界面自己按选项的 {@code resultId}（{@code role_pool:<id>}）
 * 从 {@link RolePoolRegistry} 取池子内容。
 */
public final class RolePoolVote {

    /** 投票类型 id，客户端的 {@code ClientVoteCache#getTypeId()} 用它区分界面。 */
    public static final String TYPE_ID = "role_pool";

    private static boolean running;

    private static final Random RANDOM = new Random();

    private RolePoolVote() {
    }

    /** 职业池投票是否正在进行（用于避免重复开启）。 */
    public static boolean isRunning() {
        return running;
    }

    /**
     * 服务器停止时清掉「进行中」标记：投票会话本身会被 {@link VoteManager#clear()} 清掉，
     * 但静态标记不会，下一次开服会永远卡在「职业池投票正在进行」。
     */
    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> running = false);
    }

    /**
     * 开启职业池投票。
     *
     * @param durationSeconds 与地图投票相同的时长（秒），内部至少取 5 秒
     * @param onFinished      投票结束、池子写入名单之后执行；可为 {@code null}
     * @return 是否成功开启；已有其它投票在进行时返回 {@code false}
     */
    public static boolean start(int durationSeconds, @Nullable Runnable onFinished) {
        if (running) {
            return false;
        }
        MinecraftServer server = SRE.SERVER;
        if (server == null) {
            return false;
        }
        List<RolePool> pools = RolePoolRegistry.all();
        if (pools.isEmpty()) {
            return false;
        }
        int select = Math.max(1, Math.min(SREConfig.instance().rolePoolVoteSelectCount, pools.size()));
        VoteManager.VoteBuilder builder = VoteManager
                .builder(Component.translatable("gui.sre.role_pool_vote.title"))
                .duration(Math.max(5, durationSeconds) * 20)
                .type(TYPE_ID)
                .maxSelect(select)
                .showResults(true)
                .syncInterval(20)
                // 所有人都投完就提前进入下一阶段，不必等倒计时走完
                .endCondition(session -> {
                    int online = server.getPlayerList().getPlayers().size();
                    return online > 0 && session.getTotalVotes() >= online;
                });
        for (RolePool pool : pools) {
            builder.addOption(
                    VoteOption.text(pool.displayName(),
                            Component.translatable("gui.sre.role_pool_vote.option_hint",
                                    RolePoolRegistry.rolesOf(pool).size())),
                    pool.voteResultId());
        }
        builder.callback(session -> {
            running = false;
            List<RolePool> winners = winners(session, select);
            int roleCount = RolePoolRegistry.applyToRoster(winners);
            announce(winners, roleCount);
            if (onFinished != null) {
                onFinished.run();
            }
        });
        if (builder.start() == null) {
            return false;
        }
        running = true;
        SRE.LOGGER.info("[RolePool] 职业池投票已开始：{} 个池子，每人可选 {} 个，时长 {} 秒",
                pools.size(), select, durationSeconds);
        return true;
    }
    private static List<RolePool> winners(VoteSession session, int select) {
        List<VoteOption> options = session.getOptions();
        Map<Integer, Integer> tally = session.getIndexResults();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            counts.add(tally.getOrDefault(i, 0));
        }
        List<RolePool> result = new ArrayList<>();
        for (int index : rankedIndices(counts, select, RANDOM)) {
            RolePool pool = RolePoolRegistry.byResultId(options.get(index).resultId());
            if (pool != null) {
                result.add(pool);
            }
        }
        return result;
    }

    /**
     * 纯逻辑：按票数降序排出选项下标，取前 {@code select} 个。
     *
     * <p>
     * 先按 {@code random} 打乱再用<b>稳定排序</b>按票数降序，所以同票（含 0 票）的先后是随机的：
     * 得票不足 {@code select} 个时会用其余池子随机补齐，保证结果数量稳定、又不会每次都一样。
     *
     * @param counts 每个选项的票数（下标与投票选项一一对应）
     * @param select 取前几个
     * @param random 打乱用的随机源
     */
    static List<Integer> rankedIndices(List<Integer> counts, int select, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        order.sort(Comparator.comparingInt((Integer index) -> counts.get(index)).reversed());
        return List.copyOf(order.subList(0, Math.min(Math.max(0, select), order.size())));
    }

    private static void announce(List<RolePool> winners, int roleCount) {
        MinecraftServer server = SRE.SERVER;
        if (server == null || winners.isEmpty()) {
            return;
        }
        List<Component> names = new ArrayList<>();
        for (RolePool pool : winners) {
            names.add(pool.displayName());
        }
        Component message = Component.translatable("gui.sre.role_pool_vote.result",
                join(names), roleCount);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, false);
        }
    }

    private static Component join(List<Component> parts) {
        Component result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result = result.copy().append(Component.literal(" + "));
            }
            result = result.copy().append(parts.get(i));
        }
        return result;
    }
}

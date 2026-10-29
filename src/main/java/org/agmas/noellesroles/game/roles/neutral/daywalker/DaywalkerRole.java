package org.agmas.noellesroles.game.roles.neutral.daywalker;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.role_data.neutral.DaywalkerRoleData;
import org.agmas.noellesroles.scene.SceneTaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 逐日者（Daywalker）职业 —— 独立胜利中立阵营。
 *
 * <ul>
 * <li>中立·独立胜利（{@link RoleTeam#NEUTRAL_INDEPENDENT_WIN}），使用 teamtype 直接定义</li>
 * <li>无限体力、假心情、可见计分板</li>
 * <li>仅 12 人及以上对局刷新，刷新概率 5000/10000</li>
 * <li>其他玩家只有处于其 20 格范围内时才能透视他（见 RoleInstinctRegister）</li>
 * <li>需要完成「开局人数/3」个任务，且时间耗尽（winstate 为 time）时，代替好人获得独立胜利
 * （仅时间耗尽或仅完成任务数都不会获胜）</li>
 * <li>每完成 1 个任务：局内计时器 1 秒所需的现实时间减少 0.075 秒（至多减少 0.6 秒，
 * 即局内 1 秒 = 现实 0.4 秒 / 2.5 倍速），并储存 20 秒「逐日时刻」
 * （至多 120 秒）；释放技能立即消耗所有逐日时刻并使局内时间快进对应数值，之后进入 150 秒冷却</li>
 * <li>距胜利还剩 1 个任务时全场播放一次音效并全程发光，直至游戏结束；死亡时返还所有逐日时刻并恢复正常流速</li>
 * <li>商店可花费 200 金币购买「跳过任务」（图标为书，未刷新任务时无法购买）</li>
 * </ul>
 */
public class DaywalkerRole extends CustomWinnerRole {

    /** 商店「跳过任务」价格（金币） */
    public static final int SKIP_TASK_PRICE = 200;

    public DaywalkerRole(ResourceLocation identifier, int color, RoleTeam team,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, team, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 商店 ====================

    /**
     * 逐日者商店：200 金币购买「跳过任务」（图标为书）。
     *
     * <p>购买即生效：立即刷新当前任务（不计为完成任务，不走任务奖励流程）；
     * 未刷新任务时无法购买。双端都会调用本方法，只做数据组装，不碰服务端专用逻辑。</p>
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        ItemStack book = Items.BOOK.getDefaultInstance();
        book.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.daywalker.shop.skip_task")
                        .withStyle(ChatFormatting.GOLD));
        book.set(DataComponents.LORE, new ItemLore(
                List.of(Component.translatable("item.noellesroles.daywalker.shop.skip_task.lore1")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .withStyle(ChatFormatting.GRAY))));
        shop.add(new ShopEntry(book, SKIP_TASK_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                // 未刷新任务时无法购买：及时设置失败原因并返回 false
                // （与网警/承太郎/林家子弟等商店的条件购买写法一致）
                if (!DaywalkerRole.hasTask(player)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.daywalker.no_task"));
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                // 服务端二次校验：没有已刷新任务时购买失败，及时 return false
                // （不进入扣钱/成功音效流程，由购买管线播放失败音效并展示失败文本）
                if (!DaywalkerRole.hasTask(player)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.daywalker.no_task"));
                    return false;
                }
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                if (!DaywalkerRole.skipCurrentTask(serverPlayer)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.daywalker.no_task"));
                    return false;
                }
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.daywalker.task_skipped")
                                .withStyle(ChatFormatting.GREEN),
                        true);
                return true;
            }
        });
        return shop;
    }

    /** 玩家当前是否拥有已刷新的任务 */
    public static boolean hasTask(Player player) {
        var comp = SREPlayerTaskComponent.KEY.maybeGet(player).orElse(null);
        return comp != null && !comp.tasks.isEmpty();
    }

    /**
     * 跳过当前任务：只刷新任务本身，绝不走「任务完成」奖励流程
     * （不发任务奖励金币、不加理智——跳过不算完成任务）。
     *
     * <p>与厨师烹饪菜肴的「跳过任务」效果保持一致。</p>
     *
     * @return 是否成功跳过（没有任务时返回 false）
     */
    public static boolean skipCurrentTask(ServerPlayer player) {
        var taskComp = SREPlayerTaskComponent.KEY.maybeGet(player).orElse(null);
        // 只有当前实际拥有任务时才跳过；没有任务就什么都不做，
        // 不额外刷新出一个新任务（否则等于白送一个任务）。
        if (taskComp == null || taskComp.tasks.isEmpty()) {
            return false;
        }
        taskComp.tasks.clear();
        // 与其它「清空任务」实现保持一致：重置并列任务相关状态，
        // 否则残留的 parallelTaskGenerated 会让下一个真正完成的任务
        // 额外吃一份并列任务理智加成。
        taskComp.parallelTaskTypes.clear();
        taskComp.parallelTaskGenerated = false;
        taskComp.currentTaskAge = 0;
        // 同步清理场景任务状态：SceneTaskManager 的表不随任务组件清空，
        // 残留条目后续仍会判定「完成」并直接发放任务奖励（金币+理智）。
        SceneTaskManager.clear(player);
        TrainTask task = taskComp.generateTask();
        if (task != null) {
            taskComp.tasks.put(task.getType(), task);
            taskComp.timesGotten.putIfAbsent(task.getType(), 1);
            taskComp.timesGotten.put(task.getType(), (Integer) taskComp.timesGotten.get(task.getType()) + 1);
            var refreshedMood = SREPlayerMoodComponent.KEY.maybeGet(player).orElse(null);
            if (refreshedMood != null) {
                taskComp.moodWhenTaskAssigned = refreshedMood.getMood();
            }
        }
        taskComp.sync();
        return true;
    }

    // ==================== 任务进度 ====================

    /**
     * 完成任务：累计逐日者任务进度（获得逐日时刻并提升时间快进速率）。
     */
    @Override
    public void onFinishQuest(Player player, String quest) {
        super.onFinishQuest(player, quest);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        DaywalkerRoleData data = RoleData.getNullable(DaywalkerRoleData.class, serverPlayer);
        if (data != null) {
            data.onTaskCompleted();
        }
    }

    // ==================== 死亡返还 ====================

    /**
     * 逐日者死亡：返还所有逐日时刻（含已花费与未花费的数值的时间），
     * 并使快进的时间恢复正常（局内 1 秒 = 现实 1 秒）。
     */
    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason, boolean forceDeath) {
        if (victim instanceof ServerPlayer serverPlayer) {
            DaywalkerRoleData data = RoleData.getNullable(DaywalkerRoleData.class, serverPlayer);
            if (data != null) {
                data.refundOnDeath();
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.daywalker.death_refund")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
        }
    }

    // ==================== 独立胜利 ====================

    /**
     * 独立胜利判定：仅当时间耗尽（winstate 为 time）且已完成「开局人数/3」个任务时，
     * 逐日者代替好人获得独立胜利。
     *
     * <p>仅时间耗尽、或仅完成任务数，都不会获得胜利。</p>
     *
     * <p>调用方（{@code CustomWinnerClass.checkWinnerBuiltin}）本就只在存活玩家中调用本方法，
     * 这里仍显式校验存活：逐日者必须**存活到时间结束**才能触发独立胜利。</p>
     */
    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        if (winStatus != WinStatus.TIME) {
            return WinStatus.NOT_MODIFY;
        }
        // 必须存活到时间结束（死亡逐日者不触发独立胜利，时间耗尽按好人胜利结算）
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return WinStatus.NOT_MODIFY;
        }
        DaywalkerRoleData data = RoleData.getNullable(DaywalkerRoleData.class, player);
        if (data != null && data.requiredTasks > 0 && data.completedTasks >= data.requiredTasks) {
            return WinStatus.CUSTOM;
        }
        return WinStatus.NOT_MODIFY;
    }

    /**
     * 胜利结算：逐日者必须存活到时间结束才算获胜。
     *
     * <p>{@code isPlayerTheWinner} 对 CUSTOM 状态只按职业路径匹配、不过问存活，
     * 因此这里显式兜底：已死亡的逐日者（含被复活前/换职业等边界情况）不参与独立胜利结算。</p>
     */
    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return original;
    }
}

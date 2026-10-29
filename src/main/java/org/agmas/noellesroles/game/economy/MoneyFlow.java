package org.agmas.noellesroles.game.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 金币收支账本（仅服务端）。
 * <p>
 * 会计的「收入」/「支出」技能需要知道目标玩家最近一次的金币从哪来、花到哪去，
 * 而主模组本身不记录金币流水，因此这里维护一份轻量账本。
 * <p>
 * 记录方式两条腿：
 * <ul>
 * <li><b>分类埋点</b>：在已知的金币出入口（击杀、任务、被动、扒窃、偷窃、掠夺、存折、
 * 售货机、商店、技能）就地调用 {@link #recordIncome} / {@link #recordExpense}，
 * 给出准确的分类；这些调用必须紧挨在真正改余额的那一行之前。</li>
 * <li><b>通用兜底</b>：{@code SREPlayerShopComponent#addToBalance(int)} 会调用
 * {@link #onBalanceChange}，凡是没被上面的分类埋点「认领」的变动一律记成「其它收入 / 其它支出」。
 * 认领靠 {@link #PENDING_SKIP}：分类埋点写入游戏刻，只有同刻的余额变动才会被认领，
 * 跨刻自动失效，不会误吞后续无关的收支。</li>
 * </ul>
 * <p>
 * 性能：埋点只在真正发生金币变动时执行；{@link #onBalanceChange} 每次两次 HashMap 访问，
 * 没有逐 tick 遍历、没有额外对象分配（仅在实际写流水时创建 record）。
 */
public final class MoneyFlow {

    /** 收入来源分类 */
    public enum IncomeType {
        /** 被动收入（杀手自带、警长、富豪等随时间自然增长的金币） */
        PASSIVE,
        /** 任务收入 */
        TASK,
        /** 击杀玩家获得的金币 */
        KILL,
        /** 扒窃（扒手被动向周围玩家扒窃） */
        PICKPOCKET,
        /** 偷窃（小偷技能偷钱） */
        STEAL,
        /** 掠夺（强盗击杀额外获得） */
        PLUNDER,
        /** 存折收入（从存折里取钱） */
        PASSBOOK,
        /** 技能收入（明星等释放技能获得） */
        SKILL,
        /** 其它收入 */
        OTHER,
        /** 无收入 */
        NONE
    }

    /** 支出渠道分类 */
    public enum ExpenseType {
        /** 在商店中购买物品 */
        SHOP,
        /** 在售货机中进行购物 */
        VENDING,
        /** 技能支出（释放技能消耗金币） */
        SKILL,
        /** 被小偷偷取 */
        STOLEN,
        /** 存折支出（把钱存进存折） */
        PASSBOOK,
        /** 其它支出 */
        OTHER,
        /** 无支出 */
        NONE
    }

    /** 一条收入流水 */
    public record IncomeEntry(IncomeType type, int amount, long gameTime) {
    }

    /** 一条支出流水；{@code detail} 为可选的补充信息（如商店商品名） */
    public record ExpenseEntry(ExpenseType type, int amount, Component detail, long gameTime) {
    }

    /** 每名玩家保留的流水条数上限（防止长局无限增长） */
    private static final int MAX_ENTRIES = 16;

    private static final Map<UUID, List<IncomeEntry>> INCOME_LOG = new HashMap<>();
    private static final Map<UUID, List<ExpenseEntry>> EXPENSE_LOG = new HashMap<>();

    /**
     * 分类埋点登记的「待认领」记录：{@code 游戏刻 + 金额 + 方向}。
     * <p>
     * 价值在于精确匹配：只有同刻、同金额、同方向的余额变动才会被认领跳过，
     * 因此同刻内的其它无关收支不会被误吞。
     */
    private record Pending(long gameTime, int amount, boolean income) {
    }

    private static final Map<UUID, List<Pending>> PENDING_SKIP = new HashMap<>();

    /** 单次认领排队上限，防御异常路径堆积 */
    private static final int MAX_PENDING = 8;

    private MoneyFlow() {
    }

    // ==================== 记录（分类埋点） ====================

    /**
     * 记录一条收入流水。仅在服务端生效。
     * <p>
     * 必须在真正增加余额之前调用，同一游戏刻内紧随其后的余额变动会被它认领，
     * 从而不会被通用兜底重复记成「其它收入」。
     *
     * @param player 收入获得者
     * @param type   收入来源分类
     * @param amount 金币数量（仅作展示用，可为 0）
     */
    public static void recordIncome(Player player, IncomeType type, int amount) {
        if (player == null || type == null || type == IncomeType.NONE || player.level().isClientSide())
            return;
        List<IncomeEntry> log = INCOME_LOG.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        log.add(new IncomeEntry(type, amount, player.level().getGameTime()));
        trim(log, MAX_ENTRIES);
        registerPending(player, amount, true);
    }

    /**
     * 记录一条支出流水。仅在服务端生效。
     * <p>
     * 必须在真正扣除余额之前调用，行为与 {@link #recordIncome} 一致。
     *
     * @param player 支出者
     * @param type   支出渠道分类
     * @param amount 金币数量（仅作展示用，可为 0）
     * @param detail 补充信息，可为 null
     */
    public static void recordExpense(Player player, ExpenseType type, int amount, Component detail) {
        if (player == null || type == null || type == ExpenseType.NONE || player.level().isClientSide())
            return;
        List<ExpenseEntry> log = EXPENSE_LOG.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        log.add(new ExpenseEntry(type, amount, detail, player.level().getGameTime()));
        trim(log, MAX_ENTRIES);
        registerPending(player, -amount, false);
    }

    private static void trim(List<?> log, int max) {
        while (log.size() > max) {
            log.remove(0);
        }
    }

    // ==================== 通用兜底 ====================

    /**
     * 余额变动兜底记录。
     * <p>
     * 由 {@code SREPlayerShopComponent#addToBalance(int)} 调用：正数记收入、负数记支出。
     * 若同刻同金额同方向的分类埋点已登记，则认领跳过，不重复记账。
     */
    public static void onBalanceChange(Player player, int amount) {
        if (player == null || amount == 0 || player.level().isClientSide())
            return;
        if (claimPending(player, amount)) {
            return; // 本笔已被分类埋点认领
        }
        if (amount > 0) {
            addIncomeEntry(player, IncomeType.OTHER, amount);
        } else {
            addExpenseEntry(player, ExpenseType.OTHER, -amount, null);
        }
    }

    /** 登记一笔待认领的分类变动（{@code signedAmount} 带符号：正收入、负支出） */
    private static void registerPending(Player player, int signedAmount, boolean income) {
        List<Pending> queue = PENDING_SKIP.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        queue.add(new Pending(player.level().getGameTime(), signedAmount, income));
        trim(queue, MAX_PENDING);
    }

    /**
     * 尝试认领一笔余额变动。
     * <p>
     * 必须同刻、同金额、同方向；命中即移除并返回 true。过期（跨刻）登记会被顺手清理，
     * 因此不会误吞之后的无关收支。
     */
    private static boolean claimPending(Player player, int amount) {
        List<Pending> queue = PENDING_SKIP.get(player.getUUID());
        if (queue == null || queue.isEmpty())
            return false;
        long now = player.level().getGameTime();
        boolean claimed = false;
        for (int i = queue.size() - 1; i >= 0; i--) {
            Pending pending = queue.get(i);
            if (pending.gameTime() != now) {
                // 跨刻的登记已经失效，直接丢弃
                queue.remove(i);
                continue;
            }
            if (!claimed && pending.amount() == amount && pending.income() == (amount > 0)) {
                queue.remove(i);
                claimed = true;
            }
        }
        if (queue.isEmpty()) {
            PENDING_SKIP.remove(player.getUUID());
        }
        return claimed;
    }

    /** 内部使用：只写流水，不登记认领标记（避免自己吞掉自己） */
    private static void addIncomeEntry(Player player, IncomeType type, int amount) {
        List<IncomeEntry> log = INCOME_LOG.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        log.add(new IncomeEntry(type, amount, player.level().getGameTime()));
        trim(log, MAX_ENTRIES);
    }

    private static void addExpenseEntry(Player player, ExpenseType type, int amount, Component detail) {
        List<ExpenseEntry> log = EXPENSE_LOG.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        log.add(new ExpenseEntry(type, amount, detail, player.level().getGameTime()));
        trim(log, MAX_ENTRIES);
    }

    // ==================== 查询 ====================

    /**
     * 查询最近一次收入来源。
     * <p>
     * 规则：若存在扒窃 / 偷窃 / 掠夺记录，则优先展示其中最近的一条
     * （同一 tick 内按 扒窃 &gt; 偷窃 &gt; 掠夺 排序）；否则返回最近一条收入。
     *
     * @return 收入流水；没有任何记录时为空
     */
    public static Optional<IncomeEntry> queryIncome(Player player) {
        if (player == null || player.level().isClientSide())
            return Optional.empty();
        List<IncomeEntry> log = INCOME_LOG.get(player.getUUID());
        if (log == null || log.isEmpty())
            return Optional.empty();
        IncomeEntry theft = null;
        for (IncomeEntry entry : log) {
            if (!isTheft(entry.type()))
                continue;
            if (theft == null || isBetterTheft(entry, theft))
                theft = entry;
        }
        if (theft != null)
            return Optional.of(theft);
        return Optional.of(log.get(log.size() - 1));
    }

    private static boolean isTheft(IncomeType type) {
        return type == IncomeType.PICKPOCKET || type == IncomeType.STEAL || type == IncomeType.PLUNDER;
    }

    private static boolean isBetterTheft(IncomeEntry candidate, IncomeEntry current) {
        if (candidate.gameTime() != current.gameTime())
            return candidate.gameTime() > current.gameTime();
        return theftRank(candidate.type()) < theftRank(current.type());
    }

    private static int theftRank(IncomeType type) {
        return switch (type) {
            case PICKPOCKET -> 0;
            case STEAL -> 1;
            default -> 2;
        };
    }

    /**
     * 查询指定 tick 窗口内的支出渠道。
     * <p>
     * 规则：商店购买优先展示（并带上商品名），其余按
     * 售货机 &gt; 技能 &gt; 被小偷 &gt; 存折 &gt; 其它 排序；同级取最近一条。
     *
     * @param player   目标玩家
     * @param fromTick 窗口起点（含）
     * @param toTick   窗口终点（含）
     * @return 支出流水；窗口内没有任何记录时为空
     */
    public static Optional<ExpenseEntry> queryExpense(Player player, long fromTick, long toTick) {
        if (player == null || player.level().isClientSide())
            return Optional.empty();
        List<ExpenseEntry> log = EXPENSE_LOG.get(player.getUUID());
        if (log == null || log.isEmpty())
            return Optional.empty();
        ExpenseEntry best = null;
        for (ExpenseEntry entry : log) {
            long time = entry.gameTime();
            if (time < fromTick || time > toTick)
                continue;
            if (best == null || isBetterExpense(entry, best))
                best = entry;
        }
        return Optional.ofNullable(best);
    }

    private static boolean isBetterExpense(ExpenseEntry candidate, ExpenseEntry current) {
        int candidateRank = expenseRank(candidate.type());
        int currentRank = expenseRank(current.type());
        if (candidateRank != currentRank)
            return candidateRank < currentRank;
        return candidate.gameTime() >= current.gameTime();
    }

    private static int expenseRank(ExpenseType type) {
        return switch (type) {
            case SHOP -> 0;
            case VENDING -> 1;
            case SKILL -> 2;
            case STOLEN -> 3;
            case PASSBOOK -> 4;
            default -> 5;
        };
    }

    // ==================== 清理 ====================

    /** 清空单个玩家的流水 */
    public static void reset(UUID playerId) {
        if (playerId == null)
            return;
        INCOME_LOG.remove(playerId);
        EXPENSE_LOG.remove(playerId);
        PENDING_SKIP.remove(playerId);
    }

    /** 清空全部流水（开局 / 结束时调用） */
    public static void resetAll() {
        INCOME_LOG.clear();
        EXPENSE_LOG.clear();
        PENDING_SKIP.clear();
    }
}

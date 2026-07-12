package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 避难所电脑（{@code MinigameQuestBlockEntity}）小游戏轮换：
 * 自动扫描每支队伍避难所内的任务点方块，<b>每分钟</b>把它换成一个<b>不同</b>的小游戏并广播提示（内含 20s 限时说明）。
 * <p>
 * ⚠️「20s 内完成才算成功」的<b>奖励门控</b>发生在 io.wifi 的 {@code SREPlayerMinigameTaskComponent.onMinigameBlockCompleted}
 * 内部，外部无法在不改受保护代码的前提下强制。本系统负责轮换 + 20s 限时提示；严格奖励门控需在 io.wifi 加一个
 * 「小游戏完成事件」（或在完成处校验刷新时间戳）——见 docs TODO。
 */
public final class SixtySecondsMinigameRotation {
    public static final int ROTATE_INTERVAL = 20 * 60;   // 每分钟轮换
    public static final int SUCCESS_WINDOW = 20 * 20;    // 20s 完成窗口（提示用）

    private static final Map<ServerLevel, List<BlockPos>> BLOCKS = new WeakHashMap<>();
    /** 每个任务点上次轮换的 gameTime，用于 20s 奖励门控。 */
    private static final Map<ServerLevel, Map<BlockPos, Long>> ROTATION_TIME = new WeakHashMap<>();

    private SixtySecondsMinigameRotation() {
    }

    /**
     * 该任务点当前是否处于「20s 完成窗口」内（供 {@code MinigameQuestServerNetwork} 完成处门控）。
     * 非轮换任务点（未登记）一律放行。
     */
    public static boolean canReward(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> times = ROTATION_TIME.get(level);
        if (times == null) {
            return true;
        }
        Long rotated = times.get(pos.immutable());
        if (rotated == null) {
            return true;
        }
        return level.getGameTime() - rotated <= SUCCESS_WINDOW;
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % ROTATE_INTERVAL != 0) {
            return;
        }
        List<BlockPos> blocks = BLOCKS.computeIfAbsent(level, SixtySecondsMinigameRotation::scan);
        if (blocks.isEmpty()) {
            return;
        }
        List<QuestMinigame> all = QuestMinigames.getAll();
        if (all.isEmpty()) {
            return;
        }
        boolean rotatedAny = false;
        for (BlockPos pos : blocks) {
            if (!(level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity be)) {
                continue;
            }
            String current = be.getMinigameId();
            String next = current;
            for (int i = 0; i < 8 && (next == null || next.equals(current)); i++) {
                next = all.get(level.getRandom().nextInt(all.size())).id();
            }
            be.setMinigameId(next);
            ROTATION_TIME.computeIfAbsent(level, ignored -> new java.util.HashMap<>()).put(pos.immutable(), now);
            rotatedAny = true;
        }
        if (rotatedAny) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.minigame_refresh",
                    SUCCESS_WINDOW / 20).withStyle(ChatFormatting.AQUA));
        }
    }

    /** 扫描各队避难所范围盒内的任务点方块，缓存其坐标。 */
    private static List<BlockPos> scan(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            AABB box = team.shelterBox;
            if (box == null) {
                continue;
            }
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity) {
                    found.add(pos.immutable());
                }
            }
        }
        return found;
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    public static void reset(ServerLevel level) {
        BLOCKS.remove(level);
        ROTATION_TIME.remove(level);
    }
}

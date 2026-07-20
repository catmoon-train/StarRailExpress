package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 小游戏任务 / 游戏代币组件（当局制，每局清零）。
 * <p>
 * 小游戏任务与 Mood 任务<b>解耦</b>：本组件每 {@link SREConfig#minigameTaskIntervalSeconds} 秒
 * 独立派发一个「小游戏任务」，待办数上限为 1（不积压）。玩家可前往地图中<b>指定</b>小游戏任务点方块
 * 完成小游戏来兑现该任务并获得游戏代币；每个任务点完成后对该玩家进入复用冷却
 * （{@link SREConfig#minigameBlockCooldownSeconds} 秒），冷却到期后可再次使用，
 * 冷却到期时间记录在 {@link #blockCooldownUntil} 中。
 * 组件随 CCA 自动同步到本人客户端，因此金色标记与 HUD 无需额外网络包。
 * <p>
 * 刷新任务时会随机选取地图中实际存在的小游戏类型（{@link #targetMinigameId}），
 * 玩家必须前往<b>对应类型</b>的小游戏任务点方块才能完成该任务。
 */
public class SREPlayerMinigameTaskComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<SREPlayerMinigameTaskComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("minigame_task"), SREPlayerMinigameTaskComponent.class);

    private final Player player;

    /** 当局游戏代币。 */
    public int tokens = 0;
    /** 小游戏任务独立刷新倒计时（tick），归零时派发一个小游戏任务。 */
    public int minigameTaskTimer = 0;
    /** 已派发但尚未兑现的小游戏任务数。 */
    public int pendingMinigameTasks = 0;
    /** 当前指派的小游戏目标类型 ID（null 或空字符串表示任意）。只有同类型任务点才能完成任务。 */
    public String targetMinigameId = null;
    public String sabotageMinigameId = null;
    /** 本局各小游戏任务点对该玩家的复用冷却到期游戏刻（key 为 {@link BlockPos#asLong()}）。 */
    public final Map<Long, Long> blockCooldownUntil = new HashMap<>();

    /** 轮换模式：自上次小游戏任务派发以来刷新的普通 Mood 任务数。 */
    public int normalTasksSinceMinigame = 0;
    /** 轮换模式：再刷新多少个普通任务后轮换到小游戏任务（2~3 随机，0 表示未初始化）。 */
    public int nextMinigameAfter = 0;

    public SREPlayerMinigameTaskComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.tokens = 0;
        this.minigameTaskTimer = SREConfig.instance().minigameTaskIntervalSeconds * 20;
        this.pendingMinigameTasks = 0;
        this.targetMinigameId = null;
        this.sabotageMinigameId = null;
        this.blockCooldownUntil.clear();
        this.normalTasksSinceMinigame = 0;
        this.nextMinigameAfter = 0;
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    public int getTokens() {
        return this.tokens;
    }

    public int getPendingMinigameTasks() {
        return this.pendingMinigameTasks;
    }

    public boolean hasPendingTask() {
        return this.pendingMinigameTasks > 0;
    }

    /** 获取当前指派的目标小游戏（HUD 显示用），可能为 null。 */
    public QuestMinigame getTargetMinigame() {
        if (this.targetMinigameId == null || this.targetMinigameId.isEmpty())
            return null;
        return QuestMinigames.get(this.targetMinigameId);
    }

    public boolean hasSabotageTask() {
        return this.sabotageMinigameId != null && !this.sabotageMinigameId.isEmpty();
    }

    public QuestMinigame getSabotageMinigame() {
        if (!hasSabotageTask())
            return null;
        return QuestMinigames.get(this.sabotageMinigameId);
    }

    /** 该任务点当前是否对本玩家处于复用冷却中（冷却未到期）。 */
    public boolean isBlockUsed(BlockPos pos) {
        long until = this.blockCooldownUntil.getOrDefault(pos.asLong(), 0L);
        return this.player.level().getGameTime() < until;
    }

    /**
     * 本玩家使用该任务点后开始复用冷却（{@link SREConfig#minigameBlockCooldownSeconds} 秒）。
     * 冷却期间该点对本玩家不可再次使用、且不显示金色任务透视（各玩家独立）。
     */
    public void startBlockCooldown(BlockPos pos) {
        long cooldownTicks = (long) SREConfig.instance().minigameBlockCooldownSeconds * 20;
        this.blockCooldownUntil.put(pos.asLong(), this.player.level().getGameTime() + cooldownTicks);
        this.sync();
    }

    public void addTokens(int amount) {
        setTokens(this.tokens + amount);
    }

    public void setTokens(int amount) {
        if (this.tokens != amount) {
            this.tokens = amount;
            this.sync();
        }
    }

    /**
     * 独立计时派发小游戏任务（与 Mood 任务解耦）：
     * 仅在游戏运行中、玩家存活生存、且地图开启小游戏任务时计时，
     * 倒计时归零且当前无待办时派发一个（待办上限 1）。
     * 刷新时从地图实际存在的小游戏种类中随机选取一个作为目标类型。
     */
    @Override
    public void serverTick() {
        if (!(this.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!SREGameWorldComponent.KEY.get(serverLevel).isRunning()
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(sp)
                || !AreasWorldComponent.KEY.get(serverLevel).areasSettings.minigameQuestEnabled) {
            clearSabotageTaskIfPresent();
            return;
        }
        refreshSabotageTask(serverLevel, sp);
        // 轮换模式下小游戏任务并入 Mood 任务轮换派发（见 SREPlayerTaskComponent），不再独立计时
        if (isRotationModeActive(serverLevel)) {
            this.minigameTaskTimer = SREConfig.instance().minigameTaskIntervalSeconds * 20;
            return;
        }
        this.minigameTaskTimer--;
        if (this.minigameTaskTimer <= 0) {
            if (this.pendingMinigameTasks < 1) {
                dispatchMinigameTask(sp, serverLevel);
            }
            this.minigameTaskTimer = SREConfig.instance().minigameTaskIntervalSeconds * 20;
        }
    }

    /** 从地图中存在的小游戏种类中随机选取一个派发为待办任务，成功返回 true。 */
    private boolean dispatchMinigameTask(ServerPlayer sp, ServerLevel serverLevel) {
        var areas = AreasWorldComponent.KEY.get(serverLevel);
        if (!areas.availableMinigameIds.isEmpty()) {
            this.pendingMinigameTasks++;
            List<String> ids = new ArrayList<>(areas.availableMinigameIds);
            this.targetMinigameId = ids.get(sp.getRandom().nextInt(ids.size()));
            this.sync();
            return true;
        }
        // 兜底：没有可用小游戏种类则不指派特定种类
        this.targetMinigameId = null;
        return false;
    }

    // ───────────────────────── 轮换模式（与 Mood 任务相互替换刷新） ─────────────────────────

    /** 轮换模式是否生效：配置开启且当前地图启用小游戏任务。 */
    public static boolean isRotationModeActive(ServerLevel serverLevel) {
        return SREConfig.instance().minigameTaskRotationMode
                && AreasWorldComponent.KEY.get(serverLevel).areasSettings.minigameQuestEnabled;
    }

    /** 轮换模式：普通 Mood 任务刷新一个后计数。 */
    public void onNormalTaskGenerated(ServerPlayer sp) {
        rollNextMinigameAfterIfNeeded(sp);
        if (pendingMinigameTasks <= 0)
            this.normalTasksSinceMinigame++;
    }

    /** 轮换模式：本次刷新槽位是否应替换为小游戏任务（刷满 2~3 个普通任务且无待办时）。 */
    public boolean shouldReplaceNormalTask(ServerPlayer sp) {
        rollNextMinigameAfterIfNeeded(sp);
        return this.pendingMinigameTasks < 1 && this.normalTasksSinceMinigame >= this.nextMinigameAfter;
    }

    /** 轮换模式：在普通任务的刷新槽位上派发小游戏任务，成功后重置轮换计数。 */
    public boolean dispatchRotationTask(ServerPlayer sp, ServerLevel serverLevel) {
        if (!dispatchMinigameTask(sp, serverLevel)) {
            return false;
        }
        this.normalTasksSinceMinigame = 0;
        this.nextMinigameAfter = rollNextMinigameAfter(sp);
        return true;
    }

    private void rollNextMinigameAfterIfNeeded(ServerPlayer sp) {
        if (this.nextMinigameAfter <= 0) {
            this.nextMinigameAfter = rollNextMinigameAfter(sp);
        }
    }

    private static int rollNextMinigameAfter(ServerPlayer sp) {
        int min = io.wifi.starrailexpress.game.GameConstants.MINIGAME_ROTATION_MIN_NORMAL_TASKS;
        int max = io.wifi.starrailexpress.game.GameConstants.MINIGAME_ROTATION_MAX_NORMAL_TASKS;
        return min + sp.getRandom().nextInt(Math.max(1, max - min + 1));
    }

    private void refreshSabotageTask(ServerLevel serverLevel, ServerPlayer sp) {
        var areas = AreasWorldComponent.KEY.get(serverLevel);
        var role = SREGameWorldComponent.KEY.get(serverLevel).getRole(sp);
        boolean canUseSabotage = role != null && (role.isKiller() || role.canUseSabotage());
        if (!canUseSabotage || areas.sabotageMinigameIds.isEmpty()) {
            clearSabotageTaskIfPresent();
            return;
        }
        List<String> ids = getAvailableSabotageMinigameIds(serverLevel, areas.sabotageMinigameIds);
        if (ids.isEmpty()) {
            clearSabotageTaskIfPresent();
            return;
        }
        if (hasSabotageTask() && ids.contains(this.sabotageMinigameId)) {
            return;
        }
        this.sabotageMinigameId = ids.get(sp.getRandom().nextInt(ids.size()));
        this.sync();
    }

    private List<String> getAvailableSabotageMinigameIds(ServerLevel serverLevel, Set<String> configuredIds) {
        HashSet<String> ids = new HashSet<>();
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty()) {
            return new ArrayList<>(ids);
        }
        long now = serverLevel.getGameTime();
        for (Map.Entry<BlockPos, Integer> entry : GameUtils.taskBlocks.entrySet()) {
            int type = entry.getValue();
            if (type != 14 && type != 15) {
                continue;
            }
            if (!(serverLevel.getBlockEntity(entry.getKey()) instanceof MinigameQuestBlockEntity questBe)
                    || !questBe.isSabotageTrigger()
                    || questBe.isSabotageOnCooldown(now)) {
                continue;
            }
            String minigameId = questBe.getMinigameId();
            if (minigameId != null && !minigameId.isEmpty() && configuredIds.contains(minigameId)) {
                ids.add(minigameId);
            }
        }
        return new ArrayList<>(ids);
    }

    private void clearSabotageTaskIfPresent() {
        if (this.sabotageMinigameId != null) {
            this.sabotageMinigameId = null;
            this.sync();
        }
    }

    /**
     * 完成某个小游戏方块时调用：若玩家有待办小游戏任务则兑现一个任务并发放代币。
     * 复用冷却已在打开任务点（{@link #startBlockCooldown}）时开始，此处不再处理。
     * <p>
     * 仅当该任务点的 minigameId 与 {@link #targetMinigameId} 匹配（或 targetMinigameId
     * 为空/任意）时才发放奖励。
     *
     * @param sp              玩家
     * @param pos             任务点位置
     * @param reward          基础奖励
     * @param blockMinigameId 该任务点方块的 minigameId
     * @return 是否发放了奖励
     */
    public boolean onMinigameBlockCompleted(ServerPlayer sp, BlockPos pos, int reward, String blockMinigameId) {
        if (this.pendingMinigameTasks <= 0) {
            return false;
        }
        // 校验小游戏类型匹配：若有指定目标，必须匹配
        if (this.targetMinigameId != null && !this.targetMinigameId.isEmpty()
                && !this.targetMinigameId.equals(blockMinigameId)) {
            return false;
        }
        this.pendingMinigameTasks--;
        this.targetMinigameId = null; // 完成后清除目标，等待下次刷新
        addTokens(reward);
        // 轮换模式：完成小游戏任务额外获得金币奖励
        if (sp.level() instanceof ServerLevel serverLevel && isRotationModeActive(serverLevel)) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
            if (shop != null && !SREConfig.instance().minigameTaskParallelMode) {
                shop.addToBalance(SREConfig.instance().minigameRotationCoinBonus);
            }
        }
        this.sync();
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Tokens", this.tokens);
        tag.putInt("Pending", this.pendingMinigameTasks);
        if (this.targetMinigameId != null) {
            tag.putString("TargetMinigameId", this.targetMinigameId);
        }
        if (this.sabotageMinigameId != null) {
            tag.putString("SabotageMinigameId", this.sabotageMinigameId);
        }
        long[] keys = new long[this.blockCooldownUntil.size()];
        long[] until = new long[this.blockCooldownUntil.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : this.blockCooldownUntil.entrySet()) {
            keys[i] = e.getKey();
            until[i] = e.getValue();
            i++;
        }
        tag.putLongArray("CdKeys", keys);
        tag.putLongArray("CdUntil", until);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.tokens = tag.getInt("Tokens");
        this.pendingMinigameTasks = tag.getInt("Pending");
        this.targetMinigameId = tag.contains("TargetMinigameId") ? tag.getString("TargetMinigameId") : null;
        if (this.targetMinigameId != null && this.targetMinigameId.isEmpty()) {
            this.targetMinigameId = null;
        }
        this.sabotageMinigameId = tag.contains("SabotageMinigameId") ? tag.getString("SabotageMinigameId") : null;
        if (this.sabotageMinigameId != null && this.sabotageMinigameId.isEmpty()) {
            this.sabotageMinigameId = null;
        }
        this.blockCooldownUntil.clear();
        long[] keys = tag.getLongArray("CdKeys");
        long[] until = tag.getLongArray("CdUntil");
        int n = Math.min(keys.length, until.length);
        for (int i = 0; i < n; i++) {
            this.blockCooldownUntil.put(keys[i], until[i]);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 当局制，无需持久化
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 当局制，无需持久化
    }
}

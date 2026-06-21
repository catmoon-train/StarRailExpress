package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
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

import java.util.HashMap;
import java.util.Map;

/**
 * 小游戏任务 / 游戏代币组件（当局制，每局清零）。
 * <p>
 * 小游戏任务与 Mood 任务<b>解耦</b>：本组件每 {@link SREConfig#minigameTaskIntervalSeconds} 秒
 * 独立派发一个「小游戏任务」，待办数上限为 1（不积压）。玩家可前往地图中<b>任意</b>小游戏任务点方块
 * 完成小游戏来兑现该任务并获得游戏代币；每个任务点完成后对该玩家进入复用冷却
 * （{@link SREConfig#minigameBlockCooldownSeconds} 秒），冷却到期后可再次使用，
 * 冷却到期时间记录在 {@link #blockCooldownUntil} 中。
 * 组件随 CCA 自动同步到本人客户端，因此金色标记与 HUD 无需额外网络包。
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
    /** 本局各小游戏任务点对该玩家的复用冷却到期游戏刻（key 为 {@link BlockPos#asLong()}）。 */
    public final Map<Long, Long> blockCooldownUntil = new HashMap<>();

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
        this.blockCooldownUntil.clear();
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

    /** 该任务点当前是否对本玩家处于复用冷却中（冷却未到期）。 */
    public boolean isBlockUsed(BlockPos pos) {
        long until = this.blockCooldownUntil.getOrDefault(pos.asLong(), 0L);
        return this.player.level().getGameTime() < until;
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
     */
    @Override
    public void serverTick() {
        if (!(this.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!SREGameWorldComponent.KEY.get(serverLevel).isRunning()
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(sp)
                || !AreasWorldComponent.KEY.get(serverLevel).minigameQuestEnabled) {
            return;
        }
        this.minigameTaskTimer--;
        if (this.minigameTaskTimer <= 0) {
            if (this.pendingMinigameTasks < 1) {
                this.pendingMinigameTasks++;
                this.sync();
            }
            this.minigameTaskTimer = SREConfig.instance().minigameTaskIntervalSeconds * 20;
        }
    }

    /**
     * 完成某个小游戏方块时调用：若玩家有待办小游戏任务且该方块本局未被本玩家用过，
     * 则兑现一个任务、标记该方块已用并发放代币。
     *
     * @return 是否发放了奖励
     */
    public boolean onMinigameBlockCompleted(ServerPlayer sp, BlockPos pos, int reward) {
        if (this.pendingMinigameTasks <= 0) {
            return false;
        }
        long key = pos.asLong();
        if (isBlockUsed(pos)) {
            // 该任务点对本玩家仍在复用冷却中
            return false;
        }
        // 标记该任务点进入复用冷却
        long cooldownTicks = (long) SREConfig.instance().minigameBlockCooldownSeconds * 20;
        this.blockCooldownUntil.put(key, sp.level().getGameTime() + cooldownTicks);
        this.pendingMinigameTasks--;
        addTokens(reward);
        this.sync();
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Tokens", this.tokens);
        tag.putInt("Pending", this.pendingMinigameTasks);
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

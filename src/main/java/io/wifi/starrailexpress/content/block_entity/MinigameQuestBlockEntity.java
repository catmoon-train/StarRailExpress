package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.network.MinigameQuestServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 小游戏任务点方块的BlockEntity
 * 存储选中的小游戏类型、任务路标颜色等配置
 */
public class MinigameQuestBlockEntity extends SyncingBlockEntity {

    private String minigameId = QuestMinigames.getDefaultId();
    private int markerColor = 0xFFD700; // 默认金色边框
    private boolean isTaskMarker = true; // 默认作为任务路标
    private boolean isSabotageTrigger = false; // 是否破坏任务触发点
    private int sabotageDuration = 60; // 破坏任务持续时间（秒），默认1分钟
    private int sabotageCooldown = 300; // 破坏任务冷却（秒），默认5分钟
    private long lastSabotageTime = 0; // 上次触发破坏任务的游戏时间（tick）

    public MinigameQuestBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.MINIGAME_QUEST, pos, state);
    }

    // ══════════════════════════════════════════
    // Getters & Setters
    // ══════════════════════════════════════════

    public String getMinigameId() { return minigameId; }

    public void setMinigameId(String id) {
        this.minigameId = id;
        setChanged();
    }

    public QuestMinigame getSelectedMinigame() {
        return QuestMinigames.get(minigameId);
    }

    public int getMarkerColor() { return markerColor; }

    public void setMarkerColor(int color) {
        this.markerColor = color;
        setChanged();
    }

    public boolean isTaskMarker() { return isTaskMarker; }

    public void setTaskMarker(boolean marker) {
        this.isTaskMarker = marker;
        setChanged();
    }

    public boolean isSabotageTrigger() { return isSabotageTrigger; }

    public void setSabotageTrigger(boolean v) {
        this.isSabotageTrigger = v;
        setChanged();
    }

    public int getSabotageDuration() { return sabotageDuration; }

    public void setSabotageDuration(int seconds) {
        this.sabotageDuration = Math.max(1, seconds);
        setChanged();
    }

    public int getSabotageCooldown() { return sabotageCooldown; }

    public void setSabotageCooldown(int seconds) {
        this.sabotageCooldown = Math.max(0, seconds);
        setChanged();
    }

    public long getLastSabotageTime() { return lastSabotageTime; }
    public void setLastSabotageTime(long time) { this.lastSabotageTime = time; sync(); }

    public boolean isSabotageOnCooldown(long currentGameTime) {
        long cooldownTicks = (long) this.sabotageCooldown * 20;
        return cooldownTicks > 0 && this.lastSabotageTime > 0
                && currentGameTime - this.lastSabotageTime < cooldownTicks;
    }

    /** 从网络包加载配置 */
    public void loadConfigFromTag(CompoundTag tag) {
        if (tag.contains("MinigameId")) {
            this.minigameId = tag.getString("MinigameId");
        }
        if (tag.contains("MarkerColor")) {
            this.markerColor = tag.getInt("MarkerColor");
        }
        if (tag.contains("IsTaskMarker")) {
            this.isTaskMarker = tag.getBoolean("IsTaskMarker");
        }
        if (tag.contains("IsSabotageTrigger")) {
            this.isSabotageTrigger = tag.getBoolean("IsSabotageTrigger");
        }
        if (tag.contains("SabotageDuration")) {
            this.sabotageDuration = tag.getInt("SabotageDuration");
        }
        if (tag.contains("SabotageCooldown")) {
            this.sabotageCooldown = tag.getInt("SabotageCooldown");
        }
        setChanged();
    }

    /** 打开配置界面（仅创造模式） */
    public void openConfigUI(ServerPlayer player) {
        MinigameQuestServerNetwork.sendOpenConfig(player, this.worldPosition, this);
    }

    // ══════════════════════════════════════════
    // NBT 序列化
    // ══════════════════════════════════════════

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("MinigameId", minigameId);
        tag.putInt("MarkerColor", markerColor);
        tag.putBoolean("IsTaskMarker", isTaskMarker);
        tag.putBoolean("IsSabotageTrigger", isSabotageTrigger);
        tag.putInt("SabotageDuration", sabotageDuration);
        tag.putInt("SabotageCooldown", sabotageCooldown);
        tag.putLong("LastSabotageTime", lastSabotageTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MinigameId")) {
            this.minigameId = tag.getString("MinigameId");
        }
        if (tag.contains("MarkerColor")) {
            this.markerColor = tag.getInt("MarkerColor");
        }
        if (tag.contains("IsTaskMarker")) {
            this.isTaskMarker = tag.getBoolean("IsTaskMarker");
        }
        if (tag.contains("IsSabotageTrigger")) {
            this.isSabotageTrigger = tag.getBoolean("IsSabotageTrigger");
        }
        if (tag.contains("SabotageDuration")) {
            this.sabotageDuration = tag.getInt("SabotageDuration");
        }
        if (tag.contains("SabotageCooldown")) {
            this.sabotageCooldown = tag.getInt("SabotageCooldown");
        }
        if (tag.contains("LastSabotageTime")) {
            this.lastSabotageTime = tag.getLong("LastSabotageTime");
        }
    }
}

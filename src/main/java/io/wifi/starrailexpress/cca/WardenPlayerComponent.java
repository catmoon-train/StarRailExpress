
package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.WardenRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import io.wifi.starrailexpress.index.TMMItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 典狱长玩家组件
 * 
 * 管理典狱长的状态数据：
 * - 审判目标UUID
 * - 是否进入正义审判阶段
 * - 审判阶段击杀数
 * - 凶手UUID（击杀目标的凶手）
 * - 正义反噬标记
 */
public class WardenPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WardenPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    org.agmas.noellesroles.Noellesroles.MOD_ID, "warden"),
            WardenPlayerComponent.class);

    private final Player player;

    // 审判目标UUID（正义戒律技能选中的玩家）
    @Nullable
    private UUID targetUuid = null;

    // 审判目标名字（用于HUD显示）
    private String targetName = "";

    // 目标是否已死亡（用于HUD显示）
    private boolean targetDead = false;

    // 是否进入正义审判阶段
    private boolean inJudgment = false;

    // 审判阶段击杀数
    private int judgmentKills = 0;

    // 击杀目标的凶手UUID（当目标被杀手/中立击杀时记录）
    @Nullable
    private UUID murdererUuid = null;

    // 正义反噬标记（击杀数达到上限但未击杀凶手时死亡）
    private boolean backfired = false;

    // 典狱长是否已胜利
    private boolean hasWon = false;

    // 审判阶段最大击杀数（缓存，由总人数计算）
    private int maxJudgmentKills = 0;

    // 技能冷却结束时间戳（gameTime）
    private long skillCooldownEnd = 0;

    // 商店购买冷却结束时间戳（gameTime）
    private long shopCooldownEnd = 0;

    // 技能冷却剩余tick数（用于客户端HUD显示，每tick递减）
    private long skillCooldownTicks = 0;

    public WardenPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    // ==================== Getters/Setters ====================

    @Nullable
    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isTargetDead() {
        return targetDead;
    }

    public void setTargetUuid(@Nullable UUID targetUuid) {
        this.targetUuid = targetUuid;
        this.sync();
    }

    public boolean isInJudgment() {
        return inJudgment;
    }

    public void setInJudgment(boolean inJudgment) {
        this.inJudgment = inJudgment;
        this.sync();
    }

    public int getJudgmentKills() {
        return judgmentKills;
    }

    public void setJudgmentKills(int judgmentKills) {
        this.judgmentKills = judgmentKills;
        this.sync();
    }

    public void addJudgmentKill() {
        this.judgmentKills++;
        this.sync();
    }

    @Nullable
    public UUID getMurdererUuid() {
        return murdererUuid;
    }

    public void setMurdererUuid(@Nullable UUID murdererUuid) {
        this.murdererUuid = murdererUuid;
        this.sync();
    }

    public boolean isBackfired() {
        return backfired;
    }

    public void setBackfired(boolean backfired) {
        this.backfired = backfired;
        this.sync();
    }

    public boolean hasWon() {
        return hasWon;
    }

    public void setHasWon(boolean hasWon) {
        this.hasWon = hasWon;
        this.sync();
    }

    public int getMaxJudgmentKills() {
        return maxJudgmentKills;
    }

    public void setMaxJudgmentKills(int maxJudgmentKills) {
        this.maxJudgmentKills = maxJudgmentKills;
        this.sync();
    }

    public long getSkillCooldownEnd() {
        return skillCooldownEnd;
    }

    public void setSkillCooldownEnd(long skillCooldownEnd) {
        this.skillCooldownEnd = skillCooldownEnd;
        // 同步更新skillCooldownTicks
        if (player instanceof ServerPlayer sp) {
            long remaining = skillCooldownEnd - sp.level().getGameTime();
            this.skillCooldownTicks = Math.max(0, remaining);
        }
        this.sync();
    }

    public long getShopCooldownEnd() {
        return shopCooldownEnd;
    }

    public void setShopCooldownEnd(long shopCooldownEnd) {
        this.shopCooldownEnd = shopCooldownEnd;
        this.sync();
    }

    public boolean isShopReady() {
        if (!(player instanceof ServerPlayer sp)) return false;
        return shopCooldownEnd <= sp.level().getGameTime();
    }

    public long getSkillCooldownTicks() {
        return skillCooldownTicks;
    }

    public void setSkillCooldownTicks(long skillCooldownTicks) {
        this.skillCooldownTicks = skillCooldownTicks;
        this.sync();
    }

    /**
     * 检查技能是否冷却完毕
     */
    public boolean isSkillReady() {
        if (player.level() instanceof ServerLevel sl) {
            return sl.getGameTime() >= skillCooldownEnd;
        }
        return true;
    }

    /**
     * 计算审判阶段最大击杀数：总人数÷6+1（向下取整+1）
     */
    public int calculateMaxJudgmentKills() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        int playerCount = gameWorldComponent.getStartingPlayerCount();
        if (playerCount <= 0) {
            playerCount = (int) player.level().players().stream()
                    .filter(GameUtils::isPlayerAliveAndSurvivalIgnoreShitSplit)
                    .count();
        }
        return playerCount / 6 + 1;
    }

    // ==================== 业务逻辑 ====================

    /**
     * 使用正义戒律技能，对3格内目标使用
     * @param target 目标玩家
     * @return 是否成功使用
     */
    public boolean useJusticeVerdict(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (!isSkillReady()) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) return false;

        // 检查距离（3格内）
        double distance = sp.distanceTo(target);
        if (distance > 3.0) return false;

        // 设置目标
        this.targetUuid = target.getUUID();
        this.targetName = target.getName().getString();
        this.targetDead = false;
        // 设置冷却60秒
        this.skillCooldownEnd = player.level().getGameTime() + 60 * 20;
        this.skillCooldownTicks = 60 * 20;

        // 给典狱长发送提示
        sp.displayClientMessage(
                Component.translatable("message.warden.verdict_used", target.getName())
                        .withStyle(style -> style.withColor(0x0044CC)),
                true);

        this.sync();
        return true;
    }

    /**
     * 当正义戒律的目标击杀了其他玩家时，典狱长受到惩罚
     * @param wardenPlayer 典狱长玩家
     */
    public void onTargetMisbehaved(ServerPlayer wardenPlayer) {
        if (targetUuid == null) return;
        if (inJudgment) return;

        // 典狱长获得10秒缓慢5
        wardenPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                10 * 20, // 10秒
                4,       // 等级5（0-based，所以4=缓慢V）
                false, true, true
        ));

        // 技能重新进入冷却60秒
        skillCooldownEnd = wardenPlayer.level().getGameTime() + 60 * 20;
        skillCooldownTicks = 60 * 20;

        // 典狱长提示
        wardenPlayer.displayClientMessage(
                Component.translatable("message.warden.target_misbehaved")
                        .withStyle(style -> style.withColor(0xFFAA00)),
                true);

        // 清除目标（目标失去正义戒律效果）
        targetUuid = null;
        targetName = "";
        targetDead = false;
        this.sync();
    }

    /**
     * 当目标被击杀时检查，若凶手为杀手或中立则进入正义审判阶段
     * @param killer 击杀者
     */
    public void onTargetKilled(@Nullable Player killer) {
        if (targetUuid == null) return;
        if (inJudgment) return; // 已经在审判阶段

        if (killer == null) return;
        // 典狱长自己击杀目标不触发审判
        if (killer.getUUID().equals(player.getUUID())) return;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(killer.level());
        SRERole killerRole = gameWorldComponent.getRole(killer);

        // 凶手为杀手或中立（对杀手中立）时触发审判，排除典狱长自己
        if (killerRole != null && (killerRole.canUseKiller() || killerRole.isNeutralForKiller())
                && killerRole != org.agmas.noellesroles.role.ModRoles.WARDEN) {
            this.murdererUuid = killer.getUUID();
            this.targetDead = true;
            this.inJudgment = true;
            this.maxJudgmentKills = calculateMaxJudgmentKills();
            this.judgmentKills = 0;

            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.warden.judgment_started")
                                .withStyle(style -> style.withColor(0xFF4444)),
                        true);
                sp.displayClientMessage(
                        Component.translatable("message.warden.judgment_kill_limit", maxJudgmentKills)
                                .withStyle(style -> style.withColor(0xFF4444)),
                        true);
                // 将假左轮手枪替换为德林加手枪
                for (int i = 0; i < sp.getInventory().items.size(); i++) {
                    ItemStack stack = sp.getInventory().items.get(i);
                    if (stack.is(org.agmas.noellesroles.init.ModItems.FAKE_REVOLVER)) {
                        sp.getInventory().items.set(i, new ItemStack(TMMItems.DERRINGER));
                        break;
                    }
                }
            }

            this.sync();
        }
    }

    /**
     * 审判阶段击杀玩家后检查
     * @param victim 被击杀的玩家
     */
    public void onJudgmentKill(ServerPlayer victim) {
        if (!inJudgment) return;

        addJudgmentKill();

        // 检查是否击杀了凶手
        if (murdererUuid != null && victim.getUUID().equals(murdererUuid)) {
            // 成功击杀凶手，典狱长胜利！
            hasWon = true;
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.warden.judgment_success")
                                .withStyle(style -> style.withColor(0x44FF44)),
                        false);
            }
            // 触发典狱长独立胜利
            RoleUtils.customWinnerWin(
                    (ServerLevel) player.level(),
                    org.agmas.noellesroles.role.ModRoles.WARDEN.identifier().getPath(),
                    org.agmas.noellesroles.role.ModRoles.WARDEN.color()
            );
            return;
        }

        // 检查是否达到击杀上限
        if (judgmentKills >= maxJudgmentKills) {
            // 正义反噬，典狱长死亡
            backfired = true;
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.warden.judgment_backfire")
                                .withStyle(style -> style.withColor(0xFF0000)),
                        false);
                // 强制杀死典狱长
                GameUtils.killPlayer(sp, true, null, SRE.id("warden_backfire"), true);
            }
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        // 递减技能冷却tick
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
            if (skillCooldownTicks % 20 == 0) {
                sync(); // 每秒同步一次
            }
        }

        // 检测审判目标是否已死亡（非审判阶段时，且GameMode未触发onTargetKilled的情况）
        if (!inJudgment && targetUuid != null) {
            ServerPlayer targetPlayer = sp.server.getPlayerList().getPlayer(targetUuid);
            if (targetPlayer == null || !GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                // 目标已死亡但GameMode未触发审判（可能是自然死亡、离线等），清除目标
                targetUuid = null;
                targetName = "";
                targetDead = false;
                sp.displayClientMessage(
                        Component.translatable("message.warden.target_died")
                                .withStyle(style -> style.withColor(0xFFAA00)),
                        false);
                sync();
            }
        }
    }

    // ==================== 序列化 ====================

    @Override
    public void init() {
        this.targetUuid = null;
        this.targetName = "";
        this.targetDead = false;
        this.inJudgment = false;
        this.judgmentKills = 0;
        this.murdererUuid = null;
        this.backfired = false;
        this.hasWon = false;
        this.maxJudgmentKills = 0;
        this.skillCooldownEnd = 0;
        this.shopCooldownEnd = 0;
        this.skillCooldownTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (targetUuid != null) tag.putUUID("TargetUuid", targetUuid);
        tag.putString("TargetName", targetName);
        tag.putBoolean("TargetDead", targetDead);
        tag.putBoolean("InJudgment", inJudgment);
        tag.putInt("JudgmentKills", judgmentKills);
        if (murdererUuid != null) tag.putUUID("MurdererUuid", murdererUuid);
        tag.putBoolean("Backfired", backfired);
        tag.putBoolean("HasWon", hasWon);
        tag.putInt("MaxJudgmentKills", maxJudgmentKills);
        tag.putLong("SkillCooldownEnd", skillCooldownEnd);
        tag.putLong("ShopCooldownEnd", shopCooldownEnd);
        tag.putLong("SkillCooldownTicks", skillCooldownTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.targetUuid = tag.contains("TargetUuid") ? tag.getUUID("TargetUuid") : null;
        this.targetName = tag.getString("TargetName");
        this.targetDead = tag.getBoolean("TargetDead");
        this.inJudgment = tag.getBoolean("InJudgment");
        this.judgmentKills = tag.getInt("JudgmentKills");
        this.murdererUuid = tag.contains("MurdererUuid") ? tag.getUUID("MurdererUuid") : null;
        this.backfired = tag.getBoolean("Backfired");
        this.hasWon = tag.getBoolean("HasWon");
        this.maxJudgmentKills = tag.getInt("MaxJudgmentKills");
        this.skillCooldownEnd = tag.getLong("SkillCooldownEnd");
        this.shopCooldownEnd = tag.getLong("ShopCooldownEnd");
        this.skillCooldownTicks = tag.getLong("SkillCooldownTicks");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (targetUuid != null) tag.putUUID("T", targetUuid);
        tag.putString("TN", targetName);
        tag.putBoolean("TD", targetDead);
        tag.putBoolean("J", inJudgment);
        tag.putInt("JK", judgmentKills);
        if (murdererUuid != null) tag.putUUID("M", murdererUuid);
        tag.putBoolean("B", backfired);
        tag.putBoolean("W", hasWon);
        tag.putInt("MJK", maxJudgmentKills);
        tag.putLong("SCD", skillCooldownEnd);
        tag.putLong("SHCD", shopCooldownEnd);
        tag.putLong("SCT", skillCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.targetUuid = tag.contains("T") ? tag.getUUID("T") : null;
        this.targetName = tag.getString("TN");
        this.targetDead = tag.getBoolean("TD");
        this.inJudgment = tag.getBoolean("J");
        this.judgmentKills = tag.getInt("JK");
        this.murdererUuid = tag.contains("M") ? tag.getUUID("M") : null;
        this.backfired = tag.getBoolean("B");
        this.hasWon = tag.getBoolean("W");
        this.maxJudgmentKills = tag.getInt("MJK");
        this.skillCooldownEnd = tag.getLong("SCD");
        this.shopCooldownEnd = tag.getLong("SHCD");
        this.skillCooldownTicks = tag.getLong("SCT");
    }
}

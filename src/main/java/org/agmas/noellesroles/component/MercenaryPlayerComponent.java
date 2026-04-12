package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Locale;
import java.util.UUID;

public class MercenaryPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<MercenaryPlayerComponent> KEY = ModComponents.MERCENARY;

    private static final int BASE_IDLE_SHIELDS = 2;
    private static final int GLOW_REFRESH_TICKS = 40;
    private static final int CONTRACT_COOLDOWN_TICKS = 90 * 20; // 90秒冷却

    private final Player player;

    public UUID contractTargetUuid;
    public String contractTargetName = "";
    public UUID employerUuid;
    public String employerName = "";
    public boolean contractActive = false;

    public UUID forcedTargetUuid;
    public String forcedTargetName = "";

    public int contractKillCount = 0;
    public int requiredContractKills = 1;

    // 合约冷却（tick），>0 时不能接新的合约
    public int contractCooldown = 0;

    public int bonusShields = 0;
    public boolean boughtShieldThisContract = false;
    private boolean idleShieldsGranted = false;

    public MercenaryPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        contractTargetUuid = null;
        contractTargetName = "";
        employerUuid = null;
        employerName = "";
        forcedTargetUuid = null;
        forcedTargetName = "";
        contractActive = false;
        contractKillCount = 0;
        bonusShields = 0;
        boughtShieldThisContract = false;
        idleShieldsGranted = false;
        // 基于开局玩家人数计算所需击杀数；若不可用则回退到当前在线玩家数
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        int basePlayers = (gwc != null && gwc.getStartingPlayerCount() > 0)
                ? gwc.getStartingPlayerCount()
                : player.level().players().size();
        requiredContractKills = Math.max(1, basePlayers / 10 + 1);
        contractCooldown = 0;
        enterIdleState(true);
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    public boolean isContractTarget(Player target) {
        return contractActive && matchesTrackedName(contractTargetName, target);
    }

    public boolean isForcedTarget(Player target) {
        return matchesTrackedName(forcedTargetName, target);
    }

    private static boolean matchesTrackedName(String trackedName, Player target) {
        if (target == null || trackedName == null || trackedName.isBlank()) {
            return false;
        }
        String expected = normalizeName(trackedName);
        String score = normalizeName(target.getScoreboardName());
        return expected.equals(score);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public boolean canAttackTarget(Player target) {
        if (target == null) {
            return false;
        }
        return isContractTarget(target) || isForcedTarget(target);
    }

    public void setForcedTarget(Player target) {
        if (target == null || target == player) {
            return;
        }
        this.forcedTargetUuid = target.getUUID();
        this.forcedTargetName = target.getScoreboardName();
        sync();
    }

    public boolean startContract(UUID employer, String employerDisplay, UUID target, String targetDisplay) {
        if (contractCooldown > 0) {
            if (player instanceof ServerPlayer sp) {
                int secs = (contractCooldown + 19) / 20;
                sp.displayClientMessage(Component.translatable("message.noellesroles.mercenary.cooldown", secs)
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        if (contractActive || target == null || target.equals(player.getUUID())) {
            return false;
        }
        this.employerUuid = employer;
        this.employerName = employerDisplay == null ? "" : employerDisplay;
        this.contractTargetUuid = target;
        Player liveTarget = player.level().getPlayerByUUID(target);
        this.contractTargetName = liveTarget != null ? liveTarget.getScoreboardName()
                : (targetDisplay == null ? "" : targetDisplay);
        this.contractActive = true;
        this.boughtShieldThisContract = false;

        enterContractState();

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.contract_started", contractTargetName)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
        sync();
        return true;
    }

    public void onContractTargetKilled() {
        if (!contractActive) {
            return;
        }
        contractKillCount++;

        contractActive = false;
        contractTargetUuid = null;
        contractTargetName = "";
        employerUuid = null;
        employerName = "";
        boughtShieldThisContract = false;

        clearBonusShieldsAfterContractKill();
        // 任务完成后进入冷却
        contractCooldown = CONTRACT_COOLDOWN_TICKS;
        enterIdleState(false);

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.contract_finished",
                            contractKillCount,
                            requiredContractKills)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        sync();
    }

    public void onContractTargetLost() {
        if (!contractActive) {
            return;
        }
        contractActive = false;
        contractTargetUuid = null;
        contractTargetName = "";
        employerUuid = null;
        employerName = "";
        boughtShieldThisContract = false;
        enterIdleState(false);
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.contract_lost")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        sync();
    }

    public boolean canBuyContractShield() {
        return contractActive && !boughtShieldThisContract;
    }

    public boolean onBoughtShieldLayer() {
        if (!canBuyContractShield()) {
            return false;
        }
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        armor.giveArmor();
        bonusShields++;
        boughtShieldThisContract = true;
        sync();
        return true;
    }

    public boolean canFollowFactionWin(GameUtils.WinStatus winStatus) {
        if (winStatus != GameUtils.WinStatus.KILLERS && winStatus != GameUtils.WinStatus.PASSENGERS) {
            return false;
        }
        return contractKillCount >= requiredContractKills;
    }

    private void clearBonusShieldsAfterContractKill() {
        if (bonusShields <= 0) {
            return;
        }
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        int remove = Math.min(bonusShields, Math.max(armor.getArmor(), 0));
        if (remove > 0) {
            armor.removeArmor(remove);
        }
        bonusShields = 0;
    }

    private void enterContractState() {
        idleShieldsGranted = false;
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        int current = Math.max(armor.getArmor(), 0);
        if (current > 0) {
            armor.removeArmor(current);
        }
        player.removeEffect(MobEffects.GLOWING);
    }

    private void enterIdleState(boolean forceGrant) {
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        if (forceGrant || !idleShieldsGranted) {
            int current = Math.max(armor.getArmor(), 0);
            if (current < BASE_IDLE_SHIELDS) {
                for (int i = 0; i < BASE_IDLE_SHIELDS - current; i++) {
                    armor.addArmor();
                }
            } else if (current > BASE_IDLE_SHIELDS) {
                armor.removeArmor(current - BASE_IDLE_SHIELDS);
            }
            idleShieldsGranted = true;
        }
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_REFRESH_TICKS, 0, false, false, false));
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.MERCENARY)) {
            return;
        }
        if (!gwc.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 动态使用开局人数保持一致性（避免中途玩家离开影响目标要求）
        int basePlayers = (gwc != null && gwc.getStartingPlayerCount() > 0)
                ? gwc.getStartingPlayerCount()
                : player.level().players().size();
        requiredContractKills = Math.max(1, basePlayers / 10 + 1);

        // 处理合约冷却
        if (contractCooldown > 0) {
            contractCooldown--;
            if (contractCooldown % 20 == 0 || contractCooldown == 0) {
                sync();
            }
        }

        if (!contractActive) {
            enterIdleState(false);
            if (forcedTargetUuid != null) {
                Player forcedTarget = player.level().getPlayerByUUID(forcedTargetUuid);
                if (forcedTarget == null || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(forcedTarget)) {
                    forcedTargetUuid = null;
                    forcedTargetName = "";
                    sync();
                }
            }
            return;
        }

        Player contractTarget = player.level().getPlayerByUUID(contractTargetUuid);
        if (contractTarget == null || !GameUtils.isPlayerAliveAndSurvival(contractTarget)) {
            onContractTargetLost();
            return;
        }
        // 不再在服务器端给通缉目标添加发光效果（改为客户端仅对雇佣兵渲染高亮）
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("contractActive", contractActive);
        tag.putInt("contractKillCount", contractKillCount);
        tag.putInt("requiredContractKills", requiredContractKills);
        tag.putInt("bonusShields", bonusShields);
        tag.putBoolean("boughtShieldThisContract", boughtShieldThisContract);
        tag.putInt("contractCooldown", contractCooldown);

        if (contractTargetUuid != null) {
            tag.putUUID("contractTargetUuid", contractTargetUuid);
            tag.putString("contractTargetName", contractTargetName);
        }
        if (forcedTargetUuid != null) {
            tag.putUUID("forcedTargetUuid", forcedTargetUuid);
            tag.putString("forcedTargetName", forcedTargetName);
        }
        if (employerUuid != null) {
            tag.putUUID("employerUuid", employerUuid);
            tag.putString("employerName", employerName);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        contractActive = tag.getBoolean("contractActive");
        contractKillCount = tag.getInt("contractKillCount");
        requiredContractKills = Math.max(1, tag.getInt("requiredContractKills"));
        bonusShields = Math.max(0, tag.getInt("bonusShields"));
        boughtShieldThisContract = tag.getBoolean("boughtShieldThisContract");

        contractCooldown = tag.contains("contractCooldown") ? tag.getInt("contractCooldown") : 0;

        contractTargetUuid = tag.contains("contractTargetUuid") ? tag.getUUID("contractTargetUuid") : null;
        contractTargetName = tag.contains("contractTargetName") ? tag.getString("contractTargetName") : "";

        forcedTargetUuid = tag.contains("forcedTargetUuid") ? tag.getUUID("forcedTargetUuid") : null;
        forcedTargetName = tag.contains("forcedTargetName") ? tag.getString("forcedTargetName") : "";

        employerUuid = tag.contains("employerUuid") ? tag.getUUID("employerUuid") : null;
        employerName = tag.contains("employerName") ? tag.getString("employerName") : "";
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }
}

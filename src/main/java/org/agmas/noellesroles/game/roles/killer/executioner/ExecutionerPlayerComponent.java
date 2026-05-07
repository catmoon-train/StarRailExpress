package org.agmas.noellesroles.game.roles.killer.executioner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.AlivePlayerRoleTeamInfo;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ExecutionerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ExecutionerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "executioner"),
            ExecutionerPlayerComponent.class);
    private final Player player;
    public UUID target;
    public boolean won = false;
    public boolean targetSelected = false;
    public boolean shopUnlocked = false;

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.target = null;
        this.targetSelected = false;
        this.won = false;
        this.shopUnlocked = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public ExecutionerPlayerComponent(Player player) {
        this.player = player;
        this.target = null;
        this.targetSelected = false;
        this.shopUnlocked = false;
        // assignRandomTarget();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
            return;
        // 如果目标已经死亡且executioner尚未获胜，解锁商店并重置目标
        if (target == null) {
            if (!gameWorldComponent.isRunning())
                return;
            assignRandomTarget(); // 分配新目标

        }
        if (target != null && !won) {
            if (!gameWorldComponent.isRunning())
                return;
            if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
                return;
            Player targetPlayer = player.level().getPlayerByUUID(target);
            var t_role = gameWorldComponent.getRole(targetPlayer);
            // 判断职业是否允许被绑定，否则就应该更换。
            boolean needChange = judgeRole(player.level(), t_role);
            if (t_role == null || targetPlayer == null || GameUtils.isPlayerEliminatedIgnoreShitSplit(targetPlayer)
                    || needChange) {

                // 目标死亡，解锁商店并分配新目标
                this.shopUnlocked = true;
                this.target = null;
                this.targetSelected = false;
                assignRandomTarget(); // 分配新目标
            }
        }
    }

    /**
     * 是否为不可选角色
     * 
     * @param t_role
     * @return 是否为<b>不可选</b>角色
     */
    public static boolean judgeRole(Level level, SRERole t_role) {
        if (t_role == null)
            return true;
        if (RoleUtils.compareRole(t_role, SpecialGameModeRoles.SUPER_LOOSE_END)) {
            return false;
        }
        if (t_role.isInnocent()) {
            return false;
        }
        AlivePlayerRoleTeamInfo info = SREGameWorldComponent.KEY.get(level).getAlivePlayerRoleTeamInfo();
        if (info.hasInnocentAndVigilante()) {
            return true;
        }
        if (t_role.isNeutrals() && !t_role.isNeutralForKiller()) {
            return false;
        }
        return true;
    }

    /**
     * 自动分配随机目标（仅限平民阵营）
     */
    public void assignRandomTarget() {
        // 如果配置允许手动选择目标，则不自动分配
        if (NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return;
        }

        // 如果已经有目标或者已经获胜，则不需要分配新目标
        if (target != null || won) {
            return;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        if (gameWorldComponent == null)
            return;
        List<Player> eligibleTargets = new ArrayList<>();

        // 获取所有存活的平民玩家
        for (Player p : player.level().players()) {
            if (p.getUUID().equals(player.getUUID())) {
                continue; // 跳过自己
            }
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue; // 只考虑存活玩家
            }
            final var role = gameWorldComponent.getRole(p);
            if (role != null
                    && !judgeRole(player.level(), role)) { // 只考虑平民、中立阵营
                eligibleTargets.add(p);
            }
        }

        // 随机选择一个目标
        if (!eligibleTargets.isEmpty()) {
            Collections.shuffle(eligibleTargets);
            this.target = eligibleTargets.getFirst().getUUID();
            this.targetSelected = true;
            this.sync();
        }
    }

    /**
     * 设置目标玩家（仅允许选择平民阵营）
     *
     * @param target 目标玩家的UUID
     */
    public void setTarget(UUID target) {
        // 只有在配置允许手动选择目标时才能使用此方法
        if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return;
        }

        this.target = target;
        this.targetSelected = true;
        this.sync();
    }

    /**
     * 解锁商店（当目标死亡时调用）
     */
    public void unlockShop() {
        this.shopUnlocked = true;
        this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.target != null) {
            tag.putUUID("target", this.target);
        }
        tag.putBoolean("won", this.won);
        tag.putBoolean("targetSelected", this.targetSelected);
        tag.putBoolean("shopUnlocked", this.shopUnlocked);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : null;
        this.won = tag.getBoolean("won");
        this.targetSelected = tag.getBoolean("targetSelected");
        this.shopUnlocked = tag.getBoolean("shopUnlocked");
    }

    @Override
    public void clientTick() {

    }

    public static void registerBackfireEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.EXECUTIONER)) {
                ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(player);
                if (executionerPlayerComponent.target != null
                        && executionerPlayerComponent.target.equals(target.getUUID())) {
                    return AllowShootRevolverDrop.ShouldDropResult.TRUE;
                }
            }
            if (gameWorldComponent.isRole(target, ModRoles.VOODOO)
                    && NoellesRolesConfig.HANDLER.instance().voodooShotLikeEvil) {
                return AllowShootRevolverDrop.ShouldDropResult.FALSE;
            }
            return AllowShootRevolverDrop.ShouldDropResult.PASS;
        });

    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
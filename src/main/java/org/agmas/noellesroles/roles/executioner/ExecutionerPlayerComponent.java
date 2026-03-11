package org.agmas.noellesroles.roles.executioner;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

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
    public void reset() {
        this.target = null;
        this.targetSelected = false;
        this.won = false;
        this.shopUnlocked = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
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
        // 如果目标已经死亡且executioner尚未获胜，解锁商店并重置目标
        if (target == null) {
            GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent == null)
                return;
            if (!gameWorldComponent.isRunning())
                return;
            if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
                return;
            assignRandomTarget(); // 分配新目标

        }
        if (target != null && !won) {
            GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent == null)
                return;
            if (!gameWorldComponent.isRunning())
                return;
            if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
                return;

            Player targetPlayer = player.level().getPlayerByUUID(target);
            if (targetPlayer == null || GameFunctions.isPlayerEliminated(targetPlayer)) {
                // 目标死亡，解锁商店并分配新目标
                this.shopUnlocked = true;
                this.target = null;
                this.targetSelected = false;
                assignRandomTarget(); // 分配新目标
                sync();
            }
        }
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

        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return;
        List<Player> eligibleTargets = new ArrayList<>();

        // 获取所有存活的平民玩家
        for (Player p : player.level().players()) {
            if (p.getUUID().equals(player.getUUID())) {
                continue; // 跳过自己
            }
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) {
                continue; // 只考虑存活玩家
            }
            final var role = gameWorldComponent.getRole(p);
            if (role != null && role.isInnocent() && !role.isNeutrals()) { // 只考虑平民阵营
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

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.target != null) {
            tag.putUUID("target", this.target);
        }
        tag.putBoolean("won", this.won);
        tag.putBoolean("targetSelected", this.targetSelected);
        tag.putBoolean("shopUnlocked", this.shopUnlocked);
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : null;
        this.won = tag.getBoolean("won");
        this.targetSelected = tag.getBoolean("targetSelected");
        this.shopUnlocked = tag.getBoolean("shopUnlocked");
    }

    @Override
    public void clientTick() {

    }
}
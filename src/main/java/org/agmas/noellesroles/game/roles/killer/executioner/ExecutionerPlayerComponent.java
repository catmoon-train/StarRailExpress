package org.agmas.noellesroles.game.roles.killer.executioner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.AlivePlayerRoleTeamInfo;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

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
        if (target != null) {
            if (!gameWorldComponent.isRunning())
                return;
            if (!gameWorldComponent.isRole(player, ModRoles.EXECUTIONER))
                return;
            if (PelicanManager.isStashed(target)) {
                if (redirectTargetToAlivePelicanIfStashed(gameWorldComponent))
                    return;
                this.target = null;
                this.targetSelected = false;
                assignRandomTarget();
                return;
            }
            Player targetPlayer = player.level().getPlayerByUUID(target);
            if (targetPlayer == null) {
                this.shopUnlocked = true;
                this.target = null;
                this.targetSelected = false;
                assignRandomTarget();
                return;
            }
            var t_role = gameWorldComponent.getRole(targetPlayer);
            // 判断职业是否允许被绑定，否则就应该更换。
            boolean targetIsAlivePelican = isAlivePelicanTarget(targetPlayer, gameWorldComponent);
            boolean needChange = !targetIsAlivePelican && judgeRole(player.level(), t_role);
            if (t_role == null || GameUtils.isPlayerEliminatedIgnoreShitSplit(targetPlayer)
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
     * 自动分配随机目标（仅限平民阵营，优先排除肉汁）
     */
    private boolean redirectTargetToAlivePelicanIfStashed(SREGameWorldComponent gameWorldComponent) {
        UUID pelicanId = PelicanManager.getPelicanForStashed(this.target);
        if (pelicanId == null) {
            return false;
        }
        Player pelican = player.level().getPlayerByUUID(pelicanId);
        if (!isAlivePelicanTarget(pelican, gameWorldComponent)) {
            return false;
        }
        this.target = pelicanId;
        this.targetSelected = true;
        this.sync();
        return true;
    }

    private boolean isAlivePelicanTarget(Player targetCandidate, SREGameWorldComponent gameWorldComponent) {
        return targetCandidate != null
                && !targetCandidate.getUUID().equals(player.getUUID())
                && GameUtils.isPlayerAliveAndSurvival(targetCandidate)
                && !PelicanManager.isStashed(targetCandidate)
                && gameWorldComponent.isRole(targetCandidate, ModRoles.PELICAN);
    }

    private Player findAlivePelicanTarget(SREGameWorldComponent gameWorldComponent) {
        List<Player> pelicans = new ArrayList<>();
        for (Player candidate : player.level().players()) {
            if (target != null && target.equals(candidate.getUUID())) {
                continue;
            }
            if (isAlivePelicanTarget(candidate, gameWorldComponent)) {
                pelicans.add(candidate);
            }
        }
        if (pelicans.isEmpty()) {
            return null;
        }
        Collections.shuffle(pelicans);
        return pelicans.getFirst();
    }

    public void assignRandomTarget() {
        assignRandomTarget(false);
    }

    public boolean assignRandomTarget(boolean bindNewOne) {
        // 如果配置允许手动选择目标，则不自动分配
        if (NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return false;
        }

        // 如果已经有目标或者已经获胜，则不需要分配新目标
        if (!bindNewOne && (target != null)) {
            return false;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());
        if (gameWorldComponent == null)
            return false;
        List<Player> eligibleTargets = new ArrayList<>();
        List<Player> nonMeatballTargets = new ArrayList<>();
        var lovercca = LoversComponent.KEY.get(player);
        Player mylover = null;
        if (lovercca.isLover()) {
            mylover = lovercca.getLoverAsPlayer();
        }
        // 获取所有存活的平民玩家，同时区分肉汁和非肉汁
        for (Player p : player.level().players()) {
            if (p.getUUID().equals(player.getUUID())) {
                continue; // 跳过自己
            }
            if (mylover != null && p.getUUID().equals(mylover.getUUID())) {
                continue; // 跳过恋人
            }
            if (bindNewOne && target != null && target.equals(p.getUUID()))
                continue;// 跳过当前
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue; // 只考虑存活玩家
            }
            if (PelicanManager.isStashed(p)) {
                continue;
            }
            final var role = gameWorldComponent.getRole(p);
            if (role != null
                    && !judgeRole(player.level(), role)) { // 只考虑平民、中立阵营
                eligibleTargets.add(p);
                // 肉汁最后才选（除非场上只剩肉汁）
                if (!RoleUtils.compareRole(role, ModRoles.MEATBALL)) {
                    nonMeatballTargets.add(p);
                }
            }
        }
        if (bindNewOne && target != null && nonMeatballTargets.isEmpty() && !eligibleTargets.isEmpty()) {
            return false;
        }
        // 优先从非肉汁玩家中随机选择；如果没有非肉汁目标，才从全体（只剩肉汁）中选
        List<Player> selectionPool = nonMeatballTargets.isEmpty() ? eligibleTargets : nonMeatballTargets;
        if (!selectionPool.isEmpty()) {
            Collections.shuffle(selectionPool);
            this.target = selectionPool.getFirst().getUUID();
            this.targetSelected = true;
            this.sync();
            return true;
        }
        Player pelicanTarget = findAlivePelicanTarget(gameWorldComponent);
        if (pelicanTarget != null) {
            this.target = pelicanTarget.getUUID();
            this.targetSelected = true;
            this.sync();
            return true;
        }
        return false;
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
        if (PelicanManager.isStashed(target)
                && !redirectTargetToAlivePelicanIfStashed(SREGameWorldComponent.KEY.get(player.level()))) {
            this.target = null;
            this.targetSelected = false;
            this.sync();
            return;
        }
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
        tag.putBoolean("targetSelected", this.targetSelected);
        tag.putBoolean("shopUnlocked", this.shopUnlocked);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : null;
        this.targetSelected = tag.getBoolean("targetSelected");
        this.shopUnlocked = tag.getBoolean("shopUnlocked");
    }

    @Override
    public void clientTick() {

    }

    public static void registerBackfireEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (ShootingFrenzyPlayerComponent.isInFrenzy(player)) {
                return TrueFalseResult.FALSE;
            }
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.EXECUTIONER)) {
                ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(player);
                if (executionerPlayerComponent.target != null
                        && executionerPlayerComponent.target.equals(target.getUUID())) {
                    return TrueFalseResult.TRUE;
                }
            }
            if (gameWorldComponent.isRole(target, ModRoles.VOODOO)
                    && NoellesRolesConfig.HANDLER.instance().voodooShotLikeEvil) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });

    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}

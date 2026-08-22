/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.AlivePlayerRoleTeamInfo;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ExecutionerRoleData extends SimpleRoleData {

    public UUID target;
    public boolean targetSelected = false;
    public boolean shopUnlocked = false;
    public boolean inFrenzy = false;
    private ItemStack savedMainhandItem = ItemStack.EMPTY;


    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.target = null;
        this.targetSelected = false;
        this.shopUnlocked = false;
        this.inFrenzy = false;
        this.savedMainhandItem = ItemStack.EMPTY;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public ExecutionerRoleData(RoleDataContext context) {
        super(context);
        this.target = null;
        this.targetSelected = false;
        this.shopUnlocked = false;
        // assignRandomTarget();
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
        tickFrenzy();
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
        tag.putBoolean("inFrenzy", this.inFrenzy);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : null;
        this.targetSelected = tag.getBoolean("targetSelected");
        this.shopUnlocked = tag.getBoolean("shopUnlocked");
        this.inFrenzy = tag.getBoolean("inFrenzy");
    }

    @Override
    public void clientTick() {

    }

    public static void registerBackfireEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (isInFrenzy(player)) {
                return TrueFalseResult.FALSE;
            }
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(player.level());
            if (gameWorldComponent.isRole(player, ModRoles.EXECUTIONER)) {
                ExecutionerRoleData executionerPlayerComponent = RoleData.getNullable(ExecutionerRoleData.class, player);
                if (executionerPlayerComponent != null && executionerPlayerComponent.target != null
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

    public boolean startFrenzy() {
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }
        this.savedMainhandItem = player.getMainHandItem().copy();
        if (!player.getMainHandItem().is(TMMItemTags.GUNS)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TMMItems.REVOLVER));
        }
        psychoComponent.setPsychoTicks(GameConstants.getPsychoTimer());
        psychoComponent.setArmour(1);
        psychoComponent.type = 1;
        psychoComponent.sync();
        SREGameWorldComponent.KEY.get(player.level()).refreshPsychoCount(true);
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }
        this.inFrenzy = true;
        this.sync();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.5F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.5, 1.0, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 0.5, 1.0, 0.5, 0.05);
        }
        return true;
    }

    public void stopFrenzy() {
        if (!inFrenzy)
            return;
        this.inFrenzy = false;
        if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, savedMainhandItem.copy());
        }
        this.savedMainhandItem = ItemStack.EMPTY;
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        psychoComponent.type = -1;
        psychoComponent.sync();
        this.sync();
    }

    public static boolean isInFrenzy(Player player) {
        ExecutionerRoleData data = RoleData.getNullable(ExecutionerRoleData.class, player);
        return RoleData.isAttached(data) && data.inFrenzy;
    }

    private void tickFrenzy() {
        if (!inFrenzy)
            return;
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() <= 0) {
            stopFrenzy();
            return;
        }
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 40 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    5, 0.3, 0.5, 0.3, 0.02);
        }
    }

    public static void registerGunNoDropEvent() {
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (isInFrenzy(player)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }

    public static void registerFrenzyCooldownEvent() {
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (!isInFrenzy(player)) {
                return;
            }
            ItemStack mainHandStack = player.getMainHandItem();
            if (mainHandStack.is(TMMItemTags.GUNS)) {
                int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(
                        mainHandStack.getItem(),
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0));
                player.getCooldowns().addCooldown(mainHandStack.getItem(), baseCooldown / 2);
            }
            if (target != null && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        target.getX(), target.getY() + 1, target.getZ(),
                        15, 0.3, 0.5, 0.3, 0.1);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        target.getX(), target.getY() + 1, target.getZ(),
                        10, 0.3, 0.5, 0.3, 0.05);
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        target.getX(), target.getY() + 1.5, target.getZ(),
                        20, 0.5, 0.8, 0.5, 0.3);
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        target.getX(), target.getY() + 1, target.getZ(),
                        25, 0.4, 0.6, 0.4, 0.5);
                serverLevel.playSound(null, target.blockPosition().above(1),
                        SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
        });
    }

}

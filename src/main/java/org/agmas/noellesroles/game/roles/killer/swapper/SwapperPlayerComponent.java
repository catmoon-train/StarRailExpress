package org.agmas.noellesroles.game.roles.killer.swapper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SwapperPlayerComponent implements RoleComponent, ServerTickingComponent {
    private final Player player;
    public boolean isSwapping = false;
    public int swapTimer = 0;
    public UUID target1 = null;
    public UUID target2 = null;
    /** G 键瞬移交换技能的剩余冷却（tick）。 */
    public int frontSwapCooldown = 0;

    public SwapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void serverTick() {
        if (frontSwapCooldown > 0) {
            frontSwapCooldown--;
            if (frontSwapCooldown % 20 == 0 || frontSwapCooldown == 0) {
                ModComponents.SWAPPER.sync(player);
            }
        }
        if (isSwapping) {
            swapTimer--;
            if (swapTimer <= 0) {
                performSwap();
                isSwapping = false;
                target1 = null;
                target2 = null;
                ModComponents.SWAPPER.sync(player);
            }
        }
    }

    public void startSwap(UUID t1, UUID t2) {

        SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
                .get(this.player);
        Player player1 = player.level().getPlayerByUUID(t1);
        Player player2 = player.level().getPlayerByUUID(t2);
        if (!GameUtils.isPlayerAliveAndSurvival(player1)) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.died", player1.getName()), true);
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player2)) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.died", player2.getName()), true);
            return;
        }
        if (player1.distanceToSqr(player2) >= 500 * 500) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.to_far", player1.getName(), player2.getName()),
                    true);
            return;
        }
        if (!player1.onGround()) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.not_on_ground", player1.getName()), true);
            return;
        }
        if (!player2.onGround()) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.not_on_ground", player2.getName()), true);
            return;
        }

        if (abilityPlayerComponent != null) {
            if (!abilityPlayerComponent.canUseAbility()) {
                return;
            }
            abilityPlayerComponent.setCooldown(20 * 20);
        }
        this.target1 = t1;
        this.target2 = t2;
        this.isSwapping = true;
        this.swapTimer = 50; // 2.5秒 = 50 ticks

        if (player instanceof ServerPlayer serverPlayer) {
            ConfigWorldComponent.onPlayerUsedSkill(serverPlayer);
        }
        ModComponents.SWAPPER.sync(player);
    }

    /**
     * G 键技能：与正前方视线对准的玩家瞬移交换位置（距离上限与冷却来自配置）。
     */
    public void frontSwap(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (!gw.isRole(sp, ModRoles.SWAPPER) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        if (frontSwapCooldown > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.swapper.front_cooldown",
                    (frontSwapCooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        HitResult hr = ProjectileUtil.getHitResultOnViewVector(sp,
                e -> e instanceof Player p && p != sp && GameUtils.isPlayerAliveAndSurvival(p),
                cfg.swapperFrontSwapRange);
        if (!(hr instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof ServerPlayer target)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.swapper.front_no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        Vec3 selfPos = sp.position();
        Vec3 targetPos = target.position();
        sp.stopRiding();
        if (sp.isSleeping()) {
            sp.stopSleeping();
        }
        target.stopRiding();
        if (target.isSleeping()) {
            target.stopSleeping();
        }
        sp.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        target.teleportTo(selfPos.x, selfPos.y, selfPos.z);

        frontSwapCooldown = GameConstants.getInTicks(0, cfg.swapperFrontSwapCooldown);
        ModComponents.SWAPPER.sync(sp);

        sp.level().playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.swapper.front_swapped")
                .withStyle(ChatFormatting.AQUA), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.swapper.swapped"), true);
        ConfigWorldComponent.onPlayerUsedSkill(sp);
    }

    private void performSwap() {
        if (player.level().isClientSide)
            return;

        Player player1 = player.level().getPlayerByUUID(target1);
        Player player2 = player.level().getPlayerByUUID(target2);
        if (!GameUtils.isPlayerAliveAndSurvival(player1)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player2)) {
            return;
        }
        if ((player1.getVehicle() == null && player2.getVehicle() == null)
                && (!player1.isSleeping() && !player2.isSleeping()) && (!player1.onGround() || !player2.onGround()))
            return;
        if (player1 != null && player2 != null) {
            player1.stopRiding();
            if (player1.isSleeping()) {
                player1.stopSleeping();
            }
            player2.stopRiding();
            if (player2.isSleeping()) {
                player2.stopSleeping();
            }
            Vec3 pos1 = player1.position();
            Vec3 pos2 = player2.position();
            // 检查碰撞（可选，根据原逻辑）
            if (!player.level().noCollision(player1) || !player.level().noCollision(player2)) {
                // 如果需要碰撞检查，可以在这里处理
            }

            player1.teleportTo(pos2.x, pos2.y, pos2.z);
            player2.teleportTo(pos1.x, pos1.y, pos1.z);

            // 发送提示
            player1.displayClientMessage(Component.translatable("message.noellesroles.swapper.swapped"), true);
            player2.displayClientMessage(Component.translatable("message.noellesroles.swapper.swapped"), true);

            // 设置冷却
            SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
                    .get(player);
            abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().swapperSwapCooldown);
            abilityPlayerComponent.sync();
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        isSwapping = tag.getBoolean("isSwapping");
        swapTimer = tag.getInt("swapTimer");
        frontSwapCooldown = tag.getInt("frontSwapCooldown");
        if (tag.hasUUID("target1"))
            target1 = tag.getUUID("target1");
        if (tag.hasUUID("target2"))
            target2 = tag.getUUID("target2");
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isSwapping", isSwapping);
        tag.putInt("swapTimer", swapTimer);
        tag.putInt("frontSwapCooldown", frontSwapCooldown);
        if (target1 != null)
            tag.putUUID("target1", target1);
        if (target2 != null)
            tag.putUUID("target2", target2);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        isSwapping = false;
        swapTimer = 0;
        target1 = null;
        target2 = null;
        frontSwapCooldown = 0;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
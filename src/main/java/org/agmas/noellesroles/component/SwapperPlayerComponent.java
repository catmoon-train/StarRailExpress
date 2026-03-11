package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SwapperPlayerComponent implements RoleComponent, ServerTickingComponent {
    private final Player player;
    public boolean isSwapping = false;
    public int swapTimer = 0;
    public UUID target1 = null;
    public UUID target2 = null;

    public SwapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void serverTick() {
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

        NoellesRolesAbilityPlayerComponent abilityPlayerComponent = NoellesRolesAbilityPlayerComponent.KEY
                .get(this.player);
        Player player1 = player.level().getPlayerByUUID(t1);
        Player player2 = player.level().getPlayerByUUID(t2);
        if (!GameFunctions.isPlayerAliveAndSurvival(player1)) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.died", player1.getDisplayName()), true);
            return;
        }
        if (!GameFunctions.isPlayerAliveAndSurvival(player2)) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.died", player2.getDisplayName()), true);
            return;
        }
        if (!player1.onGround()) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.not_on_ground", player1.getDisplayName()), true);
            return;
        }
        if (!player2.onGround()) {
            this.player.displayClientMessage(
                    Component.translatable("message.swapper.failed.not_on_ground", player2.getDisplayName()), true);
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

        ModComponents.SWAPPER.sync(player);
    }

    private void performSwap() {
        if (player.level().isClientSide)
            return;

        Player player1 = player.level().getPlayerByUUID(target1);
        Player player2 = player.level().getPlayerByUUID(target2);
        if (!GameFunctions.isPlayerAliveAndSurvival(player1)) {
            return;
        }
        if (!GameFunctions.isPlayerAliveAndSurvival(player2)) {
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
            NoellesRolesAbilityPlayerComponent abilityPlayerComponent = NoellesRolesAbilityPlayerComponent.KEY
                    .get(player);
            abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().swapperSwapCooldown);
            abilityPlayerComponent.sync();
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        isSwapping = tag.getBoolean("isSwapping");
        swapTimer = tag.getInt("swapTimer");
        if (tag.hasUUID("target1"))
            target1 = tag.getUUID("target1");
        if (tag.hasUUID("target2"))
            target2 = tag.getUUID("target2");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isSwapping", isSwapping);
        tag.putInt("swapTimer", swapTimer);
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
    public void reset() {
        isSwapping = false;
        swapTimer = 0;
        target1 = null;
        target2 = null;
    }

    @Override
    public void clear() {
        this.reset();
    }
}
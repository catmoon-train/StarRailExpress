package org.agmas.noellesroles.roles.manipulator;

import java.util.UUID;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class InControlCCA implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<InControlCCA> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "in_control"),
            InControlCCA.class);

    public UUID controller;
    public Player player;
    public boolean isControlling = false;
    public int controlTimer;

    public InControlCCA(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void reset() {
        this.controller = null;
        isControlling = false;
        controlTimer = 0;
    }

    @Override
    public void clear() {
        this.reset();
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        isControlling = compoundTag.getBoolean("isControlling");
        controlTimer = compoundTag.getInt("controlTimer");
        if (compoundTag.hasUUID("controller"))
            controller = compoundTag.getUUID("controller");
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        compoundTag.putBoolean("isControlling", isControlling);
        compoundTag.putInt("controlTimer", controlTimer);
        if (controller != null)
            compoundTag.putUUID("controller", controller);
    }

    public void stopControl() {
        this.isControlling = false;
        this.controlTimer = 0;
        this.controller = null;
        if (this.player != null) {
            this.player.displayClientMessage(Component.translatable("message.noellesroles.manipulator.control_ended")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        this.reset();
        this.sync();
    }

    public void stopControlFromUpstream(boolean isTimeout) {
        if (this.controller != null) {
            if ((player instanceof ServerPlayer sp)) {
                var controller_p = sp.level().getPlayerByUUID(this.controller);
                if (controller_p != null) {
                    var controllerComponent = ManipulatorPlayerComponent.KEY.get(controller_p);
                    if (controllerComponent != null) {
                        controllerComponent.stopControl(isTimeout);
                        this.controller = null;
                    }
                }
            }
        }
        this.stopControl();
        this.reset();
    }

    @Override
    public void serverTick() {
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
            if (this.isControlling) {
                this.stopControlFromUpstream(false);
            }
            return;
        }
        if (isControlling) {
            if (controlTimer > 0) {
                if (!player.hasEffect(MobEffects.UNLUCK)) {
                    player.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 1, 0, true, false, true));
                }
                --controlTimer;
                if (player.isShiftKeyDown()) {
                    --controlTimer;
                }
                if (controlTimer % 20 == 0) {
                    sync();
                }
            }

            if (controlTimer <= 0) {
                this.stopControlFromUpstream(true);
            }
        }
    }
}

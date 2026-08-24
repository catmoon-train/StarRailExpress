package org.agmas.noellesroles.role_data.killer;

import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class HoujuuNueRoleData extends SimpleRoleData {
    public static final int REMOVE_LAYER_TICKS = 30 * 20;
    public int slownessLayers = 0;
    public int tickCounter = REMOVE_LAYER_TICKS;

    public HoujuuNueRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void clientTick() {
        if (tickCounter > 0) {
            tickCounter--;
        }
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (this.player.level().getGameTime() % 40 == 2) {
            addEffects();
        }
        if (tickCounter > 0) {
            tickCounter--;
            if (tickCounter <= 0) {
                removeOneLayer();
                tickCounter = REMOVE_LAYER_TICKS;
                sync();
            }
        }
    }

    private void removeOneLayer() {
        if (this.slownessLayers > 0) {
            this.slownessLayers--;
            this.player.displayClientMessage(
                    Component.translatable("hud.houjuu_nue.tip.remove_layer", this.slownessLayers), true);
            refreshEffects();
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putInt("slownessLayers", slownessLayers);
        tag.putInt("tickCounter", tickCounter);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        slownessLayers = getIntTag(tag, "slownessLayers", 0);
        tickCounter = getIntTag(tag, "tickCounter", REMOVE_LAYER_TICKS);
    }

    public void addLayers() {
        this.slownessLayers++;
        if (this.slownessLayers > 5)
            slownessLayers = 5;
        sync();
        refreshEffects();
    }

    public void refreshEffects() {
        this.player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        this.player.removeEffect(MobEffects.GLOWING);
        this.addEffects();
    }

    public void addEffects() {
        if (this.slownessLayers > 0) {
            if (shouldGiveEffect(MobEffects.MOVEMENT_SLOWDOWN))
                this.player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, this.slownessLayers - 1,
                        false, true, true));
        }
        if (this.slownessLayers >= 3) {
            if (shouldGiveEffect(MobEffects.GLOWING))
                this.player.addEffect(ModEffects.of(MobEffects.GLOWING, 20 * 30, 1,
                        false, true, true));
        }
    }

    private boolean shouldGiveEffect(Holder<MobEffect> effect) {
        if (!player.hasEffect(effect))
            return true;
        if (player.getEffect(effect) == null)
            return true;
        if (player.getEffect(effect).getDuration() <= 50) {
            return true;
        }
        return false;
    }
}

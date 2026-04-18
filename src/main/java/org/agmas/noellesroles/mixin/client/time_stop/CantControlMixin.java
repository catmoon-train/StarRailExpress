package org.agmas.noellesroles.mixin.client.time_stop;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class CantControlMixin {

    @Shadow
    public abstract boolean same(KeyMapping keyMapping);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        final var instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        final LocalPlayer player = instance.player;
        if (player == null)
            return false;
        final var options = instance.options;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()
                && player.hasEffect((ModEffects.TIME_STOP))) {

            if (TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
                return false;
            }
            for (var hotbarSlot : options.keyHotbarSlots) {
                if (this.same(hotbarSlot)) {
                    return true;
                }
            }
            return this.same(options.keySwapOffhand) ||
                    this.same(options.keyJump) ||
                    this.same(options.keyTogglePerspective) ||
                    this.same(options.keyDrop) ||
                    this.same(options.keyLeft) ||
                    this.same(options.keyUp) ||
                    this.same(options.keyDown) ||
                    this.same(options.keyRight) ||
                    this.same(NoellesrolesClient.abilityBind) ||
                    this.same(options.keyAttack) ||
                    this.same(options.keyShift) ||
                    this.same(options.keyInventory) ||
                    this.same(options.keyUse) ||
                    this.same(options.keyAdvancements);
        }

        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        return !this.shouldSuppressKey() && original;
    }
}

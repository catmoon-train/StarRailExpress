package org.agmas.noellesroles.mixin.client.roles.insane;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        final var instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        final var player = instance.player;
        if (player == null)
            return false;
        final var options = instance.options;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()
                && SREClient.gameComponent.isRole(player,
                        ModRoles.INSANE_KILLER)) {
            final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(player);
            if (insaneKillerPlayerComponent.isActive) {
                if (this.same(options.keyUse)) {
                    return true;
                }
                return this.same(options.keySwapOffhand) ||
                        this.same(options.keyJump) ||
                        this.same(options.keyTogglePerspective) ||
                        this.same(options.keyDrop) ||
                        this.same(options.keyAttack) ||
                        this.same(options.keyShift) ||
                        this.same(options.keyAdvancements);
            }
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

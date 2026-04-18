package org.agmas.noellesroles.mixin.client.game;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class ChairWheelKeyMappingMixin {


    @Shadow
    public abstract boolean same(KeyMapping keyMapping);

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
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning() && SREClient.isPlayerAliveAndInSurvival() && SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame) {
            final boolean actionKeys = this.same(options.keySwapOffhand) ||
                    this.same(options.keyJump) ||
                    this.same(options.keyDrop) ||
                    this.same(options.keyAttack) ||
                    this.same(options.keyShift) ||
                    this.same(options.keyInventory) ||
                    this.same(options.keyAdvancements);
            final boolean movementKeys = player.hasEffect(MobEffects.BAD_OMEN) && (this.same(options.keyUp) ||
                    this.same(options.keyRight) ||
                    this.same(options.keyDown) ||
                    this.same(options.keyLeft));
            return actionKeys || movementKeys;
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

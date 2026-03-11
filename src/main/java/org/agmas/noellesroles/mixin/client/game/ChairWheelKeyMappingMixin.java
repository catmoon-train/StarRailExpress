package org.agmas.noellesroles.mixin.client.game;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.game.ChairWheelRaceGame;
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
        Minecraft instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        if (instance.player == null)
            return false;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning() && SREClient.isPlayerAliveAndInSurvival() &&SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame) {
            boolean b = this.same(instance.options.keySwapOffhand) ||
                    this.same(instance.options.keyJump) ||
                    this.same(instance.options.keyDrop) ||
                    this.same(instance.options.keyAttack) ||
                    this.same(instance.options.keyShift) ||
                    this.same(instance.options.keyInventory) ||
                    this.same(instance.options.keyDrop) ||
                    this.same(instance.options.keyAdvancements);
            boolean a =false;
            if (instance.player!=null){
                if (instance.player.hasEffect(MobEffects.BAD_OMEN)){
                    a =     this.same(instance.options.keyUp) ||
                            this.same(instance.options.keyRight) ||
                            this.same(instance.options.keyDown) ||
                            this.same(instance.options.keyLeft) ;
                }
            }
            return b||a;
        }

        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        if (this.shouldSuppressKey()) return false;
        else return original;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        if (this.shouldSuppressKey()) return false;
        else return original;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        if (this.shouldSuppressKey()) return false;
        else return original;
    }
}

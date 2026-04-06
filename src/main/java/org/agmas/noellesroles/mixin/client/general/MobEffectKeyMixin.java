package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class MobEffectKeyMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

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
        if (player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.OTHERWORLD_AURA)
                || player.hasEffect(ModEffects.GHOST_CURSE)) {
            if (this.same(NoellesrolesClient.abilityBind)) {
                return true;
            }
        }
        if (player.hasEffect(ModEffects.MOVE_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)) {
            if (this.same(options.keyJump) || this.same(options.keyLeft) || this.same(options.keyRight)
                    || this.same(options.keyUp) || this.same(options.keyShift) || this.same(options.keyDown))
                return true;
        }
        if (player.hasEffect(ModEffects.USED_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)) {
            if (this.same(options.keyAttack) || this.same(options.keyDrop) || this.same(options.keyUse))
                return true;
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

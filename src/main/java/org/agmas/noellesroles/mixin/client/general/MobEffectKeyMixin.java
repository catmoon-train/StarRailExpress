package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.roles.manipulator.InControlCCA;
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
        if (Minecraft.getInstance() == null)
            return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return false;
       if (player.hasEffect(ModEffects.SKILL_BANED)){
           return this.same(NoellesrolesClient.abilityBind);
       }
       if (player.hasEffect(ModEffects.MOVE_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)){
           return this.same(Minecraft.getInstance().options.keyJump)||this.same(Minecraft.getInstance().options.keyLeft)||this.same(Minecraft.getInstance().options.keyRight)||this.same(Minecraft.getInstance().options.keyUp)||this.same(Minecraft.getInstance().options.keyDown);
       }
        if (player.hasEffect(ModEffects.USED_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)){
            return this.same(Minecraft.getInstance().options.keyAttack)||this.same(Minecraft.getInstance().options.keyDrop)||this.same(Minecraft.getInstance().options.keyUse);
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

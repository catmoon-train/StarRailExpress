package org.agmas.noellesroles.mixin.client.roles.swapper;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
        if (Minecraft.getInstance() == null)
            return false;
        if (Minecraft.getInstance().player == null)
            return false;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning() && SREClient.isPlayerAliveAndInSurvival() && SREClient.gameComponent.isRole(Minecraft.getInstance().player, ModRoles.SWAPPER)) {
            if (Minecraft.getInstance().screen !=null) {
                return this.same(Minecraft.getInstance().options.keySwapOffhand) ||
                        this.same(Minecraft.getInstance().options.keyJump) ||
                        this.same(Minecraft.getInstance().options.keyTogglePerspective) ||
                        this.same(Minecraft.getInstance().options.keyDrop) ||
                        this.same(Minecraft.getInstance().options.keyAttack) ||
                        this.same(Minecraft.getInstance().options.keyUse) ||
                        this.same(Minecraft.getInstance().options.keyDrop) ||
                        this.same(Minecraft.getInstance().options.keyAdvancements);
            }
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

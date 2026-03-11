package org.agmas.noellesroles.mixin.client.time_stop;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(KeyMapping.class)
public abstract class CantControlMixin {

    @Shadow
    public abstract boolean same(KeyMapping keyMapping);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        if (Minecraft.getInstance() == null)
            return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return false;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()
                && player.hasEffect((ModEffects.TIME_STOP))) {

                if (TimeStopEffect.canMovePlayers.contains(player.getUUID())){
                    return false ;
                }
                return this.same(Minecraft.getInstance().options.keySwapOffhand) ||
                        this.same(Minecraft.getInstance().options.keyJump) ||
                        this.same(Minecraft.getInstance().options.keyTogglePerspective) ||
                        this.same(Minecraft.getInstance().options.keyDrop) ||
                        this.same(Minecraft.getInstance().options.keyLeft) ||
                        this.same(Minecraft.getInstance().options.keyUp) ||
                        this.same(Minecraft.getInstance().options.keyDown) ||
                        this.same(Minecraft.getInstance().options.keyRight) ||
                        this.same(NoellesrolesClient.abilityBind) ||
                        this.same(Minecraft.getInstance().options.keyAttack) ||
                        this.same(Minecraft.getInstance().options.keyShift) ||
                        this.same(Minecraft.getInstance().options.keyInventory) ||
                        Arrays.stream(Minecraft.getInstance().options.keyHotbarSlots).anyMatch(this::same) ||
                        this.same(Minecraft.getInstance().options.keyUse) ||
                        this.same(Minecraft.getInstance().options.keyAdvancements);
            }

        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        if (this.shouldSuppressKey())
            return false;
        else
            return original;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        if (this.shouldSuppressKey())
            return false;
        else
            return original;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        if (this.shouldSuppressKey())
            return false;
        else
            return original;
    }
}

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.event.OnOpenInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1))
    private void tmm$replaceInventoryScreenWithLimitedInventoryScreen(Minecraft instance, Screen screen,
            Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(instance, screen);
            return;
        }

        if (SREClient.gameComponent.getFade() > 0) {
            return;
        }
        boolean flag = SREClient.isPlayerAliveAndInSurvival();
        if(!flag && OnOpenInventory.EVENT.invoker().needOpenLimittedInventory(player, screen)){
            flag = true;
        }

        original.call(instance,
                flag ? new LimitedInventoryScreen(this.player) : screen);
    }
}

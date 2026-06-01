package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
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

        SREGameWorldComponent gameComponent = SREClient.gameComponent;
        if (gameComponent!=null){

        if (gameComponent.getFade() > 0) {
            return;
        }


        }
        boolean flag = SREClient.isPlayerAliveAndInSurvival();
        if(!flag && checkOnOpenInventory(player, screen)){
            flag = true;
        }

        original.call(instance,
                flag ? new LimitedInventoryScreen(this.player) : screen);
    }

    private static boolean checkOnOpenInventory(LocalPlayer player, Screen screen) {
        return io.wifi.starrailexpress.event.OnOpenInventory.EVENT.invoker().needOpenLimittedInventory(player, screen);
    }
}

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    public CreativeInventoryScreenMixin(InventoryMenu screenHandler, Inventory playerInventory, Component text) {
        super(screenHandler, playerInventory, text);
    }

    @WrapMethod(method = "containerTick")
    public void tmm$replaceSurvivalInventory(Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call();
            return;
        }
        if (SREClient.gameComponent == null) {
            original.call();
            return;
        }
        if (SREClient.isPlayerAliveAndInSurvival()) {
            this.minecraft.setScreen(new LimitedInventoryScreen(this.minecraft.player));
        } else {
            original.call();
        }
    }
}

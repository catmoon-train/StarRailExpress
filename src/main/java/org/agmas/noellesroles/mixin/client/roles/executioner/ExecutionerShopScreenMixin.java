package org.agmas.noellesroles.mixin.client.roles.executioner;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LimitedInventoryScreen.class)
public abstract class ExecutionerShopScreenMixin extends LimitedHandledScreen<InventoryMenu> {
    @Shadow
    @Final
    public LocalPlayer player;

    public ExecutionerShopScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    // @Inject(method = "init", at = @At("HEAD"))
    // void onInit(CallbackInfo ci) {
    //     GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(player.getWorld());
    //     
    //     // 检查是否是Executioner角色
    //     if (gameWorldComponent.isRole(player, Noellesroles.EXECUTIONER)) {
    //         ExecutionerPlayerComponent executionerComponent = ExecutionerPlayerComponent.KEY.get(player);
    //         
    //         // 检查商店是否已解锁
    //         if (executionerComponent.shopUnlocked) {
    //         }
    //     }
    // }
}
package io.wifi.starrailexpress.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.wifi.starrailexpress.event.AllowItemShowInHand;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    /**
     * tick() 中缓存副手物品，用于触发换手动画。
     * 拦截后让手铐也能触发持握动画。
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectMainhandTick(LocalPlayer player) {
        return noellesroles$resolveHand(player, true);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectOffhandTick(LocalPlayer player) {
        return noellesroles$resolveHand(player, false);
    }

    /**
     * renderHandsWithItems() 中实际渲染副手物品。
     */
    @Redirect(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectOffhandRender(LocalPlayer player) {
        return noellesroles$resolveHand(player, false);
    }

    @Redirect(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectMainhandRender(LocalPlayer player) {
        return noellesroles$resolveHand(player, true);
    }

    private static ItemStack noellesroles$resolveHand(LocalPlayer player, boolean mainHand) {
        ItemStack original = player.getOffhandItem();
        var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, original, mainHand);
        if (eventRes != null) {
            return eventRes;
        }
        return original;
    }
}
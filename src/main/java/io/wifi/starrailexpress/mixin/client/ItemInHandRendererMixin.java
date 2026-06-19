package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    // renderHandsWithItems 直接读字段，必须 shadow 覆写
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Unique private ItemStack noellesroles$savedMain = null;
    @Unique private ItemStack noellesroles$savedOff = null;

    // ── tick() ── 这里调用了方法，@Redirect 有效 ──────────────────────────

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectMainhandTick(LocalPlayer player) {
        return noellesroles$resolveHand(player, true);
    }

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noellesroles$redirectOffhandTick(LocalPlayer player) {
        return noellesroles$resolveHand(player, false);
    }

    // ── renderHandsWithItems() ── 读的是字段，用 HEAD/RETURN 覆写字段 ──────

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void noellesroles$preRender(float f, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            LocalPlayer localPlayer, int i, CallbackInfo ci) {

        // 高级运镜动画期间整只手（含物品）都不渲染，等同原版 F1 的隐藏效果。
        // 在交换字段前直接取消，避免遗留未还原的手持物状态。
        if (AdvancedCameraDirector.shouldOverride()) {
            ci.cancel();
            return;
        }

        ItemStack resolvedMain = noellesroles$resolveHand(localPlayer, true);
        if (resolvedMain != mainHandItem) {
            noellesroles$savedMain = mainHandItem;
            mainHandItem = resolvedMain;
        }

        ItemStack resolvedOff = noellesroles$resolveHand(localPlayer, false);
        if (resolvedOff != offHandItem) {
            noellesroles$savedOff = offHandItem;
            offHandItem = resolvedOff;
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void noellesroles$postRender(float f, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            LocalPlayer localPlayer, int i, CallbackInfo ci) {

        if (noellesroles$savedMain != null) {
            mainHandItem = noellesroles$savedMain;
            noellesroles$savedMain = null;
        }
        if (noellesroles$savedOff != null) {
            offHandItem = noellesroles$savedOff;
            noellesroles$savedOff = null;
        }
    }

    // ── 公共逻辑 ──────────────────────────────────────────────────────────

    private static ItemStack noellesroles$resolveHand(LocalPlayer player, boolean mainHand) {
        // 修复原代码 bug：根据 mainHand 参数取对应手的物品
        ItemStack original = mainHand ? player.getMainHandItem() : player.getOffhandItem();
        ItemStack eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, original, mainHand);
        return eventRes != null ? eventRes : original;
    }
}
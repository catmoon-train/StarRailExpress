package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.item.PhotographFrameItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让摄影师在冒险模式下也能放置照片框。
 *
 * <p>原本依赖 {@link PhotographFrameItemMixin} 对 exposure {@code useOn} 内
 * {@code mayUseItemAt} 调用点的 {@code @Redirect}，但跨 mod 注入到 exposure 调用点在实机上
 * 并不稳定（冒险模式仍被 {@code Player#mayUseItemAt} 的 CanPlaceOn 限制拦下）。这里直接注入
 * 原版 {@code Player#mayUseItemAt}：必定应用、客户端/服务端一致，是放开冒险放置的可靠做法。</p>
 */
@Mixin(Player.class)
public abstract class PhotographerFramePlaceMixin {

    @Inject(method = "mayUseItemAt", at = @At("HEAD"), cancellable = true)
    private void noe$allowPhotographerFramePlacement(BlockPos pos, Direction direction, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof PhotographFrameItem
                && PhotographerFrameEvents.isPhotographer((Player) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}

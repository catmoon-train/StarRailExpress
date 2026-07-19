package org.agmas.noellesroles.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 60s 生存模式中禁止玻璃瓶从水源方块接水。
 * <p>
 * 炼药锅（BATHTUB）已在 {@link net.exmo.sre.sixtyseconds.logic.SixtySecondsStations}
 * 的 {@code UseBlockCallback} 中被拦截为净化台 GUI，无需额外处理。
 * 但 {@link BottleItem#use} 在水源方块前的射线检测不走方块交互事件，
 * 需单独拦截。
 */
@Mixin(BottleItem.class)
public class SixtySecondsGlassBottleMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true,remap = false)
    public void sixtySeconds$preventWaterFill(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(level)) {
            // 直接 pass，不做任何操作——玻璃瓶保持原样，不消耗也不产出水瓶
            cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(interactionHand)));
            cir.cancel();
        }
    }
}

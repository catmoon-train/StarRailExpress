package org.agmas.noellesroles.mixin.scene;

import org.agmas.noellesroles.game.roles.neutral.snow_golem.SnowGolemRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;

/**
 * 雪傀儡能像穿皮革靴子一样站在细雪上。
 *
 * <p>
 * 原版的判定就集中在这一个静态方法里：<b>皮革靴子</b>和 {@code POWDER_SNOW_WALKABLE_MOBS}
 * 标签里的生物能站住，其余生物会陷下去。所以这里不再另写一套移动逻辑，
 * 直接让雪傀儡职业的玩家复用皮革靴子的那条路径，手感与原版完全一致。
 *
 * <p>
 * <b>客户端也要生效</b>：{@code LivingEntity} 在两侧都会调用它。只改服务端的话，
 * 客户端仍会按「下沉」的方式插值与播放音效，表现为位置抖动。
 */
@Mixin(PowderSnowBlock.class)
public class PowderSnowWalkMixin {

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void noellesroles$snowGolemWalksOnPowderSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && SnowGolemRole.isSnowGolem(player)) {
            cir.setReturnValue(true);
        }
    }
}

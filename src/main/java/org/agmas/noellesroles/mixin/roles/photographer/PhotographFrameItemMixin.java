package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographFrameItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocent.photographer.PhotographerFrameEvents;
import org.agmas.noellesroles.game.roles.innocent.photographer.SrePhotographerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让"摄影师"在冒险模式下也能放置照片框（exposure 原版会被 {@code Player#mayUseItemAt} 拦下），
 * 并把摄影师放置的画框实体打上标记，供开局清理时识别。
 */
@Mixin(PhotographFrameItem.class)
public class PhotographFrameItemMixin {

    /** 当前这次放置是否由摄影师发起（在同一次 useOn 调用内由权限判定写入、创建实体时读取）。 */
    @Unique
    private static final ThreadLocal<Boolean> sre$placingPhotographer = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Redirect(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;mayUseItemAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean sre$bypassAdventurePlacement(Player player, BlockPos pos, Direction direction, ItemStack stack) {
        boolean photographer = PhotographerFrameEvents.isPhotographer(player);
        sre$placingPhotographer.set(photographer);
        if (photographer) {
            return true;
        }
        return player.mayUseItemAt(pos, direction, stack);
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE",
            target = "Lio/github/mortuusars/exposure/world/item/PhotographFrameItem;createEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lio/github/mortuusars/exposure/world/entity/PhotographFrameEntity;"))
    private PhotographFrameEntity sre$markFrame(PhotographFrameItem self, Level level, BlockPos pos, Direction direction) {
        PhotographFrameEntity entity = self.createEntity(level, pos, direction);
        if (Boolean.TRUE.equals(sre$placingPhotographer.get()) && entity instanceof SrePhotographerFrame marker) {
            marker.sre$setPhotographerPlaced(true);
        }
        sre$placingPhotographer.set(Boolean.FALSE);
        return entity;
    }
}

package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographFrameItem;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents;
import org.agmas.noellesroles.game.roles.innocence.photographer.SrePhotographerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让"摄影师"在冒险模式下也能放置照片框，并把摄影师放置的画框实体打上标记，供开局清理时识别。
 *
 * <p>冒险模式放置需要同时打通两道关卡：</p>
 * <ol>
 *   <li>原版 {@code ItemStack#useOn} 会先检查 {@code Abilities#mayBuild}，冒险模式下为 {@code false}
 *       时直接返回 {@code PASS}，{@code PhotographFrameItem#useOn} 根本不会被调用。本仓库通过
 *       {@code ItemStackMixin} 对该字段读取做 {@code WrapOperation}，仅放行实现了
 *       {@link AdventureUsable} 的物品。因此这里让 {@code PhotographFrameItem} 实现该标记接口，
 *       才能让画框的 {@code useOn} 在冒险模式下被执行。</li>
 *   <li>进入 {@code PhotographFrameItem#useOn} 后还会调用 {@code Player#mayUseItemAt}，由
 *       {@code PhotographerFramePlaceMixin} 注入放行——且仅对摄影师放行，因此即便画框成了
 *       {@link AdventureUsable}，非摄影师在冒险模式仍无法放置。</li>
 * </ol>
 */
@Mixin(PhotographFrameItem.class)
public class PhotographFrameItemMixin implements AdventureUsable {

    /** 当前这次放置是否由摄影师发起（在同一次 useOn 调用内由权限判定写入、创建实体时读取）。 */
    @Unique
    private static final ThreadLocal<Boolean> sre$placingPhotographer = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Redirect(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;mayUseItemAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean sre$bypassAdventurePlacement(Player player, BlockPos pos, Direction direction, ItemStack stack) {
        // 仅记录“本次放置是否摄影师发起”，供 createEntity 时打标记。
        // 冒险模式放置放行交由 PhotographerFramePlaceMixin（直接注入原版 mayUseItemAt）处理，
        // 这里委托回真实方法即可，避免跨 mod 调用点注入在实机上不生效。
        sre$placingPhotographer.set(PhotographerFrameEvents.isPhotographer(player));
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

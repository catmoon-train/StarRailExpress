package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents;
import org.agmas.noellesroles.game.roles.innocence.photographer.SrePhotographerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 exposure 照片框实体增加：
 * <ul>
 *   <li>"是否摄影师放置"的标记（含 NBT 持久化），供开局只清理摄影师放置的画框。</li>
 *   <li>tick 中检测穿过画框的玩家并触发传送（见 {@link PhotographerFrameEvents}）。</li>
 * </ul>
 */
@Mixin(PhotographFrameEntity.class)
public class PhotographFrameEntityMixin implements SrePhotographerFrame {

    @Unique
    private boolean sre$photographerPlaced = false;

    @Override
    public boolean sre$isPhotographerPlaced() {
        return this.sre$photographerPlaced;
    }

    @Override
    public void sre$setPhotographerPlaced(boolean placed) {
        this.sre$photographerPlaced = placed;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sre$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("SrePhotographerPlaced", this.sre$photographerPlaced);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sre$load(CompoundTag tag, CallbackInfo ci) {
        this.sre$photographerPlaced = tag.getBoolean("SrePhotographerPlaced");
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void sre$frameTick(CallbackInfo ci) {
        PhotographFrameEntity self = (PhotographFrameEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide) {
            return;
        }
        ItemStack photo = self.getItem();
        if (photo.isEmpty()) {
            return;
        }
        double inflate = NoellesRolesConfig.HANDLER.instance().photographerFrameTriggerInflate;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, self.getBoundingBox().inflate(inflate))) {
            PhotographerFrameEvents.tryTeleport(self, player);
        }
    }
}

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.EntityClearUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 清除实体 Mixin
 */
@Mixin(GameUtils.class)
public class EntityClear {
    /**
     * 在 resetEntities 方法尾部注入，清除所有实体
     */
    @Inject(method = "resetEntities", at = @At("TAIL"))
    private static void clearAllEntities$start(ServerLevel world, CallbackInfo ci) {
        Noellesroles.LOGGER.info("Kill all related entities in game world!");
        EntityClearUtils.clearAllEntities(world);
    }
}

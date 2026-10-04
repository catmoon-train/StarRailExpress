package org.agmas.noellesroles.mixin.client.katana;

import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.katana.KatanaState;
import org.agmas.noellesroles.katana.client.KatanaClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端消费武士刀的实体事件：招式动画同步、下一招式预测、格挡内置冷却同步。
 */
@Mixin(LivingEntity.class)
public class LivingEntityKatanaEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void katana$onEntityEvent(byte id, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (id >= KatanaState.EVENT_MOVE_USED_BASE && id < KatanaState.EVENT_MOVE_USED_BASE + 3) {
            KatanaClientState.onMoveUsed(self, id - KatanaState.EVENT_MOVE_USED_BASE + 1);
        } else if (id >= KatanaState.EVENT_NEXT_MOVE_BASE && id < KatanaState.EVENT_NEXT_MOVE_BASE + 3) {
            KatanaClientState.onNextMove(self, id - KatanaState.EVENT_NEXT_MOVE_BASE + 1);
        } else if (id == KatanaState.EVENT_BLOCK_COOLDOWN_START) {
            KatanaClientState.onBlockCooldown(self, false);
        } else if (id == KatanaState.EVENT_BLOCK_COOLDOWN_CLEAR) {
            KatanaClientState.onBlockCooldown(self, true);
        } else if (id == KatanaState.EVENT_BLOCK_START_LINKED
                || id == KatanaState.EVENT_BLOCK_START_NORMAL) {
            KatanaClientState.onBlockStart(self, id == KatanaState.EVENT_BLOCK_START_LINKED);
        }
    }
}

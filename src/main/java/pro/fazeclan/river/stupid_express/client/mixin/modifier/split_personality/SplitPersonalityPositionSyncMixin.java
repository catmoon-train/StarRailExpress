package pro.fazeclan.river.stupid_express.client.mixin.modifier.split_personality;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同步旁观者的位置到活跃人格
 * 旁观者和活跃人格保持相同的位置、视角、速度
 */
@Mixin(LocalPlayer.class)
public abstract class SplitPersonalityPositionSyncMixin {

    @Inject(
            method = "tick()V",
            at = @At("HEAD")
    )
    void syncSplitPersonalityPosition(CallbackInfo ci) {
//        LocalPlayer player = (LocalPlayer) (Object) this;
//        var component = SplitPersonalityComponent.KEY.get(player);
//
//        if (component == null || component.getMainPersonality() == null || component.getSecondPersonality() == null) {
//            return;
//        }
//        if (component.getTemporaryRevivalStartTick()>0)return;
//
//        // 如果是旁观者，同步位置到活跃人格
//        if (!component.isCurrentlyActive()) {
//            AbstractClientPlayer activePlayer = (AbstractClientPlayer) player.level().getPlayerByUUID(component.getCurrentActivePerson());
//            if (activePlayer != null && activePlayer != player) {
//                // 同步位置 - 确保旁观者始终在活跃人格的位置
//                player.setPos(activePlayer.getX(), activePlayer.getY(), activePlayer.getZ());
//                player.xRotO = activePlayer.xRotO;
//                player.yRotO = activePlayer.yRotO;
//                player.setXRot(activePlayer.getXRot());
//                player.setYRot(activePlayer.getYRot());
//
//                // 同步眼睛高度
//
//                // 清除所有移动输入
//                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
//            }
//        }
    }
}

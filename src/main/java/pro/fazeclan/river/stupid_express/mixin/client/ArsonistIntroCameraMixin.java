package pro.fazeclan.river.stupid_express.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pro.fazeclan.river.stupid_express.client.ArsonistIntroClient;

/** 仅叠加当帧相机震动，不写玩家旋转，也不持有或播放高级相机轨道。 */
@Mixin(value = Camera.class, priority = 900)
public abstract class ArsonistIntroCameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    // 高级相机在setup中途cancel，普通TAIL注入不会执行；包裹完成后再叠加，兼容该提前返回。
    @WrapMethod(method = "setup")
    private void stupidExpress$introShake(BlockGetter level, Entity entity, boolean detached,
                                          boolean thirdPersonReverse, float partialTick, Operation<Void> original) {
        original.call(level, entity, detached, thirdPersonReverse, partialTick);
        if (!ArsonistIntroClient.isActive()) return;
        float yaw = ArsonistIntroClient.shakeYaw(partialTick);
        float pitch = ArsonistIntroClient.shakePitch(partialTick);
        if (yaw == 0 && pitch == 0) return;
        Camera camera = (Camera) (Object) this;
        setRotation(camera.getYRot() + yaw, camera.getXRot() + pitch);
    }
}

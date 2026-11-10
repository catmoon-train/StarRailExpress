package pro.fazeclan.river.stupid_express.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.role.arsonist.LargeOilCanEntity;

/**
 * 大油罐遥控镜头：相机绑定到油罐（复用机械鸟的成熟操控链路），
 * 这里只接管「视角表现」，绝不碰控制输入。
 *
 * <p>1) 旋转直接用本地玩家的鼠标朝向，不再由客户端回传、服务端写实体再同步回来，
 *    因此没有强制转向、也没有逐 tick 的往返抖动。
 * 2) 设为第三人称，让油罐本体（相机实体）能被正常渲染，玩家看得见它飞。
 * 3) 镜头挂在油罐后上方，并从玩家眼睛平滑过渡，投掷瞬间不会突然跳镜头。
 */
@Mixin(Camera.class)
public abstract class OilCanCameraMixin {

    @Shadow
    public abstract void setPosition(Vec3 vec3);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    private boolean detached;

    private static Vec3 sre$pos;

    @Inject(method = "setup", at = @At("TAIL"))
    private void sre$followOilCan(BlockGetter level, Entity entity, boolean detachedParam,
                                  boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LargeOilCanEntity can) || can.isRemoved()) {
            sre$pos = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            sre$pos = null;
            return;
        }
        // 1) 旋转完全由本地玩家鼠标决定，不被服务端视角回传接管。
        this.setRotation(player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        // 2) 第三人称：油罐本体（相机实体）才会被渲染出来。
        this.detached = true;

        // 3) 镜头挂在油罐后上方，从玩家眼睛平滑追上，避免投掷瞬间跳镜头；
        //    持续插值也能抵消服务端 20Hz 位置同步带来的逐 tick 顿挫。
        Vec3 look = player.getLookAngle();
        Vec3 anchor = can.getPosition(partialTick).add(0.0D, 0.6D, 0.0D);
        Vec3 target = anchor.subtract(look.scale(2.5D));
        BlockHitResult hit = client.level.clip(new ClipContext(anchor, target,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            target = hit.getLocation().add(look.scale(0.2D));
        }
        Vec3 previous = sre$pos == null ? player.getEyePosition() : sre$pos;
        double t = 0.35D;
        sre$pos = new Vec3(
                previous.x + (target.x - previous.x) * t,
                previous.y + (target.y - previous.y) * t,
                previous.z + (target.z - previous.z) * t);
        setPosition(sre$pos);
    }
}

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
import pro.fazeclan.river.stupid_express.client.ArsonistClient;
import pro.fazeclan.river.stupid_express.role.arsonist.LargeOilCanEntity;

/**
 * 大油罐遥控的跟随镜头：只改相机位置，绝不碰旋转。
 *
 * <p>旋转仍由 {@code Camera.setup} 按本地玩家鼠标朝向计算，因此不再出现
 * 「客户端回传视角 → 服务端写实体旋转 → 客户端相机被动跟随」的往返延迟与强制转向。
 * 油罐没有被设为相机实体，会作为普通实体渲染在镜头前方，玩家能看到它在飞行。
 */
@Mixin(Camera.class)
public abstract class OilCanCameraMixin {

    @Shadow
    public abstract void setPosition(Vec3 vec3);

    private static Vec3 sre$oilCanCameraPos;
    private static int sre$oilCanCameraEntityId = -1;

    @Inject(method = "setup", at = @At("TAIL"))
    private void sre$followOilCan(BlockGetter level, Entity entity, boolean detached,
                                  boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        int id = ArsonistClient.getActiveOilCanId();
        // 第三人称、旁观相机、断线或没有遥控油罐时，完全交还原版视角。
        if (player == null || entity != player || detached || client.level == null || id < 0) {
            sre$resetFollow();
            return;
        }
        Entity can = client.level.getEntity(id);
        if (!(can instanceof LargeOilCanEntity) || can.isRemoved()) {
            sre$resetFollow();
            return;
        }
        if (sre$oilCanCameraEntityId != id) {
            sre$resetFollow();
            sre$oilCanCameraEntityId = id;
        }
        // 镜头挂在油罐后上方，油罐本体留在镜头正前方。
        Vec3 look = player.getLookAngle();
        Vec3 anchor = can.getPosition(partialTick).add(0.0D, 0.6D, 0.0D);
        Vec3 target = anchor.subtract(look.scale(2.5D));
        // 贴墙飞行时把镜头收回到墙面之前，避免镜头钻进方块里。
        BlockHitResult hit = client.level.clip(new ClipContext(anchor, target,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            target = hit.getLocation().add(look.scale(0.2D));
        }
        // 从玩家原本的眼睛位置出发平滑追上油罐，投掷瞬间不会突然跳镜头；
        // 持续插值也能抵消服务端 20Hz 位置同步带来的逐 tick 顿挫。
        Vec3 previous = sre$oilCanCameraPos == null ? player.getEyePosition() : sre$oilCanCameraPos;
        double t = 0.35D;
        sre$oilCanCameraPos = new Vec3(
                previous.x + (target.x - previous.x) * t,
                previous.y + (target.y - previous.y) * t,
                previous.z + (target.z - previous.z) * t);
        setPosition(sre$oilCanCameraPos);
    }

    private static void sre$resetFollow() {
        sre$oilCanCameraPos = null;
        sre$oilCanCameraEntityId = -1;
    }
}

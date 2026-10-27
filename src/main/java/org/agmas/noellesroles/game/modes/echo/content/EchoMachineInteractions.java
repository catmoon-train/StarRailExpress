package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 统一机器部件归属、整机距离和视线；不扩张普通方块或玩家救援的交互范围。 */
public final class EchoMachineInteractions {
    private EchoMachineInteractions() { }
    public static BlockPos root(LevelReader level, BlockPos pos) { return EchoMachineStructure.root(level,pos); }
    public static AABB bounds(LevelReader level, BlockPos pos) {
        BlockPos root=root(level,pos);
        if(level.hasChunkAt(root)) {
            var state=level.getBlockState(root); var kind=EchoMachines.kind(state);
            if(kind!=null && state.getValue(EchoMachineBlock.PART)==0)
                return EchoMachineStructure.bounds(root,kind,state.getValue(EchoMachineBlock.FACING));
        }
        return new AABB(pos);
    }
    /** 取朝向观察者的最近外壳内侧点，避免射线恰好停在浮点面边界。 */
    public static Vec3 aim(LevelReader level, Vec3 point, Vec3 eye) {
        BlockPos pos=BlockPos.containing(point);
        if(!level.hasChunkAt(pos) || EchoMachines.kind(level.getBlockState(pos))==null) return point.add(0,0.5,0);
        return closest(bounds(level,pos),eye);
    }
    public static Vec3 closest(AABB box,Vec3 eye) {
        double epsilon=0.001;
        return new Vec3(Math.max(box.minX+epsilon,Math.min(box.maxX-epsilon,eye.x)),
                Math.max(box.minY+epsilon,Math.min(box.maxY-epsilon,eye.y)),
                Math.max(box.minZ+epsilon,Math.min(box.maxZ-epsilon,eye.z)));
    }
    public static boolean reachable(Level level, Entity observer, Vec3 point, double range) {
        Vec3 eye=observer.getEyePosition(),aim=aim(level,point,eye);
        if(!Double.isFinite(range) || range<0 || eye.distanceToSqr(aim)>range*range) return false;
        var hit=level.clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer));
        return hit.getType()==HitResult.Type.MISS || root(level,hit.getBlockPos()).equals(root(level,BlockPos.containing(point)));
    }
}

package org.agmas.noellesroles.game.modes.echo.cards;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.*;

/** 场景只投影给本局活人；落点校验、声音调度和近距拆除均由服务器控制。 */
public final class EchoSceneService {
    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoSceneState state=new EchoSceneState();
    public EchoSceneService(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; }
    public EchoSceneState state() { return state; }
    public static Point point(Vec3 p) { return new Point(p.x,p.y,p.z); }
    public static Vec3 vec(Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    /** 只接受地板上表面且有容身空间的落点；不能从客户端构造墙内/空中幻象。 */
    public Vec3 ground(ServerPlayer actor,Vec3 point,double range) {
        if(!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) reject("NO_TARGET");
        if(actor.position().distanceToSqr(point)>range*range) reject("OUT_OF_RANGE");
        if(!io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).getPlayArea().contains(point)) reject("NO_TARGET");
        BlockPos floor=BlockPos.containing(point.add(0,-0.02,0));
        if(!level.hasChunkAt(floor)) reject("NO_TARGET");
        var shape=level.getBlockState(floor).getCollisionShape(level,floor);
        boolean supported=shape.toAabbs().stream().map(b -> b.move(floor)).anyMatch(b -> Math.abs(b.maxY-point.y)<0.04 && point.x>=b.minX && point.x<=b.maxX && point.z>=b.minZ && point.z<=b.maxZ);
        if(!supported || !level.noCollision(new AABB(point.x-0.25,point.y+0.03,point.z-0.25,point.x+0.25,point.y+1.85,point.z+0.25))) reject("NO_TARGET");
        var hit=level.clip(new ClipContext(actor.getEyePosition(),point.add(0,0.02,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        if(hit.getType()!=HitResult.Type.MISS && hit.getLocation().distanceToSqr(point)>0.01) reject("NO_LOS");
        return point;
    }
    public boolean add(ServerPlayer player,List<Spec> specs) { return state.add(player.getUUID(),specs,match.matchTick()); }
    public Optional<Vec3> deception(ServerPlayer observer) { return state.deception(point(observer.position()),match.matchTick()).map(s -> vec(s.point())); }
    /** 使用空手右键的同一输入；假人没有真实实体，不会遮挡战斗射线。 */
    public boolean dismantle(ServerPlayer player) {
        if(!match.standing(player) || match.faction(player.getUUID())!=EchoFaction.INTERCEPTOR || !player.getMainHandItem().isEmpty()) return false;
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(2.75));
        var wall=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double limit=wall.getType()==HitResult.Type.MISS?2.75*2.75:eye.distanceToSqr(wall.getLocation());
        var target=state.all().stream().filter(s -> s.kind().decoy() && s.active(match.matchTick()))
                .filter(s -> new AABB(vec(s.point()).add(-0.3,0,-0.3),vec(s.point()).add(0.3,1.85,0.3)).clip(eye,end).filter(p -> eye.distanceToSqr(p)<=limit).isPresent())
                .min(Comparator.comparingDouble(s -> eye.distanceToSqr(vec(s.point()))));
        if(target.isEmpty() || !state.dismantle(target.get().id(),match.matchTick())) return false;
        match.objectives().cancelActor(player); match.activeAction(player.getUUID()); return true;
    }
    public void tick() {
        long now=match.matchTick();
        for(var scene:state.dueSounds(now)) {
            Vec3 pos=vec(scene.point());
            if(scene.kind().submission()) org.agmas.noellesroles.game.modes.echo.objective.EchoAudio.submission(match,pos);
            else org.agmas.noellesroles.game.modes.echo.objective.EchoAudio.nearby(match,pos,SoundEvents.AMETHYST_BLOCK_CHIME,scene.soundRange(),1.5F);
        }
        for(ServerPlayer receiver:match.onlinePlayers()) {
            var views=match.present(receiver) && match.life(receiver.getUUID()).potentiallyEscapable()
                    ?state.all().stream().filter(s -> s.active(now) && (s.kind().submission() || receiver.distanceToSqr(vec(s.point()))<=64*64)).toList():List.<Scene>of();
            RoleData.getOptional(EchoRoleData.class,receiver).ifPresent(d -> d.projectScenes(views));
        }
    }
    public void depart(UUID owner) { state.depart(owner); }
    public void cleanup() {
        state.clear(); for(ServerPlayer p:match.onlinePlayers()) RoleData.getOptional(EchoRoleData.class,p).ifPresent(d -> d.projectScenes(List.of()));
    }
    private static void reject(String reason) { throw new IllegalStateException(reason); }
}

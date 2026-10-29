package org.agmas.noellesroles.game.modes.echo.action;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollPayload;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 翻滚复用原版速度与扫掠碰撞，不传送、不改碰撞箱，也不增加无敌帧。 */
public final class EchoRollService {
    private final EchoMatch match;
    private final ServerLevel level;
    private final Map<UUID, EchoRollState> states = new HashMap<>();
    private final Map<UUID, Vec3> pausedMotion = new HashMap<>();
    public EchoRollService(EchoMatch match, ServerLevel level) { this.match = match; this.level = level; }
    public boolean active(UUID owner) { var s = states.get(owner); return s != null && s.active(match.matchTick()); }
    public void publish(ServerPlayer player) {
        var s = states.get(player.getUUID());
        RoleData.getOptional(EchoRoleData.class, player).ifPresent(d -> d.projectRoll(s == null ? 0 : s.readyAt(), s == null ? 0 : s.until()));
    }
    public void start(ServerPlayer player, float x, float z) {
        if (!EchoRollState.validDirection(x,z) || match.faction(player.getUUID()) != EchoFaction.CARRIER || !match.standing(player)
                || !match.phase().acceptsActions() || match.bags().busy(player.getUUID()) || match.trades().busy(player.getUUID())
                || player.isPassenger() || player.isUsingItem() || !player.onGround() || player.isInWaterOrBubble() || player.isInLava()) return;
        var state = states.computeIfAbsent(player.getUUID(), ignored -> new EchoRollState());
        if (!state.ready(match.matchTick())) { publish(player); return; }
        Vec3 direction = new Vec3(x,0,z).normalize();
        var box = player.getBoundingBox().expandTowards(direction.scale(EchoRollState.DISTANCE));
        var area = AreasWorldComponent.KEY.get(level).getPlayArea();
        if (!area.contains(box.minX,box.minY,box.minZ) || !area.contains(box.maxX,box.maxY,box.maxZ)
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(box.minX,box.minY,box.minZ))
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(box.maxX,box.maxY,box.maxZ))) return;
        for (int cx = Math.floorDiv((int)Math.floor(box.minX),16); cx <= Math.floorDiv((int)Math.floor(box.maxX),16); cx++)
            for (int cz = Math.floorDiv((int)Math.floor(box.minZ),16); cz <= Math.floorDiv((int)Math.floor(box.maxZ),16); cz++)
                if (!level.hasChunk(cx,cz)) return;
        if (!level.noCollision(player, player.getBoundingBox().expandTowards(direction.scale(0.25)))) return;
        if (!state.start(match.matchTick())) return;
        match.objectives().cancelActor(player); match.activeAction(player.getUUID());
        // 参考 CombatRoll 的 3 格地面冲量与冰面补偿；速度只能由服务器计算。
        double friction = level.getBlockState(player.blockPosition().below()).getBlock().getFriction();
        double ice = friction > 0.6 ? Math.pow(0.6 / friction,2) : 1;
        motion(player, direction.scale(0.475 * EchoRollState.DISTANCE * ice).add(0,Math.min(0,player.getDeltaMovement().y),0));
        publish(player);
        animation(player,true,x,z);
    }
    public void tick() {
        for (var entry : states.entrySet()) {
            var player = match.connectedPlayer(entry.getKey()); var state = entry.getValue();
            if (state.until() > 0) {
                if (player == null) { state.cancel(); pausedMotion.remove(entry.getKey()); continue; }
                if (!state.active(match.matchTick()) || !match.standing(player) || !match.phase().acceptsActions()
                        || player.isPassenger() || player.isInWaterOrBubble() || player.isInLava()) cancel(player);
                else if (pausedMotion.containsKey(entry.getKey())) motion(player,pausedMotion.remove(entry.getKey()));
            }
            if (player != null && match.matchTick() % 5 == 0) publish(player);
        }
    }
    /** 冻结真实速度并保留恢复点；冷却和动作只跟随比赛 tick。 */
    public void freeze() {
        for (var entry : states.entrySet()) if (entry.getValue().active(match.matchTick())) {
            var player = match.connectedPlayer(entry.getKey()); if (player == null) continue;
            pausedMotion.putIfAbsent(entry.getKey(),player.getDeltaMovement()); motion(player,Vec3.ZERO);
        }
    }
    public void cancel(ServerPlayer player) {
        var state = states.get(player.getUUID()); if (state == null || state.until() == 0) return;
        state.cancel(); pausedMotion.remove(player.getUUID());
        motion(player,new Vec3(0,Math.min(0,player.getDeltaMovement().y),0));
        publish(player); animation(player,false,0,0);
    }
    public void cleanup() {
        for (var player : match.onlinePlayers()) cancel(player);
        states.clear(); pausedMotion.clear();
    }
    private void animation(ServerPlayer actor, boolean active, float x, float z) {
        var packet = new EchoRollPayload(match.id(),level.dimension().location(),actor.getUUID(),match.matchTick(),active,x,z);
        for (var observer : level.players()) if (observer == actor || observer.distanceToSqr(actor) <= 48 * 48)
            ServerPlayNetworking.send(observer,packet);
    }
    private static void motion(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity); player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}

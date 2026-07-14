package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.network.GunTracerS2CPacket;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械射击轨迹广播（所有枪通用，不限 60s 模式）：把弹道终点发给射手与周围观察者，
 * 客户端 {@code GunTracerRenderer} 渲染渐隐轨迹线。
 */
public final class GunTracers {

    private GunTracers() {
    }

    /** @param hit 命中的实体（null=未命中，按视线方向延伸 range）。 */
    public static void broadcast(ServerPlayer shooter, @Nullable Entity hit, double range) {
        Vec3 to = hit != null
                ? hit.getBoundingBox().getCenter()
                : shooter.getEyePosition().add(shooter.getViewVector(1.0F).normalize().scale(range));
        GunTracerS2CPacket packet = new GunTracerS2CPacket(shooter.getId(), to.x, to.y, to.z);
        for (ServerPlayer tracking : PlayerLookup.tracking(shooter)) {
            ServerPlayNetworking.send(tracking, packet);
        }
        ServerPlayNetworking.send(shooter, packet);
    }
}

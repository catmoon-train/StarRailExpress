package org.agmas.noellesroles.client.roulette;

import java.util.UUID;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.event.client.ClientGameplayEvents;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.minigame.roulette.RoulettePhase;

/** 临时聚焦赌桌；只修饰返回的 FOV，不写玩家保存的视野设置。 */
public final class RoulettePresentation {
    private static ClientLevel world;
    private static UUID session, seat;
    private static float blend, previousBlend, oldYaw, oldPitch;
    private RoulettePresentation() {}
    static void register() {
        ClientGameplayEvents.ALLOW_AUXILIARY_HUD.register(player -> !active());
        ClientGameplayEvents.WORLD_FOV.register((player, fov, partial) -> cameraAllowed()
                ? Mth.lerp(Mth.lerp(partial, previousBlend, blend), fov, Math.min(fov, 48)) : fov);
        ClientTickEvents.END_CLIENT_TICK.register(RoulettePresentation::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }
    public static boolean active() { return focused() != null; }
    private static RouletteClient.State focused() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || !(mc.player.getVehicle() instanceof SeatEntity)) return null;
        var state = RouletteClient.participant();
        return state != null && state.view.phase() != RoulettePhase.WAITING
                && mc.player.distanceToSqr(Vec3.atCenterOf(state.pos)) < 36 ? state : null;
    }
    private static boolean cameraAllowed() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.getCameraEntity() == mc.player
                && mc.options.getCameraType().isFirstPerson() && !AdvancedCameraDirector.shouldOverride()
                && !AdvancedCameraDirector.isPresentationActive()
                && !org.agmas.noellesroles.client.PriestHeavenClient.isOverheadCamera()
                && !io.wifi.starrailexpress.client.SecurityCameraClientState.isInSecurityMode();
    }
    private static void tick(Minecraft mc) {
        if (world != mc.level) { clear(); world = mc.level; }
        var state = focused();
        previousBlend = blend;
        blend = Mth.clamp(blend + (state == null ? -.125F : .125F), 0, 1);
        if (state == null) {
            if (session != null && mc.player != null && mc.player.getVehicle() != null
                    && mc.player.getVehicle().getUUID().equals(seat)) {
                mc.player.setYRot(oldYaw); mc.player.setXRot(oldPitch);
            }
            session = null; seat = null; return;
        }
        if (!state.view.session().equals(session) && cameraAllowed()) {
            session = state.view.session(); seat = mc.player.getVehicle().getUUID();
            oldYaw = mc.player.getYRot(); oldPitch = mc.player.getXRot();
            Vec3 delta = Vec3.atCenterOf(state.pos).add(0, .35, 0).subtract(mc.player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
            mc.player.setYRot(yaw); mc.player.yRotO = yaw;
            mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        }
    }
    private static void clear() { world = null; session = null; seat = null; blend = previousBlend = 0; }
}

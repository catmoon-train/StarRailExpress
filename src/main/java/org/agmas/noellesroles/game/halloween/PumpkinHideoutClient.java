package org.agmas.noellesroles.game.halloween;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 仅本地进入者可见的南瓜据点边界，不扫描实体或依赖未同步的实体 tag。 */
@Environment(EnvType.CLIENT)
public final class PumpkinHideoutClient {
    private static ClientLevel lastLevel;
    private static Vec3 feet;
    private static int leaseTicks;

    private PumpkinHideoutClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PumpkinHideoutS2CPayload.ID, (packet, context) ->
                context.client().execute(() -> {
                    ClientLevel level = context.client().level;
                    if (level == null || !level.dimension().location().equals(packet.dimension())) return;
                    if (packet.anchor() == null) { clear(); return; }
                    lastLevel = level;
                    feet = Vec3.atBottomCenterOf(packet.anchor());
                    leaseTicks = 60;
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (lastLevel != client.level || feet != null && --leaseTicks <= 0) clear();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(PumpkinHideoutClient::renderBoundary);
    }

    private static void clear() {
        feet = null;
        lastLevel = null;
        leaseTicks = 0;
    }

    private static void renderBoundary(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (feet == null || client.level == null || lastLevel != client.level || client.player == null
                || client.getCameraEntity() != client.player || client.player.isSpectator() || !client.player.isAlive()
                || !HalloweenRuntime.isEnabled(client.level)
                || !PumpkinHideoutRules.contains(feet, client.player.position())
                || context.matrixStack() == null || context.consumers() == null) return;
        var poses = context.matrixStack();
        var vertices = context.consumers().getBuffer(RenderType.lines());
        Vec3 camera = context.camera().getPosition();
        AABB bounds = PumpkinHideoutRules.bounds(feet);
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poses, vertices, bounds, 1.0F, 0.65F, 0.15F, 0.85F);
            // 下沿位于地板下面；补一圈贴地轮廓，让小体型玩家也看得清水平退出边界。
            LevelRenderer.renderLineBox(poses, vertices,
                    new AABB(bounds.minX, feet.y + 0.015, bounds.minZ,
                            bounds.maxX, feet.y + 0.015, bounds.maxZ), 1.0F, 0.8F, 0.25F, 0.95F);
        } finally {
            poses.popPose();
        }
    }
}

package org.agmas.noellesroles.game.halloween;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 客户端只绘制活动状态；不参与邀请、容器及护盾消费判定。 */
public final class HalloweenClient {
    private static ClientLevel lastLevel;
    private HalloweenClient() {}

    public static void register() {
        HalloweenVillageClient.register();
        ClientPlayNetworking.registerGlobalReceiver(HalloweenNetwork.Snapshot.ID, (snapshot, context) ->
                context.client().execute(() -> {
                    var level = context.client().level;
                    if (level != null && level.dimension().location().equals(snapshot.dimension())) {
                        if (lastLevel != level) { HalloweenNetwork.clearClient(); lastLevel = level; }
                        HalloweenNetwork.apply(snapshot);
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HalloweenNetwork.clearClient(); lastLevel = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (lastLevel != client.level) { HalloweenNetwork.clearClient(); lastLevel = client.level; }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(HalloweenClient::renderLinks);
    }

    private static void renderLinks(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !HalloweenRuntime.isEnabled(client.level)
                || context.matrixStack() == null || context.consumers() == null) return;
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        var poses = context.matrixStack();
        VertexConsumer vertices = context.consumers().getBuffer(RenderType.leash());
        for (var state : HalloweenNetwork.clientStates()) {
            if (state.partner() == null || state.player().compareTo(state.partner()) >= 0) continue;
            Player a = client.level.getPlayerByUUID(state.player());
            Player b = client.level.getPlayerByUUID(state.partner());
            if (a == null || b == null || a.isSpectator() || b.isSpectator()) continue;
            Vec3 from = a.getPosition(partial).add(0, a.getBbHeight() * .6, 0);
            Vec3 to = b.getPosition(partial).add(0, b.getBbHeight() * .6, 0);
            Vec3 delta = to.subtract(from);
            Vec3 side = new Vec3(-delta.z, 0, delta.x).normalize().scale(.025);
            if (side.lengthSqr() < .00001) side = new Vec3(.025, 0, 0);
            int light = LevelRenderer.getLightColor(client.level, a.blockPosition());
            poses.pushPose();
            poses.translate(from.x - camera.x, from.y - camera.y, from.z - camera.z);
            var pose = poses.last();
            // 退化首尾顶点隔开多条绳索，避免 TRIANGLE_STRIP 在不同链接之间连片。
            vertex(vertices, pose, Vec3.ZERO.subtract(side), 0, light);
            for (int i = 0; i <= 24; i++) {
                double t = i / 24.0;
                Vec3 point = delta.scale(t).add(0, -.18 * Math.sin(Math.PI * t), 0);
                vertex(vertices, pose, point.subtract(side), i, light);
                vertex(vertices, pose, point.add(side), i, light);
            }
            vertex(vertices, pose, delta.add(side), 24, light);
            poses.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                               Vec3 point, int segment, int light) {
        boolean bright = segment % 2 == 0;
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(bright ? 160 : 105, bright ? 113 : 69, bright ? 55 : 30, 255).setLight(light);
    }
}

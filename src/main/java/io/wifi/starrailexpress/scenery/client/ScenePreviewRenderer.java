package io.wifi.starrailexpress.scenery.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ScenePreviewRenderer {
    private static final List<PreviewMesh> projectionMeshes = new ArrayList<>();
    private static Vec3 projectionOrigin = Vec3.ZERO;

    private ScenePreviewRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> SceneAssetClient.tickPreview());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ScenePreviewRenderer::render);
    }

    public static void rebuild(List<SceneAssetClient.PreviewBlock> blocks, AABB sourceArea) {
        if (!RenderSystem.isOnRenderThread()) {
            List<SceneAssetClient.PreviewBlock> snapshot = List.copyOf(blocks);
            RenderSystem.recordRenderCall(() -> rebuild(snapshot, sourceArea));
            return;
        }
        releaseOnRenderThread();
        if (blocks.isEmpty()) {
            return;
        }
        projectionOrigin = new Vec3(sourceArea.minX, sourceArea.minY, sourceArea.minZ);
        Map<RenderType, List<SceneAssetClient.PreviewBlock>> byRenderType = new LinkedHashMap<>();
        for (SceneAssetClient.PreviewBlock block : blocks) {
            if (block.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            RenderType renderType = ItemBlockRenderTypes.getRenderType(block.state(), false);
            byRenderType.computeIfAbsent(renderType, ignored -> new ArrayList<>()).add(block);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (Map.Entry<RenderType, List<SceneAssetClient.PreviewBlock>> entry : byRenderType.entrySet()) {
            RenderType renderType = entry.getKey();
            BufferBuilder builder = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
            PoseStack poseStack = new PoseStack();
            for (SceneAssetClient.PreviewBlock block : entry.getValue()) {
                poseStack.pushPose();
                poseStack.translate(
                        block.pos().getX() - projectionOrigin.x,
                        block.pos().getY() - projectionOrigin.y,
                        block.pos().getZ() - projectionOrigin.z);
                dispatcher.getModelRenderer().renderModel(
                        poseStack.last(),
                        builder,
                        block.state(),
                        dispatcher.getBlockModel(block.state()),
                        1.0F, 1.0F, 1.0F,
                        0x00F000F0,
                        OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(mesh);
                VertexBuffer.unbind();
                projectionMeshes.add(new PreviewMesh(renderType, vbo));
            }
        }
    }

    public static void release() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ScenePreviewRenderer::releaseOnRenderThread);
            return;
        }
        releaseOnRenderThread();
    }

    private static void releaseOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        for (PreviewMesh mesh : projectionMeshes) {
            mesh.vbo().close();
        }
        projectionMeshes.clear();
        projectionOrigin = Vec3.ZERO;
    }

    private static void render(WorldRenderContext context) {
        AreasWorldComponent areas = SREClient.areaComponent;
        if (!SceneAssetClient.isPreviewEnabled() || areas == null || !areas.isSceneAreaConfigured()
                || context.consumers() == null) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        AABB source = areas.getSceneArea();
        AABB playArea = areas.getPlayArea();
        AABB target = SceneGeometry.targetArea(source, playArea, areas.getSceneDisplayOffset());
        AABB expanded = SceneGeometry.expandedArea(source);
        Vec3 projectionOffset = SceneAssetClient.previewRenderOffset();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        if (!projectionMeshes.isEmpty()) {
            if (areas.getSceneScroll() == AreasWorldComponent.ScrollAxis.NONE) {
                drawProjectionCopy(context, poseStack, projectionOffset);
            } else {
                Vec3 loopStep = loopStep(expanded, areas.getSceneScroll());
                drawProjectionCopy(context, poseStack, projectionOffset.subtract(loopStep));
                drawProjectionCopy(context, poseStack, projectionOffset);
            }
        }

        VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, source, 0.0F, 0.95F, 1.0F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, playArea, 0.1F, 1.0F, 0.25F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, expanded, 1.0F, 0.85F, 0.0F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, target, 0.9F, 0.2F, 1.0F, 1.0F);

        AABB loopBox = expanded.move(projectionOffset);
        LevelRenderer.renderLineBox(poseStack, lines, loopBox, 1.0F, 0.35F, 0.1F, 0.9F);
        if (areas.getSceneScroll() != AreasWorldComponent.ScrollAxis.NONE) {
            Vec3 step = loopStep(expanded, areas.getSceneScroll());
            LevelRenderer.renderLineBox(poseStack, lines, loopBox.move(step.scale(-1.0D)),
                    1.0F, 0.35F, 0.1F, 0.45F);
        }
        renderDirectionArrow(poseStack, lines, target, areas.getSceneScroll());
        poseStack.popPose();
    }

    private static void drawProjectionCopy(WorldRenderContext context, PoseStack poseStack, Vec3 offset) {
        poseStack.pushPose();
        poseStack.translate(
                projectionOrigin.x + offset.x,
                projectionOrigin.y + offset.y,
                projectionOrigin.z + offset.z);
        for (PreviewMesh mesh : projectionMeshes) {
            mesh.renderType().setupRenderState();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, SceneAssetClient.getPreviewAlpha());
            mesh.vbo().bind();
            mesh.vbo().drawWithShader(
                    poseStack.last().pose(),
                    context.projectionMatrix(),
                    RenderSystem.getShader());
            VertexBuffer.unbind();
            mesh.renderType().clearRenderState();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static Vec3 loopStep(AABB expanded, AreasWorldComponent.ScrollAxis axis) {
        return switch (axis) {
            case X -> new Vec3(expanded.getXsize(), 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, expanded.getYsize(), 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, expanded.getZsize());
            case NONE -> Vec3.ZERO;
        };
    }

    private static void renderDirectionArrow(PoseStack poseStack, VertexConsumer lines, AABB target,
            AreasWorldComponent.ScrollAxis axis) {
        if (axis == AreasWorldComponent.ScrollAxis.NONE) {
            return;
        }
        Vec3 center = target.getCenter();
        double length = Math.min(8.0D, switch (axis) {
            case X -> target.getXsize() * 0.25D;
            case Y -> target.getYsize() * 0.25D;
            case Z -> target.getZsize() * 0.25D;
            case NONE -> 0.0D;
        });
        Vec3 end = switch (axis) {
            case X -> center.add(length, 0.0D, 0.0D);
            case Y -> center.add(0.0D, length, 0.0D);
            case Z -> center.add(0.0D, 0.0D, length);
            case NONE -> center;
        };
        AABB shaft = new AABB(center, end).inflate(0.08D);
        AABB head = new AABB(end.subtract(0.3D, 0.3D, 0.3D), end.add(0.3D, 0.3D, 0.3D));
        LevelRenderer.renderLineBox(poseStack, lines, shaft, 1.0F, 0.25F, 0.8F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, head, 1.0F, 0.25F, 0.8F, 1.0F);
    }

    private record PreviewMesh(RenderType renderType, VertexBuffer vbo) {
    }
}

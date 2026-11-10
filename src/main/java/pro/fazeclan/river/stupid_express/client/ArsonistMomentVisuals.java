package pro.fazeclan.river.stupid_express.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistPackets;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRules;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 纵火时刻的屏幕边火、场地假火和全身火焰；不修改方块、实体火焰状态或伤害。 */
@Environment(EnvType.CLIENT)
public final class ArsonistMomentVisuals {
    private static final ResourceLocation FIRE = ResourceLocation.withDefaultNamespace("block/fire_0");
    private static final Map<UUID, Integer> REMAINING = new HashMap<>();
    private static final Map<BlockPos, Vec3> FLOOR_FIRE = new LinkedHashMap<>();
    private static ClientLevel lastLevel;

    private ArsonistMomentVisuals() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ArsonistMomentVisuals::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ArsonistMomentVisuals::renderWorldFire);
        HudRenderCallback.EVENT.register((graphics, delta) -> renderEdges(graphics));
    }

    public static void accept(ArsonistPackets.MomentS2C packet) {
        if (packet.active()) REMAINING.put(packet.owner(), Mth.clamp(packet.remainingTicks(), 0, ArsonistRules.MOMENT_TICKS));
        else REMAINING.remove(packet.owner());
        if (REMAINING.isEmpty()) FLOOR_FIRE.clear();
    }

    public static void clear() {
        REMAINING.clear();
        FLOOR_FIRE.clear();
        lastLevel = null;
    }

    private static boolean visible(Minecraft client) {
        return client.level != null && client.player != null && !REMAINING.isEmpty()
                && SREClient.gameComponent != null
                && SREClient.gameComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    private static float progress() {
        int remaining = REMAINING.values().stream().mapToInt(Integer::intValue).min().orElse(ArsonistRules.MOMENT_TICKS);
        return Mth.clamp(1.0F - remaining / (float) ArsonistRules.MOMENT_TICKS, 0, 1);
    }

    private static void tick(Minecraft client) {
        if (client.level != lastLevel) {
            FLOOR_FIRE.clear();
            lastLevel = client.level;
        }
        if (!visible(client)) {
            FLOOR_FIRE.clear();
            return;
        }
        if (!SREClient.gameComponent.isSkillAvailable || SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()) return;
        REMAINING.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
        if (client.level.getGameTime() % 2 == 0) {
            for (UUID id : REMAINING.keySet()) {
                Player owner = client.level.getPlayerByUUID(id);
                if (owner == null || owner.isSpectator() || owner.distanceToSqr(client.player) > 64 * 64
                        || owner == client.player && client.options.getCameraType().isFirstPerson()) continue;
                // 螺旋环绕全身，只生成本地火焰粒子，不调用setRemainingFireTicks。
                for (int i = 0; i < 9; i++) {
                    double angle = client.level.getGameTime() * 0.17 + i * Math.PI * 2 / 3;
                    double height = (i + 0.5) / 9.0 * owner.getBbHeight();
                    double radius = owner.getBbWidth() * 0.6;
                    client.level.addParticle(ParticleTypes.FLAME,
                            owner.getX() + Math.cos(angle) * radius, owner.getY() + height,
                            owner.getZ() + Math.sin(angle) * radius, 0, 0.025, 0);
                }
            }
        }
        if (client.level.getGameTime() % 10 == 0) updateFloor(client);
    }

    /** 每半秒最多16条短射线、最多96处假火；只采样客户端已经加载的地面。 */
    private static void updateFloor(Minecraft client) {
        Vec3 center = client.player.position();
        FLOOR_FIRE.entrySet().removeIf(entry -> entry.getValue().distanceToSqr(center) > 32 * 32
                || !client.level.hasChunkAt(entry.getKey()));
        int targetCount = 6 + (int) (90 * progress());
        for (int attempt = 0; attempt < 16 && FLOOR_FIRE.size() < targetCount; attempt++) {
            double x = Math.floor(center.x + client.level.random.nextInt(49) - 24) + 0.5;
            double z = Math.floor(center.z + client.level.random.nextInt(49) - 24) + 0.5;
            Vec3 from = new Vec3(x, center.y + 1.2, z);
            Vec3 to = new Vec3(x, center.y - 6, z);
            if (!client.level.hasChunkAt(BlockPos.containing(from)) || !client.level.hasChunkAt(BlockPos.containing(to))) continue;
            var hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            Vec3 position = hit.getLocation().add(0, 0.015, 0);
            BlockPos key = BlockPos.containing(position);
            if (!client.level.getFluidState(key).isEmpty()) continue;
            FLOOR_FIRE.putIfAbsent(key, position);
        }
    }

    private static TextureAtlasSprite fireSprite(Minecraft client) {
        return client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FIRE);
    }

    private static void renderWorldFire(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!visible(client) || context.matrixStack() == null) return;
        var buffers = client.renderBuffers().bufferSource();
        var type = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vertices = buffers.getBuffer(type);
        TextureAtlasSprite sprite = fireSprite(client);
        float intensity = progress();
        for (Vec3 position : FLOOR_FIRE.values()) {
            drawFire(context, vertices, sprite, position, 0.6F + intensity * 0.5F,
                    0.65F + intensity * 0.65F, 0.75F);
        }
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (UUID id : REMAINING.keySet()) {
            Player owner = client.level.getPlayerByUUID(id);
            if (owner == null || owner.isSpectator() || owner.distanceToSqr(client.player) > 64 * 64
                    || owner == client.player && client.options.getCameraType().isFirstPerson()) continue;
            Vec3 feet = owner.getPosition(partial);
            for (int layer = 0; layer < 3; layer++) {
                drawFire(context, vertices, sprite, feet.add(0, owner.getBbHeight() * layer / 3, 0),
                        owner.getBbWidth() * 1.6F, owner.getBbHeight() * 0.58F, 0.9F);
            }
        }
        buffers.endBatch(type);
    }

    /** 与摄像机方向一致的双交叉火焰面，保留深度测试，不在墙后渲染透视火焰。 */
    private static void drawFire(WorldRenderContext context, VertexConsumer vertices, TextureAtlasSprite sprite,
            Vec3 position, float width, float height, float alpha) {
        PoseStack poses = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        poses.pushPose();
        poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        poses.mulPose(Axis.YP.rotationDegrees(-context.camera().getYRot()));
        for (int face = 0; face < 2; face++) {
            Matrix4f matrix = poses.last().pose();
            float half = width * 0.5F;
            int color = ((int) (255 * alpha) << 24) | 0xFFFFFF;
            vertex(vertices, matrix, -half, 0, sprite.getU0(), sprite.getV1(), color);
            vertex(vertices, matrix, half, 0, sprite.getU1(), sprite.getV1(), color);
            vertex(vertices, matrix, half, height, sprite.getU1(), sprite.getV0(), color);
            vertex(vertices, matrix, -half, height, sprite.getU0(), sprite.getV0(), color);
            poses.mulPose(Axis.YP.rotationDegrees(90));
        }
        poses.popPose();
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, float u, float v, int color) {
        vertices.addVertex(matrix, x, y, 0).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }

    private static void renderEdges(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!visible(client) || client.options.hideGui) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int edgeX = Math.min(52, Math.max(16, width / 12));
        int edgeY = Math.min(48, Math.max(14, height / 9));
        float alpha = 0.22F + 0.2F * progress() + 0.035F * (float) Math.sin(client.level.getGameTime() * 0.08);
        TextureAtlasSprite sprite = fireSprite(client);
        graphics.setColor(1, 0.82F, 0.6F, alpha);
        band(graphics, sprite, width, edgeY, 0, height - edgeY, 0);
        band(graphics, sprite, width, edgeY, width, edgeY, 180);
        band(graphics, sprite, height, edgeX, edgeX, 0, 90);
        band(graphics, sprite, height, edgeX, width - edgeX, height, -90);
        graphics.setColor(1, 1, 1, 1);
    }

    private static void band(GuiGraphics graphics, TextureAtlasSprite sprite,
            int length, int thickness, int x, int y, int rotation) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        for (int offset = 0; offset < length; offset += 48) {
            graphics.blit(offset, 0, 0, Math.min(48, length - offset), thickness, sprite);
        }
        graphics.pose().popPose();
    }
}

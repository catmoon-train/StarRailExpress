package pro.fazeclan.river.stupid_express.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistPackets;

import java.util.ArrayList;
import java.util.List;

/** 独立受控火束：位置由本类逐tick推进，不使用会随机化速度的原版FlameParticle。 */
public final class ArsonistFireJetClient {
    private static final ResourceLocation FIRE = ResourceLocation.withDefaultNamespace("block/fire_0");
    private static final List<Ember> EMBERS = new ArrayList<>();
    private static final List<Jet> JETS = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static int deathTicks;
    private static final class Ember {
        Vec3 previous;
        Vec3 position;
        final Vec3 direction;
        final double speed;
        final double maxTravel;
        final int life;
        int age;
        Ember(Vec3 position, Vec3 direction, double speed, double maxTravel, int life) {
            this.position = position; previous = position; this.direction = direction;
            this.speed = speed; this.maxTravel = maxTravel; this.life = life;
        }
    }
    private static final class Jet {
        final Vec3 muzzle;
        final Vec3 direction;
        final double length;
        int age;
        Jet(Vec3 muzzle, Vec3 direction, double length) { this.muzzle = muzzle; this.direction = direction; this.length = length; }
    }
    private ArsonistFireJetClient() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) { clear(); lastLevel = client.level; }
            if (deathTicks > 0) deathTicks--;
            EMBERS.removeIf(ember -> {
                ember.previous = ember.position;
                ember.position = ember.position.add(ember.direction.scale(ember.speed));
                return ++ember.age >= ember.life || ember.age * ember.speed >= ember.maxTravel;
            });
            JETS.removeIf(jet -> ++jet.age >= 4);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayNetworking.registerGlobalReceiver(ArsonistPackets.DeathFxS2C.ID,
                (packet, context) -> context.client().execute(() -> deathTicks = 60));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ArsonistFireJetClient::render);
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            if (deathTicks <= 0 || Minecraft.getInstance().level == null) return;
            var client = Minecraft.getInstance();
            float alpha = Math.min(0.5F, deathTicks / 80.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ((int) (alpha * 65) << 24) | 0xFF6317);
            var sprite = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FIRE);
            graphics.setColor(1, 0.75F, 0.45F, alpha);
            int h = graphics.guiHeight() / 3;
            for (int x = 0; x < graphics.guiWidth(); x += 48) {
                graphics.blit(x, graphics.guiHeight() - h, 0, Math.min(48, graphics.guiWidth() - x), h, sprite);
            }
            graphics.setColor(1, 1, 1, 1);
        });
    }

    private static void clear() { EMBERS.clear(); JETS.clear(); deathTicks = 0; }

    public static void emit(ClientLevel level, Vec3 eye, float[] params) {
        if (params.length < 3) return;
        Minecraft client = Minecraft.getInstance();
        if (eye.distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) > 48 * 48) return;
        Vec3 look = Vec3.directionFromRotation(params[1], params[0]).normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();
        if (params.length > 4 && level.getEntity((int) params[4]) instanceof Player shooter) eye = shooter.getEyePosition();
        double hand = params.length > 5 ? params[5] : 1;
        Vec3 muzzle = eye.add(look.scale(0.72)).add(right.scale(0.28 * hand)).add(0, -0.32, 0);
        var barrelClip = level.clip(new ClipContext(eye, muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (barrelClip.getType() != HitResult.Type.MISS) muzzle = barrelClip.getLocation().subtract(look.scale(0.06));
        double range = Math.max(0.1, Math.min(5, params[2]) - Math.max(0, muzzle.subtract(eye).dot(look)));
        double centralRange = clippedRange(level, muzzle, look, range);
        if (JETS.size() >= 32) JETS.remove(0);
        JETS.add(new Jet(muzzle, look, centralRange));
        int setting = client.options.particles().get().ordinal();
        int count = setting >= 2 ? 0 : setting == 1 ? 5 : 10;
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double spread = (i < count * 2 / 3 ? 0.06 : 0.16) * Math.sqrt(level.random.nextDouble());
            Vec3 direction = look.add(right.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)).normalize();
            double speed = 0.42 + level.random.nextDouble() * 0.16;
            add(new Ember(muzzle, direction, speed, clippedRange(level, muzzle, direction, range), 12));
        }
    }

    public static void killBurst(ClientLevel level, Vec3 center) {
        int setting = Minecraft.getInstance().options.particles().get().ordinal();
        int count = setting >= 2 ? 6 : setting == 1 ? 12 : 24;
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / Math.max(1, count / 2);
            Vec3 direction = new Vec3(Math.cos(a) * 0.7, 0.4 + level.random.nextDouble() * 0.8, Math.sin(a) * 0.7).normalize();
            Vec3 start = center.add(direction.scale(0.25));
            add(new Ember(start, direction, 0.07 + level.random.nextDouble() * 0.07,
                    clippedRange(level, start, direction, 2.5), 24));
        }
    }

    private static void add(Ember ember) {
        if (EMBERS.size() >= 192) EMBERS.remove(0);
        EMBERS.add(ember);
    }

    private static double clippedRange(ClientLevel level, Vec3 start, Vec3 direction, double range) {
        var hit = level.clip(new ClipContext(start, start.add(direction.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return hit.getType() == HitResult.Type.MISS ? range : Math.max(0, hit.getLocation().distanceTo(start) - 0.035);
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || context.matrixStack() == null || EMBERS.isEmpty() && JETS.isEmpty()) return;
        var buffers = client.renderBuffers().bufferSource();
        var type = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vertices = buffers.getBuffer(type);
        TextureAtlasSprite sprite = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FIRE);
        Matrix4f matrix = context.matrixStack().last().pose();
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cameraRight = Vec3.directionFromRotation(0, context.camera().getYRot() + 90).normalize();
        Vec3 cameraUp = cameraRight.cross(Vec3.directionFromRotation(context.camera().getXRot(), context.camera().getYRot())).normalize();
        for (Ember ember : EMBERS) {
            Vec3 center = ember.previous.lerp(ember.position, partial).subtract(camera);
            double size = 0.065 + ember.age * 0.021;
            Vec3 right = cameraRight.scale(size), up = cameraUp.scale(size);
            int color = ((int) (220 * (1 - ember.age / (float) ember.life)) << 24) | 0xFFE8B0;
            quad(vertices, matrix, sprite, center.subtract(right).subtract(up), center.add(right).subtract(up),
                    center.add(right).add(up), center.subtract(right).add(up), color);
        }
        for (Jet jet : JETS) {
            Vec3 side = jet.direction.cross(camera.subtract(jet.muzzle)).normalize();
            if (side.lengthSqr() < 0.001) side = jet.direction.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
            for (int plane = 0; plane < 2; plane++) {
                for (int i = 0; i < 6; i++) {
                    double from = jet.length * i / 6, to = jet.length * (i + 1) / 6;
                    Vec3 a = jet.muzzle.add(jet.direction.scale(from)).subtract(camera);
                    Vec3 b = jet.muzzle.add(jet.direction.scale(to)).subtract(camera);
                    double wa = 0.045 + from * 0.05, wb = 0.045 + to * 0.05;
                    int alpha = (int) ((plane == 0 ? 145 : 95) * (1 - jet.age / 4.0));
                    quad(vertices, matrix, sprite, a.subtract(side.scale(wa)), b.subtract(side.scale(wb)),
                            b.add(side.scale(wb)), a.add(side.scale(wa)), (alpha << 24) | 0xFFD080);
                }
                side = jet.direction.cross(side).normalize();
            }
        }
        buffers.endBatch(type);
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, TextureAtlasSprite sprite,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(out, matrix, a, sprite.getU0(), sprite.getV1(), color);
        vertex(out, matrix, b, sprite.getU1(), sprite.getV1(), color);
        vertex(out, matrix, c, sprite.getU1(), sprite.getV0(), color);
        vertex(out, matrix, d, sprite.getU0(), sprite.getV0(), color);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int color) {
        out.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }
}

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 通用包驱动的本地网格演出；不创建实体，不发包，不以烟雾代替鬼形。 */
public final class SackGhostEffects {
    private enum Kind { VEIL, PUSH, EFFIGY, ECHO_DEPART, ECHO_RETURN, WAIL, PARASITE, SEIZE, ESCAPE, REALM_DEPART, REALM_RETURN }
    private static final int MAX_EFFECTS = 48;
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final RenderType INK = RenderType.entityTranslucent(SackGhostRenderer.TEXTURE);
    private static SackGhostHandModel hand;
    private static ClientLevel world;
    private static boolean registered;

    private static final class Effect {
        final Kind kind;
        final Vec3 origin;
        final int duration;
        final int owner;
        final int target;
        final float radius;
        final SackGhostEffigyRenderer.Echo echo;
        int age;

        Effect(Kind kind, Vec3 origin, int duration, int owner, int target, float radius, Player player) {
            this.kind = kind;
            this.origin = origin;
            this.duration = duration;
            this.owner = owner;
            this.target = target;
            this.radius = radius;
            echo = SackGhostEffigyRenderer.snapshot(player);
        }
    }

    private SackGhostEffects() {}

    public static void register() {
        if (registered) return;
        registered = true;
        for (Kind kind : Kind.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("noellesroles", "sack_ghost/" + kind.name().toLowerCase(java.util.Locale.ROOT));
            CustomParticleHandlers.register(id, (level, origin, duration, params) -> receive(level, kind, origin, duration, params));
        }
        WorldRenderEvents.AFTER_ENTITIES.register(SackGhostEffects::render);
    }

    private static int id(float[] params, int index) {
        return index < params.length && Float.isFinite(params[index]) ? (int) params[index] : -1;
    }

    private static float radius(float[] params, int index, float fallback) {
        return index < params.length && Float.isFinite(params[index]) ? Mth.clamp(params[index], 0.5F, 16) : fallback;
    }

    private static void receive(ClientLevel level, Kind kind, Vec3 origin, int duration, float[] params) {
        Minecraft mc = Minecraft.getInstance();
        SackGhostClient.useWorld(level);
        if (world != level) { clear(); world = level; }
        if (mc.player == null || !mc.player.isAlive() || !SackGhostClient.playable()) return;
        int owner = switch (kind) {
            case PUSH, PARASITE, SEIZE -> id(params, 1);
            case VEIL -> -1;
            default -> id(params, 0);
        };
        int target = switch (kind) {
            case PARASITE, SEIZE -> id(params, 0);
            case EFFIGY -> id(params, 1);
            default -> -1;
        };
        if (kind == Kind.PARASITE || kind == Kind.WAIL || kind == Kind.ECHO_DEPART || kind == Kind.ESCAPE) {
            EFFECTS.removeIf(effect -> effect.kind == kind && effect.owner == owner && (kind != Kind.PARASITE || effect.target == target));
        }
        if (kind == Kind.ECHO_RETURN) EFFECTS.removeIf(effect -> effect.kind == Kind.ECHO_DEPART && effect.owner == owner);
        // 0 时长寄生包必须先处理，即使目标已在视距外，也必须撤销旧附着。
        if (duration <= 0) return;
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(origin) > 64 * 64) return;
        int max = switch (kind) {
            case PARASITE -> 300;
            case ESCAPE -> 240;
            case ECHO_DEPART -> 160;
            case WAIL -> 100;
            case VEIL -> 60;
            case SEIZE -> 10;
            default -> 25;
        };
        float radius = switch (kind) {
            case VEIL -> radius(params, 0, 5);
            case PUSH -> radius(params, 0, 7);
            case WAIL -> radius(params, 1, 10);
            case ECHO_RETURN -> radius(params, 1, 4);
            default -> 2;
        };
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.removeFirst();
        Entity source = level.getEntity(owner);
        EFFECTS.add(new Effect(kind, origin, Math.min(duration, max), owner, target, radius, source instanceof Player p ? p : null));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { clear(); world = mc.level; }
        if (world == null) return;
        // 死亡/实体消失清理不依赖时钟；暂停期间不推进任何演出寿命。
        EFFECTS.removeIf(effect -> dead(world.getEntity(effect.owner)) || dead(world.getEntity(effect.target))
                || (effect.age > 10 && ((effect.owner >= 0 && world.getEntity(effect.owner) == null)
                || (followsTarget(effect.kind) && world.getEntity(effect.target) == null))));
        if (!SackGhostClient.clockRunning()) return;
        EFFECTS.removeIf(effect -> ++effect.age >= effect.duration);
    }

    private static boolean dead(Entity entity) {
        return entity != null && (entity.isRemoved() || entity instanceof LivingEntity living && !living.isAlive()
                || entity instanceof Player player && !GameUtils.isPlayerAliveAndSurvival(player));
    }

    private static boolean followsOwner(Kind kind) { return kind == Kind.WAIL || kind == Kind.ESCAPE; }
    private static boolean followsTarget(Kind kind) { return kind == Kind.PARASITE || kind == Kind.SEIZE; }

    public static int detail() {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 2;
            case DECREASED -> 1;
            case MINIMAL -> 0;
        };
    }

    private static Vec3 position(Entity entity, float partial) {
        return new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()), Mth.lerp(partial, entity.zo, entity.getZ()));
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (world == null || world != mc.level || !SackGhostClient.playable() || EFFECTS.isEmpty()
                || context.matrixStack() == null || context.consumers() == null) return;
        int detail = detail();
        int budget = detail == 2 ? MAX_EFFECTS : detail == 1 ? 24 : 8;
        float partial = SackGhostClient.visualPartial(context.tickCounter().getGameTimeDeltaPartialTick(false));
        Vec3 camera = context.camera().getPosition();
        PoseStack poses = context.matrixStack();
        for (Effect effect : EFFECTS) {
            if (budget <= 0) break;
            Entity attached = world.getEntity(followsTarget(effect.kind) ? effect.target : effect.owner);
            Vec3 center = (followsOwner(effect.kind) || followsTarget(effect.kind)) && attached != null ? position(attached, partial) : effect.origin;
            double distance = camera.distanceToSqr(center);
            int range = detail == 2 ? 48 : detail == 1 ? 32 : 20;
            if (distance > range * range || (context.frustum() != null && !context.frustum().isVisible(new AABB(center, center).inflate(effect.radius + 3)))) continue;
            // 最少档只保留关键技能轮廓，不画鬼群、碎布和多层音浪。
            if (detail == 0 && (effect.kind == Kind.ESCAPE || effect.kind == Kind.REALM_DEPART || effect.kind == Kind.REALM_RETURN)) continue;
            budget--;
            float age = effect.age + partial;
            float progress = Mth.clamp(age / effect.duration, 0, 1);
            float fade = Math.min(1, age / 3) * Math.min(1, (effect.duration - age) / 5);
            poses.pushPose();
            poses.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            renderEffect(effect, attached, poses, context.consumers(), age, progress, fade, detail, partial);
            poses.popPose();
        }
    }

    private static void renderEffect(Effect effect, Entity attached, PoseStack poses, MultiBufferSource buffers,
                                     float age, float progress, float fade, int detail, float partial) {
        float time = SackGhostClient.visualTime(partial);
        float yaw = attached instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : effect.echo.yaw();
        switch (effect.kind) {
            case PUSH -> {
                float radius = effect.radius * (1 - (1 - progress) * (1 - progress));
                groundFace(poses, buffers, Math.min(3.8F, effect.radius * 0.65F), fade);
                ring(poses, buffers, radius, 0.12F, 0.65F * (1 - progress), fade * 0.7F, detail);
                if (detail > 0) for (int i = 0; i < detail * 3; i++) {
                    float angle = i * Mth.TWO_PI / (detail * 3) + progress * 0.6F;
                    poses.pushPose();
                    poses.translate(Mth.cos(angle) * radius, 0, Mth.sin(angle) * radius);
                    SackGhostRenderer.draw(poses, buffers, 0x900090, time, -angle * Mth.RAD_TO_DEG, -20,
                            0.65F, fade * 0.6F, 0.5F, 0.9F);
                    poses.popPose();
                }
            }
            case VEIL -> {
                ring(poses, buffers, effect.radius, 0.04F, 0.15F, fade * 0.5F, detail);
                if (detail > 0) for (int i = 0; i < detail * 2; i++) {
                    float angle = i * Mth.TWO_PI / (detail * 2) + age * 0.015F;
                    poses.pushPose();
                    poses.translate(Mth.cos(angle) * effect.radius, -0.4, Mth.sin(angle) * effect.radius);
                    drawHand(poses, buffers, time, 0.9F, fade * 0.65F, 1.1F);
                    poses.popPose();
                }
            }
            case ECHO_DEPART -> {
                poses.translate(Mth.sin(time * 0.2F) * 0.018, 0, 0);
                SackGhostEffigyRenderer.renderEcho(effect.echo, poses, buffers, 0xB000B0, fade * 0.5F);
                if (detail > 0) {
                    poses.translate(0.06, 0, 0.03);
                    SackGhostRenderer.draw(poses, buffers, 0x700070, time, yaw, 0, 1, fade * 0.18F, 0, 0);
                }
            }
            case ECHO_RETURN -> {
                ring(poses, buffers, effect.radius * progress, 0.15F, (1 - progress) * 1.3F, fade, detail);
                SackGhostEffigyRenderer.renderEcho(effect.echo, poses, buffers, 0xF000F0, fade * (1 - progress));
                if (detail > 0) shards(poses, buffers, age, progress, fade, detail);
            }
            case WAIL -> {
                // 鬼影张口仰头；音浪中心每帧跟随施法者，不固定在施法点。
                poses.pushPose();
                poses.translate(0, 0.15, 0);
                SackGhostRenderer.draw(poses, buffers, 0xB000B0, time, yaw, -32, 1.15F, fade * 0.8F, 1, 0.3F);
                poses.popPose();
                int waves = detail == 0 ? 1 : detail + 2;
                for (int i = 0; i < waves; i++) {
                    float phase = (age / 24 + i / (float) waves) % 1;
                    ring(poses, buffers, Math.max(0.3F, phase * effect.radius), 0.5F + phase * 0.35F,
                            0.35F + phase * 0.5F, fade * (1 - phase) * 0.55F, detail);
                }
            }
            case PARASITE -> {
                float sanity = attached instanceof Player player ? SREPlayerMoodComponent.KEY.get(player).getMood() : 0.5F;
                float pressure = Mth.clamp(1 - sanity, 0, 1);
                poses.mulPose(Axis.YP.rotationDegrees(-yaw));
                poses.translate(0, 0.1 + pressure * 0.12, -0.45 + pressure * 0.2);
                SackGhostRenderer.draw(poses, buffers, 0x800080, time, 0, 20, 0.7F + pressure * 0.15F,
                        fade * (0.18F + pressure * 0.8F), 0.3F, 0.45F + pressure * 0.5F);
                if (detail > 0) ring(poses, buffers, 0.4F - pressure * 0.15F, 0.9F, 0.08F, fade * pressure * 0.5F, detail);
            }
            case SEIZE -> {
                Entity owner = world.getEntity(effect.owner);
                Vec3 delta = owner == null || attached == null ? new Vec3(0, 0, -1) : position(owner, partial).subtract(position(attached, partial));
                poses.mulPose(Axis.YP.rotation((float) Math.atan2(delta.x, delta.z)));
                poses.translate(0, 1, 0.1);
                poses.mulPose(Axis.XP.rotationDegrees(-75));
                drawHand(poses, buffers, time, Math.min(1, progress * 4), fade, 2.7F);
                // 从被抓目标伸向施法者的阴影牵引带，目标位移仍完全由服务端负责。
                if (detail > 0) {
                    poses.mulPose(Axis.XP.rotationDegrees(75));
                    float length = (float) Math.min(15, delta.length());
                    band(poses, buffers, -0.09F, 0, 0, 0.09F, 0, length, fade * 0.4F);
                }
            }
            case EFFIGY -> {
                if (effect.target >= 0) {
                    Entity target = world.getEntity(effect.target);
                    Vec3 delta = target == null ? Vec3.ZERO : position(target, partial).subtract(effect.origin);
                    poses.translate(delta.x * Math.min(1, progress * 3), Math.sin(progress * Math.PI) * 0.6, delta.z * Math.min(1, progress * 3));
                    yaw = (float) (-Math.atan2(delta.x, delta.z) * Mth.RAD_TO_DEG);
                }
                SackGhostRenderer.draw(poses, buffers, 0xA000A0, time, yaw, -15, 1, fade, 0.8F, 1);
                if (detail > 0) shards(poses, buffers, age, progress, fade, detail);
            }
            case ESCAPE -> {
                if (age < 25) ring(poses, buffers, age * 0.13F, 0.05F, 0.4F, fade * (1 - age / 25), detail);
                if (detail > 0) shards(poses, buffers, time, (age % 24) / 24, fade * 0.4F, detail);
            }
            case REALM_DEPART, REALM_RETURN -> {
                float rise = effect.kind == Kind.REALM_RETURN ? 1 - progress : progress;
                ring(poses, buffers, 0.4F + 2.3F * progress, 0.04F, 0.25F, fade * 0.65F, detail);
                poses.translate(0, rise * 0.5, 0);
                SackGhostRenderer.draw(poses, buffers, 0x900090, time, yaw, -10, 1, fade * 0.8F, 0.3F, 0.4F);
            }
        }
    }

    private static void drawHand(PoseStack poses, MultiBufferSource buffers, float time, float grip, float alpha, float scale) {
        if (hand == null) hand = new SackGhostHandModel();
        poses.pushPose();
        poses.scale(scale, -scale, scale);
        poses.mulPose(Axis.ZP.rotation(Mth.sin(time * 0.12F) * 0.05F));
        hand.render(poses, buffers, grip, alpha);
        poses.popPose();
    }

    private static void shards(PoseStack poses, MultiBufferSource buffers, float age, float progress, float fade, int detail) {
        VertexConsumer out = buffers.getBuffer(INK);
        for (int i = 0; i < detail * 6; i++) {
            float angle = i * 2.39996F + age * 0.025F;
            float radius = progress * (1.5F + i % 3 * 0.6F);
            float x = Mth.cos(angle) * radius, z = Mth.sin(angle) * radius;
            float y = (1 - progress) * (0.3F + i % 4 * 0.4F);
            quad(out, poses.last(), x - 0.07F, y, z, x + 0.07F, y, z,
                    x + 0.15F, y + 0.65F, z + 0.1F, x, y + 0.8F, z + 0.1F, 0x18101C, fade * (1 - progress));
        }
    }

    private static void ring(PoseStack poses, MultiBufferSource buffers, float radius, float y, float height, float alpha, int detail) {
        VertexConsumer out = buffers.getBuffer(INK);
        int segments = detail == 2 ? 48 : detail == 1 ? 32 : 20;
        for (int i = 0; i < segments; i++) {
            float a = i * Mth.TWO_PI / segments, b = (i + 1) * Mth.TWO_PI / segments;
            float x = Mth.cos(a) * radius, z = Mth.sin(a) * radius;
            float xx = Mth.cos(b) * radius, zz = Mth.sin(b) * radius;
            // 竖直风壁与地面环带均为双面四边形，光影下仍走原生实体管线。
            quad(out, poses.last(), x, y, z, xx, y, zz, xx, y + height, zz, x, y + height, z, 0x211425, alpha);
            float inner = Math.max(0, radius - 0.15F);
            quad(out, poses.last(), x, y, z, xx, y, zz, Mth.cos(b) * inner, y, Mth.sin(b) * inner,
                    Mth.cos(a) * inner, y, Mth.sin(a) * inner, 0x6B263C, alpha * 0.7F);
        }
    }

    private static void groundFace(PoseStack poses, MultiBufferSource buffers, float size, float alpha) {
        VertexConsumer out = buffers.getBuffer(INK);
        // 巨大面孔由实际轮廓、斜眼与锯齿口构成，不是圆形烟雾占位。
        for (int i = 0; i < 32; i++) {
            float a = i * Mth.TWO_PI / 32, b = (i + 1) * Mth.TWO_PI / 32;
            quad(out, poses.last(), 0, 0.035F, 0, Mth.cos(a) * size, 0.035F, Mth.sin(a) * size * 0.8F,
                    Mth.cos(b) * size, 0.035F, Mth.sin(b) * size * 0.8F, 0, 0.035F, 0, 0x09070E, alpha * 0.9F);
        }
        for (int side : new int[]{-1, 1}) {
            float x = side * size * 0.4F;
            quad(out, poses.last(), x - size * 0.22F, 0.055F, -size * 0.2F, x + size * 0.22F, 0.055F, -size * 0.3F,
                    x + size * 0.17F, 0.055F, -size * 0.05F, x - size * 0.17F, 0.055F, -size * 0.1F, 0xD41F3D, alpha);
        }
        for (int i = 0; i < 9; i++) {
            float x = (i - 4) * size * 0.13F;
            float z = size * (0.28F + Math.abs(i - 4) * 0.025F);
            quad(out, poses.last(), x, 0.06F, z, x + size * 0.12F, 0.06F, z,
                    x + size * 0.07F, 0.06F, z + size * 0.16F, x, 0.06F, z + size * 0.23F, 0x8E2941, alpha * 0.85F);
        }
    }

    private static void band(PoseStack poses, MultiBufferSource buffers, float x, float y, float z, float xx, float yy, float zz, float alpha) {
        quad(buffers.getBuffer(INK), poses.last(), x, y, z, xx, y, z, xx, yy, zz, x, yy, zz, 0x271329, alpha);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(out, pose, x1, y1, z1, rgb, a); vertex(out, pose, x2, y2, z2, rgb, a);
        vertex(out, pose, x3, y3, z3, rgb, a); vertex(out, pose, x4, y4, z4, rgb, a);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int rgb, int alpha) {
        out.addVertex(pose, x, y, z).setColor((alpha << 24) | rgb).setUv(63.0F / 64, 63.0F / 64)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }

    /** 私有受害者包驱动；无视隐藏界面设置，不修改相机、不挡住技能输入。 */
    public static void renderVeil(GuiGraphics gui, int remaining, int total, float partial) {
        if (remaining <= 0) return;
        int width = gui.guiWidth(), height = gui.guiHeight();
        float age = total - remaining + SackGhostClient.visualPartial(partial);
        float close = Mth.clamp(age / 12, 0, 1);
        float release = Mth.clamp(remaining / 5.0F, 0, 1);
        int color = ((int) (245 * release) << 24) | 0x030207;
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 500);
        for (int side : new int[]{-1, 1}) {
            gui.pose().pushPose();
            gui.pose().translate(side < 0 ? 0 : width, height * 0.5, 0);
            gui.pose().scale(side < 0 ? 1 : -1, 1, 1);
            float reach = width * (0.08F + close * 0.46F);
            int palm = (int) (reach * 0.67F);
            gui.fill(0, -height, palm, height, color);
            // 五根长指依次收紧；弯曲指尖和拇指保持可辨认的鬼手外形。
            for (int finger = 0; finger < 5; finger++) {
                gui.pose().pushPose();
                float spread = (finger - 2) * (height * 0.18F);
                gui.pose().translate(palm - 4, spread, 0);
                gui.pose().mulPose(Axis.ZP.rotationDegrees((finger - 2) * (9 - close * 4)));
                int length = (int) (reach * (finger == 4 ? 0.42F : 0.61F - Math.abs(finger - 2) * 0.04F));
                int thick = Math.max(5, (int) (height * 0.1F));
                gui.fill(-5, -thick, length, thick, color);
                gui.pose().translate(length - 3, 0, 0);
                gui.pose().mulPose(Axis.ZP.rotationDegrees(finger < 2 ? 20 : -20));
                gui.fill(-2, -thick + 2, thick + 5, thick - 2, color);
                gui.pose().popPose();
            }
            gui.pose().popPose();
        }
        gui.pose().popPose();
    }

    public static void clear() { EFFECTS.clear(); world = null; }
}

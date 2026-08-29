package org.agmas.noellesroles.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.packet.FakeSteveApparitionObservationC2SPacket;
import org.agmas.noellesroles.packet.FakeSteveApparitionS2CPacket;
import org.agmas.noellesroles.packet.FakeSteveControlS2CPacket;
import org.agmas.noellesroles.game.fake_steve.FakeSteveMotionPolicy;
import org.agmas.noellesroles.game.fake_steve.FakeSteveApparitionLifecycle;

import java.io.InputStream;
import java.util.UUID;

/** Target-local rendering and gaze acknowledgement for the faceless Steve. */
public final class FakeSteveClient {
    private static final ResourceLocation VANILLA_STEVE = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/steve.png");
    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-00000000f57e");
    private static Apparition apparition;
    private static MotionLease motion;
    private static ResourceLocation facelessTexture;
    private static boolean registered;

    private FakeSteveClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(FakeSteveApparitionS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> onPacket(payload)));
        ClientPlayNetworking.registerGlobalReceiver(FakeSteveControlS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> onControl(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearRuntime());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearRuntime());
        ClientTickEvents.END_CLIENT_TICK.register(FakeSteveClient::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FakeSteveClient::render);
        AllowOtherCameraType.EVENT.register((original, player) -> {
            if (!player.isSpectator() && WorldModifierComponent.KEY.get(player.level())
                    .isModifier(player, NRModifiers.FAKE_STEVE_REPLACED)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    private static void clearRuntime() {
        apparition = null;
        motion = null;
    }

    private static void onControl(FakeSteveControlS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!packet.active() || client.level == null) {
            motion = null;
            return;
        }
        if (motion != null && packet.sequence() <= motion.sequence) {
            return;
        }
        motion = new MotionLease(packet, client.level.getGameTime() + packet.durationTicks());
    }

    public static void applyAiInput(Input input) {
        Minecraft client = Minecraft.getInstance();
        if (motion == null || client.level == null || client.player == null
                || client.level.getGameTime() > motion.expiresAtTick) {
            motion = null;
            return;
        }
        input.forwardImpulse = motion.forward;
        input.leftImpulse = motion.strafe;
        input.up = motion.forward > 0.0F;
        input.down = motion.forward < 0.0F;
        input.left = motion.strafe > 0.0F;
        input.right = motion.strafe < 0.0F;
        input.jumping = motion.jump;
        input.shiftKeyDown = motion.crouch;
        client.player.setSprinting(motion.sprint);
        client.player.setYRot(FakeSteveMotionPolicy.turnToward(
                client.player.getYRot(), motion.targetYaw));
        client.player.setYHeadRot(client.player.getYRot());
        client.player.setXRot(FakeSteveMotionPolicy.turnToward(
                client.player.getXRot(), motion.targetPitch));
    }

    private static void onPacket(FakeSteveApparitionS2CPacket packet) {
        if (packet.remove()) {
            if (apparition != null && apparition.id.equals(packet.apparitionId())) {
                apparition = null;
            }
            return;
        }
        apparition = new Apparition(packet.apparitionId(), new Vec3(packet.x(), packet.y(), packet.z()));
    }

    private static void tick(Minecraft client) {
        if (apparition == null || client.level == null || client.player == null) {
            return;
        }
        FakeSteveApparitionLifecycle.Stage before = apparition.lifecycle.stage();
        FakeSteveApparitionLifecycle.Stage after = apparition.lifecycle.tick(
                isVisible(client, apparition.position), 1);
        if (before != after && after == FakeSteveApparitionLifecycle.Stage.OBSERVED) {
            ClientPlayNetworking.send(new FakeSteveApparitionObservationC2SPacket(
                    apparition.id, FakeSteveApparitionObservationC2SPacket.Stage.OBSERVED));
        } else if (after == FakeSteveApparitionLifecycle.Stage.LOOKED_AWAY) {
            UUID id = apparition.id;
            apparition = null;
            ClientPlayNetworking.send(new FakeSteveApparitionObservationC2SPacket(
                    id, FakeSteveApparitionObservationC2SPacket.Stage.LOOKED_AWAY));
        } else if (after == FakeSteveApparitionLifecycle.Stage.TIMED_OUT) {
            apparition = null;
        }
    }

    private static boolean isVisible(Minecraft client, Vec3 feet) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        Vec3 target = feet.add(0.0D, 1.55D, 0.0D);
        Vec3 direction = target.subtract(camera);
        if (direction.lengthSqr() < 0.01D || direction.lengthSqr() > 32.0D * 32.0D) {
            return false;
        }
        org.joml.Vector3f look = client.gameRenderer.getMainCamera().getLookVector();
        Vec3 lookVector = new Vec3(look.x, look.y, look.z);
        if (lookVector.dot(direction.normalize()) < Math.cos(Math.toRadians(30.0D))) {
            return false;
        }
        HitResult hit = client.level.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(camera) + 0.25D >= target.distanceToSqr(camera);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (apparition == null || client.level == null || client.player == null || context.consumers() == null) {
            return;
        }
        ensureTexture(client);
        if (facelessTexture == null) {
            return;
        }

        if (apparition.entity == null) {
            apparition.entity = new FacelessRemotePlayer(client, facelessTexture);
            apparition.entity.setPos(apparition.position.x, apparition.position.y, apparition.position.z);
            double initialX = client.player.getX() - apparition.position.x;
            double initialZ = client.player.getZ() - apparition.position.z;
            apparition.bodyYaw = (float) (Math.toDegrees(Math.atan2(initialZ, initialX)) - 90.0D);
            apparition.headYaw = apparition.bodyYaw;
            apparition.entity.setCustomName(Component.literal("unknown"));
            apparition.entity.setCustomNameVisible(true);
        }
        FacelessRemotePlayer fake = apparition.entity;
        double dx = client.player.getX() - apparition.position.x;
        double dz = client.player.getZ() - apparition.position.z;
        float desiredHeadYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float relativeHead = Mth.clamp(Mth.wrapDegrees(desiredHeadYaw - apparition.bodyYaw),
                -60.0F, 60.0F);
        apparition.headYaw = FakeSteveMotionPolicy.turnToward(apparition.headYaw,
                apparition.bodyYaw + relativeHead);
        double dy = client.player.getEyeY() - (apparition.position.y + 1.62D);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        apparition.headPitch = FakeSteveMotionPolicy.turnToward(apparition.headPitch,
                Mth.clamp(desiredPitch, -45.0F, 45.0F));
        fake.setYRot(apparition.bodyYaw);
        fake.setYBodyRot(apparition.bodyYaw);
        fake.setYHeadRot(apparition.headYaw);
        fake.setXRot(apparition.headPitch);

        Vec3 camera = context.camera().getPosition();
        int light = LevelRenderer.getLightColor(client.level, BlockPos.containing(apparition.position));
        context.matrixStack().pushPose();
        client.getEntityRenderDispatcher().render(fake,
                apparition.position.x - camera.x,
                apparition.position.y - camera.y,
                apparition.position.z - camera.z,
                apparition.bodyYaw, context.tickCounter().getGameTimeDeltaPartialTick(false),
                context.matrixStack(), context.consumers(), light);
        context.matrixStack().popPose();
    }

    private static void ensureTexture(Minecraft client) {
        if (facelessTexture != null) {
            return;
        }
        try (InputStream input = client.getResourceManager().getResourceOrThrow(VANILLA_STEVE).open();
                NativeImage image = NativeImage.read(input)) {
            int skinColor = image.getPixelRGBA(9, 8);
            image.fillRect(8, 8, 8, 8, skinColor);
            image.fillRect(40, 8, 8, 8, 0x00000000);
            facelessTexture = client.getTextureManager().register("fake_steve_faceless",
                    new DynamicTexture(image.mappedCopy(pixel -> pixel)));
        } catch (Exception exception) {
            Noellesroles.LOGGER.error("Unable to build Fake Steve faceless texture", exception);
        }
    }

    private static final class FacelessRemotePlayer extends RemotePlayer {
        private final PlayerSkin skin;

        private FacelessRemotePlayer(Minecraft client, ResourceLocation texture) {
            super(client.level, new GameProfile(PROFILE_ID, "unknown"));
            this.skin = new PlayerSkin(texture, null, null, null, PlayerSkin.Model.WIDE, false);
        }

        @Override
        public PlayerSkin getSkin() {
            return skin;
        }
    }

    private static final class Apparition {
        private final UUID id;
        private final Vec3 position;
        private final FakeSteveApparitionLifecycle lifecycle = new FakeSteveApparitionLifecycle();
        private FacelessRemotePlayer entity;
        private float bodyYaw;
        private float headYaw;
        private float headPitch;

        private Apparition(UUID id, Vec3 position) {
            this.id = id;
            this.position = position;
        }
    }

    private static final class MotionLease {
        private final long sequence;
        private final long expiresAtTick;
        private final float forward;
        private final float strafe;
        private final boolean jump;
        private final boolean sprint;
        private final boolean crouch;
        private final float targetYaw;
        private final float targetPitch;

        private MotionLease(FakeSteveControlS2CPacket packet, long expiresAtTick) {
            this.sequence = packet.sequence();
            this.expiresAtTick = expiresAtTick;
            this.forward = packet.forward();
            this.strafe = packet.strafe();
            this.jump = packet.jump();
            this.sprint = packet.sprint();
            this.crouch = packet.crouch();
            this.targetYaw = packet.targetYaw();
            this.targetPitch = packet.targetPitch();
        }
    }
}

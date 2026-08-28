package org.agmas.noellesroles.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.packet.FakeSteveApparitionLostC2SPacket;
import org.agmas.noellesroles.packet.FakeSteveApparitionS2CPacket;

import java.io.InputStream;
import java.util.UUID;

/** Target-local rendering and gaze acknowledgement for the faceless Steve. */
public final class FakeSteveClient {
    private static final ResourceLocation VANILLA_STEVE = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/steve.png");
    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-00000000f57e");
    private static Apparition apparition;
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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> apparition = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> apparition = null);
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
        boolean visible = isVisible(client, apparition.position);
        if (!apparition.seen) {
            apparition.visibleTicks = visible ? apparition.visibleTicks + 1 : 0;
            if (apparition.visibleTicks >= 5) {
                apparition.seen = true;
            }
            return;
        }
        apparition.lostTicks = visible ? 0 : apparition.lostTicks + 1;
        if (apparition.lostTicks >= 3) {
            UUID id = apparition.id;
            apparition = null;
            ClientPlayNetworking.send(new FakeSteveApparitionLostC2SPacket(id));
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

        FacelessRemotePlayer fake = new FacelessRemotePlayer(client, facelessTexture);
        fake.setPos(apparition.position.x, apparition.position.y, apparition.position.z);
        double dx = client.player.getX() - apparition.position.x;
        double dz = client.player.getZ() - apparition.position.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        fake.setYRot(yaw);
        fake.setYBodyRot(yaw);
        fake.setYHeadRot(yaw);
        fake.setCustomName(Component.literal("unknown"));
        fake.setCustomNameVisible(true);

        Vec3 camera = context.camera().getPosition();
        int light = LevelRenderer.getLightColor(client.level, BlockPos.containing(apparition.position));
        context.matrixStack().pushPose();
        client.getEntityRenderDispatcher().render(fake,
                apparition.position.x - camera.x,
                apparition.position.y - camera.y,
                apparition.position.z - camera.z,
                yaw, context.tickCounter().getGameTimeDeltaPartialTick(false),
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
        private int visibleTicks;
        private int lostTicks;
        private boolean seen;

        private Apparition(UUID id, Vec3 position) {
            this.id = id;
            this.position = position;
        }
    }
}

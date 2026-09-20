package org.agmas.noellesroles.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;
import org.agmas.noellesroles.packet.PurpleMonsterEventS2CPacket;
import org.agmas.noellesroles.client.screen.PurpleMonsterPlayerSelectScreen;
import org.agmas.noellesroles.client.screen.PurpleMonsterQuestionScreen;
import io.wifi.starrailexpress.index.TMMEntities;

import java.util.List;
import java.util.UUID;

/** Client-only hallucination renderer and the two event screens. */
public final class PurpleMonsterClient {
    private static EventState state;
    private static boolean registered;

    private PurpleMonsterClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(PurpleMonsterEventS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> receive(packet)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        ClientTickEvents.END_CLIENT_TICK.register(PurpleMonsterClient::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PurpleMonsterClient::render);
    }

    private static void receive(PurpleMonsterEventS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.CLOSE) {
            clear(client);
            return;
        }
        if (state == null || !state.eventId.equals(packet.eventId())) state = new EventState(packet.eventId());
        state.stage = packet.stage();
        state.position = new Vec3(packet.x(), packet.y(), packet.z());
        state.skinPlayer = packet.skinPlayer();
        state.candidates = packet.candidates();
        state.observedTicks = 0;
        if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.QUESTION) {
            lockCamera(client);
            client.setScreen(new PurpleMonsterQuestionScreen(state.eventId));
        } else if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.SELECT) {
            lockCamera(client);
            client.setScreen(new PurpleMonsterPlayerSelectScreen(state.eventId, state.candidates));
        } else if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE) {
            if (client.screen != null) client.setScreen(null);
            lockCamera(client);
        }
    }

    private static void tick(Minecraft client) {
        if (state == null || client.level == null || client.player == null) return;
        if (state.stage == PurpleMonsterEventS2CPacket.Stage.DISGUISE) {
            if (isVisible(client, state.position)) {
                state.observedTicks++;
                if (state.observedTicks == 6 * 20) {
                    ClientPlayNetworking.send(new PurpleMonsterEventC2SPacket(state.eventId,
                            PurpleMonsterEventC2SPacket.Action.OBSERVED, null));
                }
            } else state.observedTicks = 0;
        } else if (state.stage == PurpleMonsterEventS2CPacket.Stage.QUESTION
                || state.stage == PurpleMonsterEventS2CPacket.Stage.SELECT
                || state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE) {
            lockCamera(client);
        }
    }

    private static void lockCamera(Minecraft client) {
        if (client.level == null) return;
        Entity entity = fakeEntity(client);
        if (entity != null && client.getCameraEntity() != entity) client.setCameraEntity(entity);
    }

    private static boolean isVisible(Minecraft client, Vec3 feet) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        Vec3 target = feet.add(0, 1.55, 0);
        Vec3 direction = target.subtract(camera);
        if (direction.lengthSqr() < 0.01 || direction.lengthSqr() > 32 * 32) return false;
        var look = client.gameRenderer.getMainCamera().getLookVector();
        if (new Vec3(look.x, look.y, look.z).dot(direction.normalize()) < Math.cos(Math.toRadians(30))) return false;
        HitResult hit = client.level.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (state == null || client.level == null || context.consumers() == null) return;
        Entity fake = fakeEntity(client);
        if (fake == null) return;
        Vec3 camera = context.camera().getPosition();
        int light = LevelRenderer.getLightColor(client.level, BlockPos.containing(state.position));
        context.matrixStack().pushPose();
        client.getEntityRenderDispatcher().render(fake, state.position.x - camera.x,
                state.position.y - camera.y, state.position.z - camera.z, fake.getYRot(),
                context.tickCounter().getGameTimeDeltaPartialTick(false), context.matrixStack(),
                context.consumers(), light);
        context.matrixStack().popPose();
    }

    private static Entity fakeEntity(Minecraft client) {
        if (client.level == null || state == null) return null;
        if (state.stage == PurpleMonsterEventS2CPacket.Stage.DISGUISE) {
            if (state.playerEntity == null) {
                net.minecraft.world.entity.player.Player close = state.skinPlayer == null ? null
                        : client.level.getPlayerByUUID(state.skinPlayer);
                if (close == null) return null;
                PlayerSkin skin = close instanceof AbstractClientPlayer player
                        ? player.getSkin() : DefaultPlayerSkin.get(close.getUUID());
                state.playerEntity = new SkinRemotePlayer(client, close.getGameProfile(), skin);
            }
            state.playerEntity.setPos(state.position.x, state.position.y, state.position.z);
            state.playerEntity.setCustomName(Component.literal("unknown"));
            state.playerEntity.setCustomNameVisible(false);
            return state.playerEntity;
        }
        EntityType<?> type = state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE
                ? TMMEntities.PURPLE_MONSTER_SECOND : TMMEntities.PURPLE_MONSTER;
        if (state.monsterEntity != null && state.monsterEntity.getType() != type) state.monsterEntity = null;
        if (state.monsterEntity == null) {
            state.monsterEntity = type.create(client.level);
            if (state.monsterEntity == null) return null;
            state.monsterEntity.setNoGravity(true);
            state.monsterEntity.setCustomName(Component.literal("purple_monster"));
        }
        state.monsterEntity.setPos(state.position.x, state.position.y, state.position.z);
        return state.monsterEntity;
    }

    private static void clear(Minecraft client) {
        if (client.player != null && client.getCameraEntity() != client.player) client.setCameraEntity(client.player);
        if (client.screen instanceof PurpleMonsterQuestionScreen || client.screen instanceof PurpleMonsterPlayerSelectScreen)
            client.setScreen(null);
        if (state != null) {
            state.playerEntity = null;
            state.monsterEntity = null;
        }
        state = null;
    }

    private static final class SkinRemotePlayer extends RemotePlayer {
        private final PlayerSkin skin;

        private SkinRemotePlayer(Minecraft client, GameProfile profile, PlayerSkin skin) {
            super(client.level, profile);
            this.skin = skin;
        }

        @Override
        public PlayerSkin getSkin() { return skin; }
    }

    private static final class EventState {
        private final UUID eventId;
        private PurpleMonsterEventS2CPacket.Stage stage = PurpleMonsterEventS2CPacket.Stage.DISGUISE;
        private Vec3 position = Vec3.ZERO;
        private UUID skinPlayer;
        private List<UUID> candidates = List.of();
        private int observedTicks;
        private SkinRemotePlayer playerEntity;
        private Entity monsterEntity;

        private EventState(UUID eventId) { this.eventId = eventId; }
    }
}

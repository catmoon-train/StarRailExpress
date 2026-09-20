package org.agmas.noellesroles.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;
import org.agmas.noellesroles.packet.PurpleMonsterEventS2CPacket;
import org.agmas.noellesroles.packet.PurpleMonsterProgressS2CPacket;
import org.agmas.noellesroles.client.screen.PurpleMonsterPlayerSelectScreen;
import org.agmas.noellesroles.client.screen.PurpleMonsterQuestionScreen;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.role.bouns.BounsRoles;

import java.util.List;
import java.util.UUID;

/** Client-only hallucination renderer and the two event screens. */
public final class PurpleMonsterClient {
    private static EventState state;
    private static int assimilated;
    private static int assimilationGoal = 1;
    private static boolean registered;

    private PurpleMonsterClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(PurpleMonsterEventS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PurpleMonsterProgressS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    assimilated = Math.max(0, packet.assimilated());
                    assimilationGoal = Math.max(1, packet.goal());
                }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
        ClientTickEvents.END_CLIENT_TICK.register(PurpleMonsterClient::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PurpleMonsterClient::render);
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> renderHud(graphics));
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
        playStageSound(client, packet.stage());
        if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.QUESTION) {
            lockCamera(client);
            client.setScreen(new PurpleMonsterQuestionScreen(state.eventId));
        } else if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.SELECT) {
            lockCamera(client);
            client.setScreen(new PurpleMonsterPlayerSelectScreen(state.eventId, state.candidates));
        } else if (packet.stage() == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_TRANSFORM
                || packet.stage() == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE
                || packet.stage() == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_EFFECT) {
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
                || state.stage == PurpleMonsterEventS2CPacket.Stage.REVEAL
                || state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_TRANSFORM
                || state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE) {
            lockCamera(client);
        } else if (state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_EFFECT) {
            lockCamera(client);
        }
    }

    private static void playStageSound(Minecraft client, PurpleMonsterEventS2CPacket.Stage stage) {
        if (client.player == null) return;
        switch (stage) {
            case REVEAL -> client.player.playSound(SoundEvents.ENDERMAN_AMBIENT, 0.45F, 0.75F);
            case ASSIMILATE_TRANSFORM -> client.player.playSound(SoundEvents.ENDERMAN_AMBIENT, 0.55F, 0.55F);
            case ASSIMILATE -> client.player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.65F, 0.8F);
            case ASSIMILATE_EFFECT -> client.player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.55F);
            default -> { }
        }
    }

    private static void lockCamera(Minecraft client) {
        if (client.level == null || client.player == null) return;
        Entity entity = fakeEntity(client);
        if (entity == null) return;

        // Do not use the hallucination itself as the camera entity. Rendering a
        // camera entity at its own position puts the camera inside the model.
        // Keep the camera at the player and lock the player's view toward it.
        if (client.getCameraEntity() != client.player) client.setCameraEntity(client.player);
        lookAt(client.player, entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0));
    }

    private static void lookAt(net.minecraft.client.player.LocalPlayer player, Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-5D && Math.abs(delta.y) < 1.0E-5D) return;
        float yaw = (float) (Math.atan2(-delta.x, delta.z) * (180.0D / Math.PI));
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * (180.0D / Math.PI)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
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
                PlayerInfo info = client.getConnection() == null ? null
                        : client.getConnection().getPlayerInfo(state.skinPlayer);
                PlayerSkin skin = info != null && info.getSkin() != null
                        ? info.getSkin()
                        : close instanceof AbstractClientPlayer player
                        ? player.getSkin() : DefaultPlayerSkin.get(close.getUUID());
                state.playerEntity = new SkinRemotePlayer(client, close.getGameProfile(), state.skinPlayer, skin);
            }
            stabilizeEntity(state.playerEntity, state.position);
            state.playerEntity.setCustomName(Component.literal("unknown"));
            state.playerEntity.setCustomNameVisible(false);
            return state.playerEntity;
        }
        EntityType<?> type = state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE
                || state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_EFFECT
                ? TMMEntities.PURPLE_MONSTER_SECOND : TMMEntities.PURPLE_MONSTER;
        if (state.monsterEntity != null && state.monsterEntity.getType() != type) state.monsterEntity = null;
        if (state.monsterEntity == null) {
            state.monsterEntity = type.create(client.level);
            if (state.monsterEntity == null) return null;
            state.monsterEntity.setNoGravity(true);
            state.monsterEntity.setCustomName(Component.literal("purple_monster"));
        }
        stabilizeEntity(state.monsterEntity, state.position);
        return state.monsterEntity;
    }

    /**
     * These client-only entities are not in the level tick list. Keep their previous and
     * current transforms identical, otherwise the camera interpolates from stale values
     * every frame and appears to shake during the transformation sequence.
     */
    private static void stabilizeEntity(Entity entity, Vec3 position) {
        entity.setPos(position.x, position.y, position.z);
        entity.xOld = position.x;
        entity.yOld = position.y;
        entity.zOld = position.z;
        entity.xo = position.x;
        entity.yo = position.y;
        entity.zo = position.z;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.yRotO = entity.getYRot();
        entity.xRotO = entity.getXRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = living.getYRot();
            living.yHeadRot = living.yHeadRotO = living.getYRot();
        }
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

    private static void reset(Minecraft client) {
        clear(client);
        assimilated = 0;
        assimilationGoal = 1;
    }

    private static void renderHud(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (state != null && state.stage == PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_EFFECT) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x552D0747);
        }
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning()) return;
        var role = SREClient.getCachedPlayerRole();
        if (role == null || !role.identifier().equals(BounsRoles.PURPLE_MONSTER.identifier())) return;
        Component text = Component.translatable("hud.noellesroles.purple_monster.assimilation_progress",
                assimilated, assimilationGoal);
        graphics.drawString(client.font, text, 10, graphics.guiHeight() - client.font.lineHeight - 12,
                0xFFFFFFFF);
    }

    private static final class SkinRemotePlayer extends RemotePlayer {
        private final UUID skinPlayer;
        private final PlayerSkin skin;

        private SkinRemotePlayer(Minecraft client, GameProfile profile, UUID skinPlayer, PlayerSkin skin) {
            super(client.level, profile);
            this.skinPlayer = skinPlayer;
            this.skin = skin;
        }

        /** PlayerRenderer obtains the texture/model from PlayerInfo, so point it at the real close player. */
        @Override
        public PlayerInfo getPlayerInfo() {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            return connection == null ? null : connection.getPlayerInfo(this.skinPlayer);
        }

        @Override
        public PlayerSkin getSkin() {
            PlayerInfo info = getPlayerInfo();
            return info != null && info.getSkin() != null ? info.getSkin() : skin;
        }
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

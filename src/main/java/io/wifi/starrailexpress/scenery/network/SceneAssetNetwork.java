package io.wifi.starrailexpress.scenery.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import io.wifi.starrailexpress.scenery.SceneRegistryFingerprint;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import io.wifi.starrailexpress.scenery.server.SceneLibrary;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class SceneAssetNetwork {
    private SceneAssetNetwork() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(ManifestS2C.ID, ManifestS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(ChunkS2C.ID, ChunkS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorS2C.ID, OpenEditorS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSceneManagerS2C.ID, OpenSceneManagerS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestChunkC2S.ID, RequestChunkC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ReadyC2S.ID, ReadyC2S.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestChunkC2S.ID, (payload, context) ->
                context.server().execute(() -> sendRequestedChunk(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ReadyC2S.ID, (payload, context) -> {
            if (SceneAssetCodec.isValidHash(payload.hash())) {
                SRE.LOGGER.debug("Player {} loaded scene asset {}", context.player().getScoreboardName(),
                        payload.hash());
            }
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ManifestS2C.ID, (payload, context) ->
                context.client().execute(() -> SceneAssetClient.handleManifest(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ChunkS2C.ID, (payload, context) ->
                context.client().execute(() -> SceneAssetClient.handleChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorS2C.ID, (payload, context) ->
                context.client().execute(SceneAssetClient::openEditor));
        ClientPlayNetworking.registerGlobalReceiver(OpenSceneManagerS2C.ID, (payload, context) ->
                context.client().execute(() -> SceneAssetClient.openSceneManager(
                        payload.sceneIds(), payload.currentSceneId())));
    }

    public static void sendManifestToAll(ServerLevel level) {
        PlayerLookup.world(level).forEach(player -> sendManifest(player, level));
    }

    public static void sendManifest(ServerPlayer player, ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        SceneAssetServer.Status status = SceneAssetServer.status(level);
        SceneAssetServer.ensureTransient(level);
        SceneAssetServer.NetworkAsset networkAsset = areas.isSceneAreaConfigured()
                ? SceneAssetServer.networkAsset(level)
                : new SceneAssetServer.NetworkAsset("", 0L, false);
        AABB area = areas.getSceneArea();
        String remoteUrl = networkAsset.transientAsset()
                ? ""
                : resolveRemoteUrl(areas.getSceneAssetRemoteUrl(), networkAsset.hash(), areas.mapName);
        ServerPlayNetworking.send(player, new ManifestS2C(
                networkAsset.hash(),
                SceneRegistryFingerprint.compute(level.registryAccess()),
                networkAsset.size(),
                area,
                areas.getSceneScroll().name(),
                areas.mapName == null ? "" : areas.mapName,
                status.dirty(),
                networkAsset.transientAsset(),
                remoteUrl,
                areas.isSceneAssetTrusted() && !networkAsset.transientAsset()));
    }

    public static void openEditor(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenEditorS2C());
        if (player.level() instanceof ServerLevel level) {
            sendManifest(player, level);
        }
    }

    public static void openSceneManager(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
            ServerPlayNetworking.send(player,
                    new OpenSceneManagerS2C(SceneLibrary.list(level), areas.getSceneId()));
        }
    }

    private static void sendRequestedChunk(ServerPlayer player, RequestChunkC2S request) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SceneAssetServer.NetworkAsset networkAsset = SceneAssetServer.networkAsset(level);
        if (!request.hash().equals(networkAsset.hash()) || !SceneAssetCodec.isValidHash(request.hash())) {
            return;
        }
        try {
            byte[] transientBytes = SceneAssetServer.transientBytes(level, request.hash());
            Path path = SceneAssetServer.assetPath(level, request.hash());
            long total = transientBytes != null ? transientBytes.length : Files.size(path);
            if (request.offset() < 0L || request.offset() > total
                    || request.offset() % SceneAssetServer.CHUNK_BYTES != 0L) {
                return;
            }
            int length = (int) Math.min(SceneAssetServer.CHUNK_BYTES, total - request.offset());
            byte[] bytes = new byte[length];
            if (transientBytes != null) {
                System.arraycopy(transientBytes, (int) request.offset(), bytes, 0, length);
            } else {
                try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                    file.seek(request.offset());
                    file.readFully(bytes);
                }
            }
            CRC32 crc = new CRC32();
            crc.update(bytes);
            ServerPlayNetworking.send(player,
                    new ChunkS2C(request.hash(), request.offset(), total, crc.getValue(), bytes));
        } catch (IOException e) {
            SRE.LOGGER.warn("Failed to send scene asset chunk {} at {}", request.hash(), request.offset(), e);
        }
    }

    public record ManifestS2C(String hash, String registryFingerprint, long size, AABB sourceArea,
            String scrollAxis, String mapName, boolean stale, boolean transientAsset, String remoteUrl,
            boolean trustedFastPath) implements CustomPacketPayload {
        public static final Type<ManifestS2C> ID = SceneAssetNetwork.type("scene_manifest");
        public static final StreamCodec<FriendlyByteBuf, ManifestS2C> CODEC =
                StreamCodec.ofMember(ManifestS2C::encode, ManifestS2C::decode);

        private void encode(FriendlyByteBuf buf) {
            buf.writeUtf(hash);
            buf.writeUtf(registryFingerprint);
            buf.writeLong(size);
            writeBox(buf, sourceArea);
            buf.writeUtf(scrollAxis);
            buf.writeUtf(mapName);
            buf.writeBoolean(stale);
            buf.writeBoolean(transientAsset);
            buf.writeUtf(remoteUrl);
            buf.writeBoolean(trustedFastPath);
        }

        private static ManifestS2C decode(FriendlyByteBuf buf) {
            return new ManifestS2C(buf.readUtf(), buf.readUtf(), buf.readLong(), readBox(buf),
                    buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(4096),
                    buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RequestChunkC2S(String hash, long offset) implements CustomPacketPayload {
        public static final Type<RequestChunkC2S> ID = SceneAssetNetwork.type("scene_request_chunk");
        public static final StreamCodec<FriendlyByteBuf, RequestChunkC2S> CODEC =
                StreamCodec.ofMember(RequestChunkC2S::encode, RequestChunkC2S::decode);

        private void encode(FriendlyByteBuf buf) {
            buf.writeUtf(hash);
            buf.writeLong(offset);
        }

        private static RequestChunkC2S decode(FriendlyByteBuf buf) {
            return new RequestChunkC2S(buf.readUtf(), buf.readLong());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ChunkS2C(String hash, long offset, long totalSize, long crc32, byte[] data)
            implements CustomPacketPayload {
        public static final Type<ChunkS2C> ID = SceneAssetNetwork.type("scene_chunk");
        public static final StreamCodec<FriendlyByteBuf, ChunkS2C> CODEC =
                StreamCodec.ofMember(ChunkS2C::encode, ChunkS2C::decode);

        private void encode(FriendlyByteBuf buf) {
            buf.writeUtf(hash);
            buf.writeLong(offset);
            buf.writeLong(totalSize);
            buf.writeLong(crc32);
            buf.writeByteArray(data);
        }

        private static ChunkS2C decode(FriendlyByteBuf buf) {
            return new ChunkS2C(buf.readUtf(), buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readByteArray(SceneAssetServer.CHUNK_BYTES));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ReadyC2S(String hash) implements CustomPacketPayload {
        public static final Type<ReadyC2S> ID = SceneAssetNetwork.type("scene_ready");
        public static final StreamCodec<FriendlyByteBuf, ReadyC2S> CODEC =
                StreamCodec.ofMember(ReadyC2S::encode, ReadyC2S::decode);

        private void encode(FriendlyByteBuf buf) {
            buf.writeUtf(hash);
        }

        private static ReadyC2S decode(FriendlyByteBuf buf) {
            return new ReadyC2S(buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record OpenEditorS2C() implements CustomPacketPayload {
        public static final Type<OpenEditorS2C> ID = SceneAssetNetwork.type("scene_open_editor");
        public static final StreamCodec<FriendlyByteBuf, OpenEditorS2C> CODEC =
                StreamCodec.of((buf, payload) -> {
                }, buf -> new OpenEditorS2C());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record OpenSceneManagerS2C(List<String> sceneIds, String currentSceneId)
            implements CustomPacketPayload {
        public static final Type<OpenSceneManagerS2C> ID = SceneAssetNetwork.type("scene_open_manager");
        public static final StreamCodec<FriendlyByteBuf, OpenSceneManagerS2C> CODEC =
                StreamCodec.ofMember(OpenSceneManagerS2C::encode, OpenSceneManagerS2C::decode);

        public OpenSceneManagerS2C {
            sceneIds = List.copyOf(sceneIds);
            currentSceneId = currentSceneId == null ? "" : currentSceneId;
        }

        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(sceneIds.size());
            for (String sceneId : sceneIds) {
                buf.writeUtf(sceneId, 128);
            }
            buf.writeUtf(currentSceneId, 128);
        }

        private static OpenSceneManagerS2C decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > 4096) {
                throw new IllegalArgumentException("Invalid scene library size: " + size);
            }
            List<String> sceneIds = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                sceneIds.add(buf.readUtf(128));
            }
            return new OpenSceneManagerS2C(sceneIds, buf.readUtf(128));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, path));
    }

    private static void writeBox(FriendlyByteBuf buf, AABB box) {
        buf.writeDouble(box.minX);
        buf.writeDouble(box.minY);
        buf.writeDouble(box.minZ);
        buf.writeDouble(box.maxX);
        buf.writeDouble(box.maxY);
        buf.writeDouble(box.maxZ);
    }

    private static AABB readBox(FriendlyByteBuf buf) {
        return new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static String resolveRemoteUrl(String template, String hash, String mapName) {
        if (template == null || template.isBlank() || !SceneAssetCodec.isValidHash(hash)) {
            return "";
        }
        String encodedMap = URLEncoder.encode(mapName == null ? "" : mapName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String resolved = template.trim()
                .replace("{sha256}", hash)
                .replace("{map}", encodedMap);
        if (resolved.length() > 4096) {
            return "";
        }
        try {
            URI uri = URI.create(resolved);
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null ? uri.toASCIIString() : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}

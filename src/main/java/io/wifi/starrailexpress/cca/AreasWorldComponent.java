package io.wifi.starrailexpress.cca;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AreasWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<AreasWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("areas"),
            AreasWorldComponent.class);
    private final Level world;

    public static enum ScrollAxis {
        X, Y, Z, NONE
    }

    private ScrollAxis sceneScroll = ScrollAxis.NONE;
    private boolean sceneAreaConfigured = false;
    private String sceneId = "";
    private String sceneAssetHash = "";
    private String sceneAssetRemoteUrl = "";
    private boolean sceneAssetTrusted = false;
    private Vec3 sceneDisplayOffset = Vec3.ZERO;
    public HashSet<String> disabledTasks = new HashSet<>();

    public HashSet<String> getDisabledTasks() {
        if (this.disabledTasks == null)
            return new HashSet<>();
        return new HashSet<>(this.disabledTasks);
    }

    public static class PosWithOrientation {
        public final Vec3 pos;
        public final float yaw;
        public final float pitch;

        public PosWithOrientation(Vec3 pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public PosWithOrientation(double x, double y, double z, float yaw, float pitch) {
            this(new Vec3(x, y, z), yaw, pitch);
        }

    }

    public static Vec3 getVec3dFromNbt(CompoundTag ctag, String name) {
        if (ctag.contains(name)) {
            var tag = ctag.getCompound(name);
            return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        }
        return new Vec3(0, 0, 0);
    }

    public void writeVec3dToNbt(CompoundTag tag, Vec3 vec3d, String name) {
        CompoundTag ctag = new CompoundTag();
        ctag.putDouble("X", vec3d.x());
        ctag.putDouble("Y", vec3d.y());
        ctag.putDouble("Z", vec3d.z());
        tag.put(name, ctag);
    }

    public static PosWithOrientation getPosWithOrientationFromNbt(CompoundTag ctag, String name) {
        if (ctag.contains(name, CompoundTag.TAG_COMPOUND)) {
            var tag = ctag.getCompound(name);
            return new PosWithOrientation(tag.getDouble("X"), tag.getFloat("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        }
        return new PosWithOrientation(0, 0, 0, 0, 0);
    }

    public void writePosWithOrientationToNbt(CompoundTag tag, PosWithOrientation posWithOrientation, String name) {
        CompoundTag ctag = new CompoundTag();
        ctag.putDouble("X", posWithOrientation.pos.x());
        ctag.putDouble("Y", posWithOrientation.pos.y());
        ctag.putDouble("Z", posWithOrientation.pos.z());
        ctag.putFloat("Yaw", posWithOrientation.yaw);
        ctag.putFloat("Pitch", posWithOrientation.pitch);
        tag.put(name, ctag);
    }

    public static AABB getBoxFromNbt(CompoundTag tag, String name) {
        if (tag.contains(name, CompoundTag.TAG_COMPOUND)) {
            var ctag = tag.getCompound(name);
            return new AABB(ctag.getDouble("MinX"), ctag.getDouble("MinY"), ctag.getDouble("MinZ"),
                    ctag.getDouble("MaxX"), ctag.getDouble("MaxY"), ctag.getDouble("MaxZ"));
        }
        return new AABB(0, 0, 0, 0, 0, 0);
    }

    public void writeBoxToNbt(CompoundTag ctag, AABB box, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("MinX", box.minX);
        tag.putDouble("MinY", box.minY);
        tag.putDouble("MinZ", box.minZ);
        tag.putDouble("MaxX", box.maxX);
        tag.putDouble("MaxY", box.maxY);
        tag.putDouble("MaxZ", box.maxZ);
        ctag.put(name, tag);
    }

    // Game areas
    // PosWithOrientation spawnPos = new PosWithOrientation(-872.5f, 0f, -323f, 90f,
    // 0f);
    // PosWithOrientation spectatorSpawnPos = new PosWithOrientation(-68f, 133f,
    // -535.5f, -90f, 15f);
    //
    // Box readyArea = new Box(-1017, -1, -363.5f, -813, 3, -357.5f);
    // Vec3d playAreaOffset = new Vec3d(963, 121, -175);
    // Box playArea = new Box(-140, 118, -535.5f - 15, 230, 200, -535.5f + 15);
    //
    // Box resetTemplateArea = new Box(-57, 64, -531, 177, 74, -541);
    // Box resetPasteArea = resetTemplateArea.offset(0, 55, 0);

    PosWithOrientation spawnPos = null;
    PosWithOrientation spectatorSpawnPos = new PosWithOrientation(-68f, 133f, -535.5f, -90f, 15f);

    AABB readyArea = new AABB(0, 0, 0, 100, 10, 100);
    Vec3 playAreaOffset = new Vec3(0, 0, 0);
    AABB sceneArea = new AABB(0, 20, 0, 100, 30, 100);
    AABB playArea = new AABB(0, 0, 0, 100, 10, 100);

    AABB resetTemplateArea = new AABB(0, 0, 0, 0, 0, 0);
    AABB resetPasteArea = new AABB(0, 0, 0, 0, 0, 0); // Default: resetTemplateArea.offset(0, 55, 0)

    // Room count
    int roomCount = 1;

    // Room positions map
    Map<Integer, Vec3> roomPositions = new HashMap<>();
    public boolean canJump = false;
    public boolean canSwim = false;
    public boolean noReset = false;
    public String mapName = null;
    public boolean haveOutsideSound = false;

    // 场景偏移配置 - 将sceneArea内的区块渲染到偏移位置（默认关闭）
    public boolean sceneOffsetEnabled = false;
    public double sceneOffsetX = 0;
    public double sceneOffsetY = 125; // 默认向上偏移125格（场景放置在游玩区域下方100-150格）
    public double sceneOffsetZ = 0;
    
    // 雪花效果配置（默认关闭）
    public boolean snowEnabled = false;

    // 雾气效果配置（默认启用）
    public boolean fogEnabled = true;
    
    // 雾气可见范围（fogEnd，默认200），仅在 fogEnabled 启用时生效
    public float fogEnd = 200.0f;

    // 雾气形状（SPHERE 或 CYLINDER），默认 SPHERE，仅在 fogEnabled 启用时生效
    public String fogShape = "SPHERE";
    
    // 天气配置（默认晴天）
    public String weather = "clear"; // clear, rain, thunder
    
    // 重力配置（默认0.08）
    public double gravity = 0.08;
    
    // 药水效果配置（格式：["namespace:effect_id,level", ...]，为空数组则无效果）
    public List<String> effect = new ArrayList<>();
    
    // 时间配置（默认午夜 18000）
    public long time = 18000;
    
    // 昼夜循环配置（默认关闭）
    public boolean daylightCycle = false;
    
    // 天气循环配置（默认关闭）
    public boolean weatherCycle = false;
    
    public boolean mustCopy = false;

    // 支持的游戏模式列表，为空表示支持所有模式
    public java.util.List<String> gameModes = new java.util.ArrayList<>();

    public PosWithOrientation getSpawnPos() {
        return spawnPos;
    }

    public void setSpawnPos(PosWithOrientation spawnPos) {
        this.spawnPos = spawnPos;
    }

    public PosWithOrientation getSpectatorSpawnPos() {
        return spectatorSpawnPos;
    }

    public void setSpectatorSpawnPos(PosWithOrientation spectatorSpawnPos) {
        this.spectatorSpawnPos = spectatorSpawnPos;
    }

    public AABB getReadyArea() {
        return readyArea;
    }

    public void setSceneArea(AABB area) {
        this.sceneArea = area;
    }

    public ScrollAxis getSceneScroll() {
        return sceneScroll;
    }

    public void setSceneScroll(ScrollAxis sceneScroll) {
        this.sceneScroll = sceneScroll == null ? ScrollAxis.NONE : sceneScroll;
    }

    public boolean isSceneAreaConfigured() {
        return sceneAreaConfigured;
    }

    public void setSceneAreaConfigured(boolean sceneAreaConfigured) {
        this.sceneAreaConfigured = sceneAreaConfigured;
    }

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId == null ? "" : sceneId.trim();
    }

    public String getSceneAssetHash() {
        return sceneAssetHash;
    }

    public void setSceneAssetHash(String sceneAssetHash) {
        this.sceneAssetHash = sceneAssetHash == null ? "" : sceneAssetHash;
    }

    public String getSceneAssetRemoteUrl() {
        return sceneAssetRemoteUrl;
    }

    public void setSceneAssetRemoteUrl(String sceneAssetRemoteUrl) {
        this.sceneAssetRemoteUrl = sceneAssetRemoteUrl == null ? "" : sceneAssetRemoteUrl.trim();
    }

    public boolean isSceneAssetTrusted() {
        return sceneAssetTrusted;
    }

    public void setSceneAssetTrusted(boolean sceneAssetTrusted) {
        this.sceneAssetTrusted = sceneAssetTrusted;
    }

    public Vec3 getSceneDisplayOffset() {
        return sceneDisplayOffset;
    }

    public void setSceneDisplayOffset(Vec3 sceneDisplayOffset) {
        this.sceneDisplayOffset = sceneDisplayOffset == null ? Vec3.ZERO : sceneDisplayOffset;
    }

    public void setReadyArea(AABB readyArea) {
        this.readyArea = readyArea;
    }

    public Vec3 getPlayAreaOffset() {
        return playAreaOffset;
    }

    public void setPlayAreaOffset(Vec3 playAreaOffset) {
        this.playAreaOffset = playAreaOffset;
    }

    public AABB getSceneArea() {
        return sceneArea;
    }

    public AABB getPlayArea() {
        return playArea;
    }

    public void setPlayArea(AABB playArea) {
        this.playArea = playArea;
    }

    public AABB getResetTemplateArea() {
        return resetTemplateArea;
    }

    public void setResetTemplateArea(AABB resetTemplateArea) {
        this.resetTemplateArea = resetTemplateArea;
    }

    public AABB getResetPasteArea() {
        return resetPasteArea;
    }

    public void setResetPasteArea(AABB resetPasteArea) {
        this.resetPasteArea = resetPasteArea;
    }

    public int getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(int roomCount) {
        this.roomCount = roomCount;
    }

    public Map<Integer, Vec3> getRoomPositions() {
        return roomPositions;
    }

    public void setRoomPositions(Map<Integer, Vec3> roomPositions) {
        this.roomPositions = roomPositions;
    }

    public Vec3 getRoomPosition(int roomNumber) {
        return roomPositions.get(roomNumber);
    }

    public void setRoomPosition(int roomNumber, Vec3 position) {
        this.roomPositions.put(roomNumber, position);
    }

    public AreasWorldComponent(Level world) {
        this.world = world;
        // Initialize default room positions
        initializeDefaultRoomPositions();
    }

    private void initializeDefaultRoomPositions() {
        roomPositions.put(1, new Vec3(0, 0, 0));
    }

    public void sync() {
        KEY.sync(this.world);
    }

    // 新增方法：从单独的 readyArea.json 文件加载准备区域
    public void loadReadyAreaFromFile() {
        try {
            Path readyAreaFilePath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "readyArea.json");
            File readyAreaFile = readyAreaFilePath.toFile();

            if (readyAreaFile.exists()) {
                FileReader reader = new FileReader(readyAreaFile);
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                reader.close();

                if (jsonObject.has("readyArea")) {
                    JsonObject readyAreaObj = jsonObject.getAsJsonObject("readyArea");
                    this.readyArea = new AABB(
                            readyAreaObj.get("minX").getAsDouble(),
                            readyAreaObj.get("minY").getAsDouble(),
                            readyAreaObj.get("minZ").getAsDouble(),
                            readyAreaObj.get("maxX").getAsDouble(),
                            readyAreaObj.get("maxY").getAsDouble(),
                            readyAreaObj.get("maxZ").getAsDouble());
                    SRE.LOGGER.info("Successfully loaded readyArea from readyArea.json");
                }
            }
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to load readyArea from readyArea.json", e);
        }
    }

    // 新增方法：将准备区域保存到单独的 readyArea.json 文件
    public void saveReadyAreaToFile() {
        try {
            Path areasDirPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString());
            File areasDir = areasDirPath.toFile();
            if (!areasDir.exists()) {
                areasDir.mkdirs();
            }

            Path readyAreaFilePath = Paths.get(areasDirPath.toString(), "readyArea.json");
            File readyAreaFile = readyAreaFilePath.toFile();

            JsonObject jsonObject = new JsonObject();

            // Save ready area
            JsonObject readyAreaObj = new JsonObject();
            readyAreaObj.addProperty("minX", this.readyArea.minX);
            readyAreaObj.addProperty("minY", this.readyArea.minY);
            readyAreaObj.addProperty("minZ", this.readyArea.minZ);
            readyAreaObj.addProperty("maxX", this.readyArea.maxX);
            readyAreaObj.addProperty("maxY", this.readyArea.maxY);
            readyAreaObj.addProperty("maxZ", this.readyArea.maxZ);
            jsonObject.add("readyArea", readyAreaObj);

            // Write to file
            FileWriter writer = new FileWriter(readyAreaFile);
            new Gson().toJson(jsonObject, writer);
            writer.close();

            SRE.LOGGER.info("Successfully saved readyArea to readyArea.json");
        } catch (IOException e) {
            SRE.LOGGER.error("Failed to save readyArea to readyArea.json", e);
        }
    }

    // 重载准备区域配置并同步到客户端
    public void reloadReadyArea() {
        // 先保存当前的 readyArea 到单独的文件
        saveReadyAreaToFile();

        // 从单独的文件加载 readyArea
        loadReadyAreaFromFile();

        // 同步到客户端
        sync();
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // this.spawnPos = getPosWithOrientationFromNbt(tag, "spawnPos");
        // this.spectatorSpawnPos = getPosWithOrientationFromNbt(tag,
        // "spectatorSpawnPos");
        if (tag.contains("readyArea")) {
            this.readyArea = getBoxFromNbt(tag, "readyArea");
        }
        if (tag.contains("playArea")) {
            this.playArea = getBoxFromNbt(tag, "playArea");
        }
        if (tag.contains("sceneArea")) {
            this.sceneArea = getBoxFromNbt(tag, "sceneArea");
        }
        this.sceneAreaConfigured = tag.contains("sceneAreaConfigured") && tag.getBoolean("sceneAreaConfigured");
        this.sceneId = tag.contains("sceneId") ? tag.getString("sceneId") : "";
        this.sceneScroll = parseScrollAxis(tag.getString("sceneScroll"),
                this.sceneAreaConfigured ? ScrollAxis.X : ScrollAxis.NONE);
        this.sceneAssetHash = tag.contains("sceneAssetHash") ? tag.getString("sceneAssetHash") : "";
        this.sceneAssetRemoteUrl = tag.contains("sceneAssetRemoteUrl") ? tag.getString("sceneAssetRemoteUrl") : "";
        this.sceneAssetTrusted = tag.contains("sceneAssetTrusted") && tag.getBoolean("sceneAssetTrusted");
        this.sceneDisplayOffset = getVec3dFromNbt(tag, "sceneDisplayOffset");
        this.mapName = tag.contains("mapName") && !tag.getString("mapName").isBlank()
                ? tag.getString("mapName")
                : null;
        this.canJump = tag.contains("canJump") ? tag.getBoolean("canJump") : false;
        this.canSwim = tag.contains("canSwim") ? tag.getBoolean("canSwim") : false;
        this.haveOutsideSound = tag.contains("haveOutsideSound") ? tag.getBoolean("haveOutsideSound") : false;
        this.snowEnabled = tag.contains("snowEnabled") ? tag.getBoolean("snowEnabled") : false;
        this.fogEnabled = tag.contains("fogEnabled") ? tag.getBoolean("fogEnabled") : true;
        this.fogEnd = tag.contains("fogEnd") ? tag.getFloat("fogEnd") : 200.0f;
        this.fogShape = tag.contains("fogShape") ? tag.getString("fogShape") : "SPHERE";
        this.sceneOffsetEnabled = tag.contains("sceneOffsetEnabled") ? tag.getBoolean("sceneOffsetEnabled") : false;
        this.sceneOffsetX = tag.contains("sceneOffsetX") ? tag.getDouble("sceneOffsetX") : 0;
        this.sceneOffsetY = tag.contains("sceneOffsetY") ? tag.getDouble("sceneOffsetY") : 125;
        this.sceneOffsetZ = tag.contains("sceneOffsetZ") ? tag.getDouble("sceneOffsetZ") : 0;
        this.weather = tag.contains("weather") ? tag.getString("weather") : "clear";
        this.gravity = tag.contains("gravity") ? tag.getDouble("gravity") : 0.08;
        this.effect = new ArrayList<>();
        if (tag.contains("effect")) {
            var list = tag.getList("effect", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                this.effect.add(list.getString(i));
            }
        }
        this.time = tag.contains("time") ? tag.getLong("time") : 18000;
        this.daylightCycle = tag.contains("daylightCycle") ? tag.getBoolean("daylightCycle") : false;
        this.weatherCycle = tag.contains("weatherCycle") ? tag.getBoolean("weatherCycle") : false;
        // this.playAreaOffset = getVec3dFromNbt(tag, "playAreaOffset");
        // this.playArea = getBoxFromNbt(tag, "playArea");
        //
        // this.resetTemplateArea = getBoxFromNbt(tag, "resetTemplateArea");
        // this.resetPasteArea = getBoxFromNbt(tag, "resetPasteArea");
        //
        // // 从NBT读取房间数量和房间位置（如果存在）
        // if (tag.contains("roomCount")) {
        // this.roomCount = tag.getInt("roomCount");
        // }
        //
        // 房间位置需要从NBT中读取（如果实现此功能）
        // 这里暂时不实现，因为NBT格式可能需要专门处理Map类型
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // writePosWithOrientationToNbt(tag, this.spawnPos, "spawnPos");
        // writePosWithOrientationToNbt(tag, this.spectatorSpawnPos,
        // "spectatorSpawnPos");
        if (this.readyArea != null) {
            writeBoxToNbt(tag, this.readyArea, "readyArea");
        }
        if (this.playArea != null) {
            writeBoxToNbt(tag, this.playArea, "playArea");
        }
        if (this.sceneArea != null) {
            writeBoxToNbt(tag, this.sceneArea, "sceneArea");
        }
        tag.putString("sceneScroll", this.sceneScroll.name());
        tag.putBoolean("sceneAreaConfigured", this.sceneAreaConfigured);
        tag.putString("sceneId", this.sceneId);
        tag.putString("sceneAssetHash", this.sceneAssetHash);
        tag.putString("sceneAssetRemoteUrl", this.sceneAssetRemoteUrl);
        tag.putBoolean("sceneAssetTrusted", this.sceneAssetTrusted);
        writeVec3dToNbt(tag, this.sceneDisplayOffset, "sceneDisplayOffset");
        if (this.mapName != null) {
            tag.putString("mapName", this.mapName);
        }
        // writeVec3dToNbt(tag, this.playAreaOffset, "playAreaOffset");
        // writeBoxToNbt(tag, this.playArea, "playArea");
        //
        // writeBoxToNbt(tag, this.resetTemplateArea, "resetTemplateArea");
        // writeBoxToNbt(tag, this.resetPasteArea, "resetPasteArea");
        //
        // 将房间数量写入NBT
        tag.putInt("roomCount", this.roomCount);
        tag.putBoolean("canJump", this.canJump);
        tag.putBoolean("canSwim", this.canSwim);
        tag.putBoolean("haveOutsideSound", this.haveOutsideSound);
        tag.putBoolean("snowEnabled", this.snowEnabled);
        tag.putBoolean("fogEnabled", this.fogEnabled);
        tag.putFloat("fogEnd", this.fogEnd);
        tag.putString("fogShape", this.fogShape);
        tag.putBoolean("sceneOffsetEnabled", this.sceneOffsetEnabled);
        tag.putDouble("sceneOffsetX", this.sceneOffsetX);
        tag.putDouble("sceneOffsetY", this.sceneOffsetY);
        tag.putDouble("sceneOffsetZ", this.sceneOffsetZ);
        tag.putString("weather", this.weather);
        tag.putDouble("gravity", this.gravity);
        var effectList = new net.minecraft.nbt.ListTag();
        for (String e : this.effect) {
            effectList.add(net.minecraft.nbt.StringTag.valueOf(e));
        }
        tag.put("effect", effectList);
        tag.putLong("time", this.time);
        tag.putBoolean("daylightCycle", this.daylightCycle);
        tag.putBoolean("weatherCycle", this.weatherCycle);

        // 房间位置需要写入NBT（如果实现此功能）
        // 这里暂时不实现，因为NBT格式可能需要专门处理Map类型
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
        if (tag.contains("readyArea")) {
            this.readyArea = getBoxFromNbt(tag, "readyArea");
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
        if (this.readyArea != null) {
            writeBoxToNbt(tag, this.readyArea, "readyArea");
        }
    }

    public static ScrollAxis parseScrollAxis(String value, ScrollAxis fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ScrollAxis.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

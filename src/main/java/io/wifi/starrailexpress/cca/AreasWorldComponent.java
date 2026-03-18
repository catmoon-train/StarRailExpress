package io.wifi.starrailexpress.cca;

import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.wifi.starrailexpress.SRE;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AreasWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<AreasWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("areas"),
            AreasWorldComponent.class);
    private final Level world;

    public static class PosWithOrientation {
        public final Vec3 pos;
        public final float yaw;
        public final float pitch;

        PosWithOrientation(Vec3 pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public PosWithOrientation(double x, double y, double z, float yaw, float pitch) {
            this(new Vec3(x, y, z), yaw, pitch);
        }

    }

    public static Vec3 getVec3dFromNbt(CompoundTag tag, String name) {
        return new Vec3(tag.getDouble(name + "X"), tag.getFloat(name + "Y"), tag.getDouble(name + "Z"));
    }

    public void writeVec3dToNbt(CompoundTag tag, Vec3 vec3d, String name) {
        tag.putDouble(name + "X", vec3d.x());
        tag.putDouble(name + "Y", vec3d.y());
        tag.putDouble(name + "Z", vec3d.z());
    }

    public static PosWithOrientation getPosWithOrientationFromNbt(CompoundTag tag, String name) {
        return new PosWithOrientation(tag.getDouble(name + "X"), tag.getFloat(name + "Y"), tag.getDouble(name + "Z"),
                tag.getFloat(name + "Yaw"), tag.getFloat(name + "Pitch"));
    }

    public void writePosWithOrientationToNbt(CompoundTag tag, PosWithOrientation posWithOrientation, String name) {
        tag.putDouble(name + "X", posWithOrientation.pos.x());
        tag.putDouble(name + "Y", posWithOrientation.pos.y());
        tag.putDouble(name + "Z", posWithOrientation.pos.z());
        tag.putDouble(name + "Yaw", posWithOrientation.yaw);
        tag.putDouble(name + "Pitch", posWithOrientation.pitch);
    }

    public static AABB getBoxFromNbt(CompoundTag tag, String name) {
        if (!tag.contains(name))
            return null;
        return new AABB(tag.getDouble(name + "MinX"), tag.getFloat(name + "MinY"), tag.getDouble(name + "MinZ"),
                tag.getDouble(name + "MaxX"), tag.getFloat(name + "MaxY"), tag.getDouble(name + "MaxZ"));
    }

    public void writeBoxToNbt(CompoundTag tag, AABB box, String name) {
        tag.putDouble(name + "MinX", box.minX);
        tag.putDouble(name + "MinY", box.minY);
        tag.putDouble(name + "MinZ", box.minZ);
        tag.putDouble(name + "MaxX", box.maxX);
        tag.putDouble(name + "MaxY", box.maxY);
        tag.putDouble(name + "MaxZ", box.maxZ);
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

    PosWithOrientation spawnPos = new PosWithOrientation(-872.5f, 0f, -323f, 90f, 0f);
    PosWithOrientation spectatorSpawnPos = new PosWithOrientation(-68f, 133f, -535.5f, -90f, 15f);

    AABB readyArea = new AABB(-1017, -1, -363.5f, -813, 3, -357.5f);
    Vec3 playAreaOffset = new Vec3(963, 121, -175);
    AABB playArea = new AABB(177, 60, -524, -82, 84, -546);

    AABB resetTemplateArea = new AABB(177, 60, -524, -82, 84, -546);
    AABB resetPasteArea = new AABB(177, 115, -524, -82, 139, -546); // Default: resetTemplateArea.offset(0, 55, 0)

    // Room count
    int roomCount = 7;

    // Room positions map
    Map<Integer, Vec3> roomPositions = new HashMap<>();
    public boolean canJump = false;
    public boolean canSwim = false;
    public boolean noReset = false;
    public String mapName = null;
    public boolean haveOutsideSound = false;

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

    public void setReadyArea(AABB readyArea) {
        this.readyArea = readyArea;
    }

    public Vec3 getPlayAreaOffset() {
        return playAreaOffset;
    }

    public void setPlayAreaOffset(Vec3 playAreaOffset) {
        this.playAreaOffset = playAreaOffset;
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
            Path readyAreaFilePath = Paths.get(world.getServer().getServerDirectory().toString(), "world",
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
            Path areasDirPath = Paths.get(world.getServer().getServerDirectory().toString(), "world");
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

    public void saveToFile() {
        try {
            Path areasDirPath = Paths.get(world.getServer().getServerDirectory().toString(), "world");
            File areasDir = areasDirPath.toFile();
            if (!areasDir.exists()) {
                areasDir.mkdirs();
            }

            Path areasFilePath = Paths.get(areasDirPath.toString(), "areas.json");
            File areasFile = areasFilePath.toFile();

            JsonObject jsonObject = new JsonObject();

            // Save spawn position
            JsonObject spawnPosObj = new JsonObject();
            spawnPosObj.addProperty("x", this.spawnPos.pos.x);
            spawnPosObj.addProperty("y", this.spawnPos.pos.y);
            spawnPosObj.addProperty("z", this.spawnPos.pos.z);
            spawnPosObj.addProperty("yaw", this.spawnPos.yaw);
            spawnPosObj.addProperty("pitch", this.spawnPos.pitch);
            jsonObject.add("spawnPos", spawnPosObj);

            // Save spectator spawn position
            JsonObject spectatorSpawnPosObj = new JsonObject();
            spectatorSpawnPosObj.addProperty("x", this.spectatorSpawnPos.pos.x);
            spectatorSpawnPosObj.addProperty("y", this.spectatorSpawnPos.pos.y);
            spectatorSpawnPosObj.addProperty("z", this.spectatorSpawnPos.pos.z);
            spectatorSpawnPosObj.addProperty("yaw", this.spectatorSpawnPos.yaw);
            spectatorSpawnPosObj.addProperty("pitch", this.spectatorSpawnPos.pitch);
            jsonObject.add("spectatorSpawnPos", spectatorSpawnPosObj);

            // Save ready area
            JsonObject readyAreaObj = new JsonObject();
            readyAreaObj.addProperty("minX", this.readyArea.minX);
            readyAreaObj.addProperty("minY", this.readyArea.minY);
            readyAreaObj.addProperty("minZ", this.readyArea.minZ);
            readyAreaObj.addProperty("maxX", this.readyArea.maxX);
            readyAreaObj.addProperty("maxY", this.readyArea.maxY);
            readyAreaObj.addProperty("maxZ", this.readyArea.maxZ);
            jsonObject.add("readyArea", readyAreaObj);

            // Save play area offset
            JsonObject playAreaOffsetObj = new JsonObject();
            playAreaOffsetObj.addProperty("x", this.playAreaOffset.x);
            playAreaOffsetObj.addProperty("y", this.playAreaOffset.y);
            playAreaOffsetObj.addProperty("z", this.playAreaOffset.z);
            jsonObject.add("playAreaOffset", playAreaOffsetObj);

            // Save play area
            JsonObject playAreaObj = new JsonObject();
            playAreaObj.addProperty("minX", this.playArea.minX);
            playAreaObj.addProperty("minY", this.playArea.minY);
            playAreaObj.addProperty("minZ", this.playArea.minZ);
            playAreaObj.addProperty("maxX", this.playArea.maxX);
            playAreaObj.addProperty("maxY", this.playArea.maxY);
            playAreaObj.addProperty("maxZ", this.playArea.maxZ);
            jsonObject.add("playArea", playAreaObj);

            // Save reset template area
            JsonObject resetTemplateAreaObj = new JsonObject();
            resetTemplateAreaObj.addProperty("minX", this.resetTemplateArea.minX);
            resetTemplateAreaObj.addProperty("minY", this.resetTemplateArea.minY);
            resetTemplateAreaObj.addProperty("minZ", this.resetTemplateArea.minZ);
            resetTemplateAreaObj.addProperty("maxX", this.resetTemplateArea.maxX);
            resetTemplateAreaObj.addProperty("maxY", this.resetTemplateArea.maxY);
            resetTemplateAreaObj.addProperty("maxZ", this.resetTemplateArea.maxZ);
            jsonObject.add("resetTemplateArea", resetTemplateAreaObj);

            // Save reset paste area
            JsonObject resetPasteAreaObj = new JsonObject();
            resetPasteAreaObj.addProperty("minX", this.resetPasteArea.minX);
            resetPasteAreaObj.addProperty("minY", this.resetPasteArea.minY);
            resetPasteAreaObj.addProperty("minZ", this.resetPasteArea.minZ);
            resetPasteAreaObj.addProperty("maxX", this.resetPasteArea.maxX);
            resetPasteAreaObj.addProperty("maxY", this.resetPasteArea.maxY);
            resetPasteAreaObj.addProperty("maxZ", this.resetPasteArea.maxZ);
            jsonObject.add("resetPasteArea", resetPasteAreaObj);

            // Save room count
            jsonObject.addProperty("roomCount", this.roomCount);
            jsonObject.addProperty("canJump", false);
            // Save room positions
            JsonObject roomPositionsObj = new JsonObject();
            for (Map.Entry<Integer, Vec3> entry : this.roomPositions.entrySet()) {
                JsonObject posObj = new JsonObject();
                posObj.addProperty("x", entry.getValue().x);
                posObj.addProperty("y", entry.getValue().y);
                posObj.addProperty("z", entry.getValue().z);
                roomPositionsObj.add(String.valueOf(entry.getKey()), posObj);
            }
            jsonObject.add("roomPositions", roomPositionsObj);

            // Write to file
            FileWriter writer = new FileWriter(areasFile);
            new Gson().toJson(jsonObject, writer);
            writer.close();
        } catch (IOException e) {
            SRE.LOGGER.error("Failed to save areas to file", e);
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // this.spawnPos = getPosWithOrientationFromNbt(tag, "spawnPos");
        // this.spectatorSpawnPos = getPosWithOrientationFromNbt(tag,
        // "spectatorSpawnPos");
        if (tag.contains("readyArea"))
            this.readyArea = getBoxFromNbt(tag, "readyArea");
        this.canJump = tag.contains("canJump") ? tag.getBoolean("canJump") : false;
        this.canSwim = tag.contains("canSwim") ? tag.getBoolean("canSwim") : false;
        this.haveOutsideSound = tag.contains("haveOutsideSound") ? tag.getBoolean("haveOutsideSound") : false;
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
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // writePosWithOrientationToNbt(tag, this.spawnPos, "spawnPos");
        // writePosWithOrientationToNbt(tag, this.spectatorSpawnPos,
        // "spectatorSpawnPos");
        if (this.readyArea != null)
            writeBoxToNbt(tag, this.readyArea, "readyArea");
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

        // 房间位置需要写入NBT（如果实现此功能）
        // 这里暂时不实现，因为NBT格式可能需要专门处理Map类型
    }
}
package net.exmo.sre.camera;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级相机轨道：由若干 {@link AdvancedCameraNode} 关键帧组成的完整运镜序列，附带黑边、循环、结束恢复视角等开关。
 *
 * <p>支持从 JSON 解析（{@link #fromJson(String)}）与序列化（{@link #toJson()}），网络层以 JSON 字符串传输，
 * 服务端与客户端共用同一套解析逻辑。
 *
 * <h3>JSON Schema</h3>
 * <pre>
 * {
 *   "blackBars": true,        // 是否显示电影黑边（默认 true）
 *   "restore": true,          // 结束后是否恢复到原视角/第一人称（默认 true）
 *   "loop": false,            // 是否循环播放（默认 false）
 *   "nodes": [
 *     // duration: 从上一节点过渡到本节点的 tick 数；hold: 到达后停留 tick 数
 *     // pos / lookAt: [x,y,z] 数组或 {"x":,"y":,"z":} 对象；yaw/pitch: 显式角度（lookAt 为空时生效）
 *     // fov: 视野角度，<=0 表示不覆盖
 *     {"duration": 0,  "hold": 10, "pos": [10.5, 80, 20.5], "lookAt": [0, 70, 0], "fov": 60},
 *     {"duration": 60, "hold": 0,  "pos": [2.5, 72, 4.5],   "lookAt": [0, 70, 0], "fov": 70}
 *   ]
 * }
 * </pre>
 * 顶层也可直接是节点数组 {@code [ {...}, {...} ]}，此时各开关取默认值。
 */
public final class AdvancedCameraSequence {

    private static final Gson GSON = new Gson();

    public final List<AdvancedCameraNode> nodes;
    public final boolean blackBars;
    public final boolean restore;
    public final boolean loop;

    public AdvancedCameraSequence(List<AdvancedCameraNode> nodes, boolean blackBars, boolean restore, boolean loop) {
        this.nodes = nodes;
        this.blackBars = blackBars;
        this.restore = restore;
        this.loop = loop;
    }

    /** 整条轨道总时长（所有节点的过渡 + 停留之和）。 */
    public int totalTicks() {
        int total = 0;
        for (AdvancedCameraNode node : nodes) {
            total += node.totalTicks();
        }
        return total;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    // ==================== 解析 ====================

    /**
     * 从 JSON 字符串解析轨道。
     *
     * @throws JsonParseException 当 JSON 非法或缺少节点时
     */
    public static AdvancedCameraSequence fromJson(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray nodesArray;
        boolean blackBars = true;
        boolean restore = true;
        boolean loop = false;

        if (root.isJsonArray()) {
            nodesArray = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("nodes") || !obj.get("nodes").isJsonArray()) {
                throw new JsonParseException("轨道 JSON 缺少 \"nodes\" 数组");
            }
            nodesArray = obj.getAsJsonArray("nodes");
            blackBars = getBool(obj, "blackBars", true);
            restore = getBool(obj, "restore", true);
            loop = getBool(obj, "loop", false);
        } else {
            throw new JsonParseException("轨道 JSON 必须是对象或数组");
        }

        List<AdvancedCameraNode> nodes = new ArrayList<>();
        for (JsonElement element : nodesArray) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("节点必须是 JSON 对象");
            }
            nodes.add(parseNode(element.getAsJsonObject()));
        }
        if (nodes.isEmpty()) {
            throw new JsonParseException("轨道至少需要一个节点");
        }
        return new AdvancedCameraSequence(nodes, blackBars, restore, loop);
    }

    private static AdvancedCameraNode parseNode(JsonObject obj) {
        int duration = getInt(obj, "duration", 0);
        int hold = getInt(obj, "hold", 0);
        Vec3 pos = getVec(obj, "pos");
        Vec3 lookAt = getVec(obj, "lookAt");
        Float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : null;
        Float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : null;
        float fov = obj.has("fov") ? obj.get("fov").getAsFloat() : 0f;
        return new AdvancedCameraNode(duration, hold, pos, lookAt, yaw, pitch, fov);
    }

    @Nullable
    private static Vec3 getVec(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() != 3) {
                throw new JsonParseException("\"" + key + "\" 数组必须包含 3 个数字 [x, y, z]");
            }
            return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        }
        if (element.isJsonObject()) {
            JsonObject vecObj = element.getAsJsonObject();
            return new Vec3(vecObj.get("x").getAsDouble(), vecObj.get("y").getAsDouble(), vecObj.get("z").getAsDouble());
        }
        throw new JsonParseException("\"" + key + "\" 必须是 [x,y,z] 数组或 {x,y,z} 对象");
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    // ==================== 序列化 ====================

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("blackBars", blackBars);
        root.addProperty("restore", restore);
        root.addProperty("loop", loop);
        JsonArray array = new JsonArray();
        for (AdvancedCameraNode node : nodes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("duration", node.durationTicks);
            obj.addProperty("hold", node.holdTicks);
            if (node.pos != null) {
                obj.add("pos", vecToJson(node.pos));
            }
            if (node.lookAt != null) {
                obj.add("lookAt", vecToJson(node.lookAt));
            }
            if (node.yaw != null) {
                obj.addProperty("yaw", node.yaw);
            }
            if (node.pitch != null) {
                obj.addProperty("pitch", node.pitch);
            }
            if (node.fov > 0) {
                obj.addProperty("fov", node.fov);
            }
            array.add(obj);
        }
        root.add("nodes", array);
        return GSON.toJson(root);
    }

    private static JsonArray vecToJson(Vec3 vec) {
        JsonArray array = new JsonArray();
        array.add(vec.x);
        array.add(vec.y);
        array.add(vec.z);
        return array;
    }
}

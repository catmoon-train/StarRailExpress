package net.exmo.sre.namewhitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 基于游戏名(username)的玩家白名单存储。
 * <p>
 * 与 {@link net.exmo.sre.mod_whitelist} 的“模组白名单”不同：模组白名单校验的是客户端安装的模组，
 * 而本系统只根据玩家的用户名(username)判定是否放行——因此可以给非正版(离线模式)玩家开白名单。
 * <p>
 * 名称匹配大小写不敏感（{@link String#CASE_INSENSITIVE_ORDER}），但保留录入时的原始大小写用于展示。
 * 数据持久化到 {@code config/name_whitelist.json}。
 * <p>
 * 所有读写都发生在服务端主线程（命令线程与玩家加入事件线程一致），此处仍统一 synchronized 以防万一。
 */
public final class NameWhitelistStorage {

    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("name_whitelist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 大小写不敏感去重，同时保留原始大小写。 */
    private static final Set<String> NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static boolean enabled = false;

    private NameWhitelistStorage() {
    }

    static {
        load();
    }

    /** 触发静态初始化（可在模组初始化时显式调用一次，便于尽早读盘并打印日志）。 */
    public static void init() {
        // 静态块已在类加载时执行，此方法仅用于显式触发。
        MWLogger.LOGGER.info("[NameWhitelist] 用户名白名单已加载：{} 个名字，状态：{}", NAMES.size(), enabled ? "启用" : "关闭");
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    /** 判定某用户名是否在白名单中（大小写不敏感）。 */
    public static synchronized boolean isWhitelisted(String username) {
        return username != null && NAMES.contains(username.trim());
    }

    /** @return true 表示新增成功，false 表示已存在。 */
    public static synchronized boolean add(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) return false;
        boolean added = NAMES.add(name);
        if (added) save();
        return added;
    }

    /** @return true 表示移除成功，false 表示原本就不在名单中。 */
    public static synchronized boolean remove(String username) {
        String name = username == null ? "" : username.trim();
        boolean removed = NAMES.remove(name);
        if (removed) save();
        return removed;
    }

    /**
     * 批量导入用户名，使用 {@code ;} 分隔。
     *
     * @return 长度为 2 的数组：[0]=本次实际新增数量，[1]=因重复而跳过的数量。
     */
    public static synchronized int[] importNames(String raw) {
        int added = 0;
        int skipped = 0;
        if (raw != null) {
            for (String token : raw.split(";")) {
                String name = token.trim();
                if (name.isEmpty()) continue;
                if (NAMES.add(name)) {
                    added++;
                } else {
                    skipped++;
                }
            }
        }
        if (added > 0) save();
        return new int[]{added, skipped};
    }

    public static synchronized int clear() {
        int size = NAMES.size();
        NAMES.clear();
        save();
        return size;
    }

    public static synchronized List<String> listNames() {
        return new ArrayList<>(NAMES);
    }

    public static synchronized int size() {
        return NAMES.size();
    }

    /** 从磁盘重新读取（用于 reload 命令）。 */
    public static synchronized void reload() {
        load();
    }

    private static void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                save(); // 生成默认文件
                return;
            }
            String content = Files.readString(CONFIG_FILE);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            NAMES.clear();
            enabled = false;
            if (json != null) {
                if (json.has("enabled")) {
                    enabled = json.get("enabled").getAsBoolean();
                }
                if (json.has("names") && json.get("names").isJsonArray()) {
                    JsonArray arr = json.getAsJsonArray("names");
                    for (int i = 0; i < arr.size(); i++) {
                        String name = arr.get(i).getAsString().trim();
                        if (!name.isEmpty()) NAMES.add(name);
                    }
                }
            }
        } catch (Exception e) {
            MWLogger.LOGGER.error("[NameWhitelist] 读取 name_whitelist.json 失败", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            JsonArray arr = new JsonArray();
            for (String name : NAMES) {
                arr.add(name);
            }
            json.add("names", arr);
            Files.writeString(CONFIG_FILE, GSON.toJson(json));
        } catch (IOException e) {
            MWLogger.LOGGER.error("[NameWhitelist] 写入 name_whitelist.json 失败", e);
        }
    }
}

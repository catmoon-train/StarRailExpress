/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 皮肤「立体（3D）能力」登记表。
 * <p>
 * 皮肤页需要判断某个皮肤是不是 3D 皮肤：走了 GeckoLib {@code .geo.json} 模型、
 * 而不是原版平面 {@code models/item/*.json}。这个信息只有内容模组自己知道
 * （主模组不依赖 GeckoLib，也不该去猜附属模组的资源布局），
 * 所以由内容模组在客户端初始化时调用 {@link #register(String, String)}，
 * 把自己的 Geo 注册表报上来。
 * <p>
 * 两路数据来源，自动合并：
 * <ol>
 *   <li><b>显式登记</b>（推荐）：{@link #register(String, String)} 把
 *       「皮肤类型 + 皮肤 ID」标成 3D，可另带排序权重；
 *       另有 {@link #registerType(String)} 把整类标成 3D（随从这类天生全 3D 的类型）。</li>
 *   <li><b>资源探测</b>（兜底）：{@link #probeGeoResources(ResourceManager)} 在客户端
 *       资源加载后扫一遍 {@code assets/*}{@code /geo/**.geo.json}，
 *       把文件名与皮肤 ID 相同的条目也认作 3D。这样没显式登记的模型
 *       （例如主模组自己带的 Geo 皮肤）也会被识别，不用两边维护两份名单。</li>
 * </ol>
 * 线程安全：构造期 / 客户端初始化期登记，之后只读。
 */
public final class ItemSkin3dRegistry {

    /** 皮肤类型 → 该类型下所有 3D 皮肤 ID */
    private static final Map<String, Set<String>> BY_TYPE = new ConcurrentHashMap<>();

    /** 天生整类都是 3D 的皮肤类型（例如 pet：随从只有 Geo 模型） */
    private static final Set<String> WHOLE_TYPES = ConcurrentHashMap.newKeySet();

    /** 资源探测得到的 3D 模型文件名，按皮肤 ID 兜底 */
    private static final Set<String> PROBED_IDS = ConcurrentHashMap.newKeySet();

    /** 显式登记的排序权重（同类同分时大的排前面），保证排序稳定 */
    private static final Map<String, Integer> WEIGHTS = new ConcurrentHashMap<>();

    private ItemSkin3dRegistry() {
    }

    // ─── 登记 ────────────────────────────────────────────────────────────────

    /**
     * 把一个皮肤登记为 3D 皮肤。
     *
     * @param skinType 皮肤类型（{@code hat} / {@code pet} / {@code knife} …）
     * @param skinId   皮肤 ID（与 {@link ItemSkinManager#getSkins(String)} 的键一致）
     */
    public static void register(String skinType, String skinId) {
        register(skinType, skinId, 0);
    }

    /**
     * 把一个皮肤登记为 3D 皮肤，并给一个排序权重。
     *
     * @param weight 越大越靠前；用于同类同分时的稳定排序，通常传 0
     */
    public static void register(String skinType, String skinId, int weight) {
        if (skinType == null || skinId == null || skinType.isBlank() || skinId.isBlank()) {
            return;
        }
        String type = skinType.trim().toLowerCase(Locale.ROOT);
        String id = skinId.trim().toLowerCase(Locale.ROOT);
        BY_TYPE.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(id);
        if (weight != 0) {
            WEIGHTS.put(type + "/" + id, weight);
        }
    }

    /** 一次性登记一批同类型的 3D 皮肤 ID */
    public static void registerAll(String skinType, Iterable<String> skinIds) {
        if (skinIds == null) {
            return;
        }
        for (String id : skinIds) {
            register(skinType, id);
        }
    }

    /** 把整个皮肤类型标记为 3D（其下所有皮肤都算 3D） */
    public static void registerType(String skinType) {
        if (skinType == null || skinType.isBlank()) {
            return;
        }
        WHOLE_TYPES.add(skinType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 资源探测：主模组自带的 Geo 皮肤没有内容模组替它登记，
     * 由客户端在资源加载完成后调用本方法扫一遍
     * {@code assets/*}{@code /geo/**.geo.json}，把文件名收集成「按 ID 的 3D 集合」。
     * <p>
     * 只在客户端调用；重复调用是幂等的（并发 Set 去重）。
     */
    public static void probeGeoResources(ResourceManager manager) {
        if (manager == null) {
            return;
        }
        try {
            // path 参数是资源目录前缀：匹配到的 ResourceLocation 形如
            // itemskins:geo/pets/mimi.geo.json，取文件名当皮肤 ID 兜底
            Map<ResourceLocation, ?> found = manager.listResources(
                    "geo", loc -> loc.getPath().endsWith(".geo.json"));
            for (ResourceLocation loc : found.keySet()) {
                String path = loc.getPath();
                int slash = path.lastIndexOf('/');
                String file = slash >= 0 ? path.substring(slash + 1) : path;
                if (file.endsWith(".geo.json")) {
                    file = file.substring(0, file.length() - ".geo.json".length());
                }
                if (!file.isEmpty()) {
                    PROBED_IDS.add(file.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
            // 资源探测只是兜底，失败不影响显式登记的判定
        }
    }

    // ─── 查询 ────────────────────────────────────────────────────────────────

    /** 该皮肤是否 3D。 */
    public static boolean is3d(String skinType, String skinId) {
        if (skinId == null || skinId.isBlank()) {
            return false;
        }
        String type = skinType == null ? "" : skinType.trim().toLowerCase(Locale.ROOT);
        String id = skinId.trim().toLowerCase(Locale.ROOT);
        if (WHOLE_TYPES.contains(type)) {
            return true;
        }
        Set<String> ids = BY_TYPE.get(type);
        if (ids != null && ids.contains(id)) {
            return true;
        }
        return PROBED_IDS.contains(id);
    }

    /** 整个皮肤类型是否全部 3D。 */
    public static boolean isWholeType3d(String skinType) {
        return skinType != null && WHOLE_TYPES.contains(skinType.trim().toLowerCase(Locale.ROOT));
    }

    /** 显式登记过的 3D 皮肤（类型 → ID 集合），只读快照，供调试与文档使用。 */
    public static Map<String, Set<String>> registered() {
        Map<String, Set<String>> snapshot = new LinkedHashMap<>();
        BY_TYPE.forEach((type, ids) -> snapshot.put(type, Collections.unmodifiableSet(ids)));
        return Collections.unmodifiableMap(snapshot);
    }

    /** 当前类型下显式登记过的 3D 皮肤数量。 */
    public static int countOf(String skinType) {
        if (skinType == null) {
            return 0;
        }
        Set<String> ids = BY_TYPE.get(skinType.trim().toLowerCase(Locale.ROOT));
        return ids == null ? 0 : ids.size();
    }

    /** 排序权重（没登记过返回 0）。 */
    public static int weight(String skinType, String skinId) {
        if (skinType == null || skinId == null) {
            return 0;
        }
        Integer w = WEIGHTS.get(skinType.trim().toLowerCase(Locale.ROOT)
                + "/" + skinId.trim().toLowerCase(Locale.ROOT));
        return w == null ? 0 : w;
    }

    /** 是否登记过任何 3D 皮肤（皮肤页据此决定要不要显示筛选按钮）。 */
    public static boolean any() {
        if (!WHOLE_TYPES.isEmpty() || !PROBED_IDS.isEmpty()) {
            return true;
        }
        return BY_TYPE.values().stream().anyMatch(s -> !s.isEmpty());
    }
}

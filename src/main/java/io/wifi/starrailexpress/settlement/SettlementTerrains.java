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

package io.wifi.starrailexpress.settlement;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 结算舞台的「地形皮肤」注册表（服务端可用）。
 *
 * <p>舞台方块必须由服务端写入，而客户端注册表在服务端不存在，
 * 因此地形单独有一份注册表：附属模组在 {@code ServerModInitializer}
 * 或公共初始化阶段注册，{@link SettlementManager} 按快照里的皮肤 ID 查表。
 *
 * <p>未注册任何地形时，结算舞台使用 {@code SettlementStage} 内置的中性布置
 * （黑混凝土外壳 + 镜面黑石地板 + 铜色台口），这是默认行为。
 */
public final class SettlementTerrains {

    /**
     * 一种地形：按相对坐标返回该格应放置的方块。
     *
     * <p>返回 {@code null} 表示「这一格不接管」，交回本体默认布置。
     * 坐标系与 {@code SettlementStage} 内部一致：
     * X ∈ [-16, 16]、Y ∈ [0, 14]、Z ∈ [-15, 15]，原点在地板中心。
     */
    @FunctionalInterface
    public interface Terrain {
        @Nullable
        BlockState blockAt(int x, int y, int z);
    }

    private static final Map<String, Terrain> TERRAINS = new LinkedHashMap<>();

    private SettlementTerrains() {
    }

    /**
     * 注册一种地形皮肤。
     *
     * @param skinId 与 {@code ItemSkinManager} 注册的结算皮肤 ID 一致
     */
    public static void register(String skinId, Terrain terrain) {
        if (skinId == null || skinId.isBlank()) {
            throw new IllegalArgumentException("地形皮肤必须有非空 id");
        }
        if (terrain == null) {
            throw new IllegalArgumentException("地形不能为 null: " + skinId);
        }
        TERRAINS.put(skinId, terrain);
    }

    /** 按皮肤 ID 取地形，未注册返回 {@code null}（表示用默认布置）。 */
    @Nullable
    public static Terrain byId(@Nullable String skinId) {
        return skinId == null ? null : TERRAINS.get(skinId);
    }

    /** 已注册数量，供调试与测试断言。 */
    public static int size() {
        return TERRAINS.size();
    }

    /** 清空注册表（仅用于测试）。 */
    public static void clear() {
        TERRAINS.clear();
    }
}

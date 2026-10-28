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

package io.wifi.starrailexpress.settlement.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * 结算皮肤注册表（纯客户端）。
 *
 * <p>本体启动时不注册任何皮肤，所有具体皮肤（含原本内置的末影龙主题）
 * 都由附属模组在客户端初始化阶段调用 {@link #register(SettlementSkin)} 提供。
 * 未注册任何皮肤时结算照常进行，只是没有额外布景。
 *
 * <p>查表一律走 {@link #byId(String)}，未知 ID 返回 {@code null}，
 * 调用方必须容忍 {@code null}（旧快照、皮肤被卸载、玩家换了皮肤包）。
 */
@Environment(EnvType.CLIENT)
public final class SettlementSkins {

    private static final Map<String, SettlementSkin> SKINS = new LinkedHashMap<>();

    private SettlementSkins() {
    }

    /**
     * 注册一款结算皮肤。
     *
     * <p>重复注册同一 ID 会覆盖旧实现，便于资源包热重载后替换。
     *
     * @throws IllegalArgumentException id 为空
     */
    public static void register(SettlementSkin skin) {
        if (skin == null || skin.id() == null || skin.id().isBlank()) {
            throw new IllegalArgumentException("结算皮肤必须有非空 id");
        }
        SKINS.put(skin.id(), skin);
    }

    /** 按皮肤 ID 取实现，未注册返回 {@code null}。 */
    @Nullable
    public static SettlementSkin byId(@Nullable String id) {
        return id == null ? null : SKINS.get(id);
    }

    /** 已注册数量，供调试与测试断言。 */
    public static int size() {
        return SKINS.size();
    }

    /** 清空注册表（仅用于测试与资源重载）。 */
    public static void clear() {
        SKINS.clear();
    }
}

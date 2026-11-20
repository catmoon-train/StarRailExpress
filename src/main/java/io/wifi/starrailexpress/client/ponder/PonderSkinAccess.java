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

package io.wifi.starrailexpress.client.ponder;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 思索玩家模型的皮肤覆盖登记表。
 *
 * <p>PondererLib 创建的玩家模型是普通 {@code RemotePlayer}，皮肤由
 * {@code PlayerRenderer#getTextureLocation} 决定。本项目的
 * {@code PlayerEntityRendererMixin} 会在这个方法里优先返回职业皮肤/疯魔皮肤，
 * 因此皮肤覆盖必须在它之前生效——由 {@link PonderSkinAccessMixin} 在
 * {@code getTextureLocation} 头部查询本表完成。
 *
 * <p>用弱引用键保存，玩家模型被场景回收后登记自动失效，不会泄漏。
 */
public final class PonderSkinAccess {

    private static final Map<AbstractClientPlayer, ResourceLocation> OVERRIDES = new WeakHashMap<>();

    private PonderSkinAccess() {
    }

    /** 为指定玩家模型登记皮肤覆盖。 */
    public static void override(AbstractClientPlayer player, ResourceLocation texture) {
        if (player == null || texture == null) {
            return;
        }
        synchronized (OVERRIDES) {
            OVERRIDES.put(player, texture);
        }
    }

    /** 查询皮肤覆盖；未登记时返回 null。 */
    @Nullable
    public static ResourceLocation textureOf(@Nullable AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        synchronized (OVERRIDES) {
            return OVERRIDES.get(player);
        }
    }

    /** 清空全部登记（资源重载或退出世界时调用）。 */
    public static void clear() {
        synchronized (OVERRIDES) {
            OVERRIDES.clear();
        }
    }
}

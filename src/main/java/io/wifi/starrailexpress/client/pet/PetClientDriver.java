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

package io.wifi.starrailexpress.client.pet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * 随从的客户端驱动器：只做两件事——每 tick 推进运动、断开连接时清缓存。
 * <p>
 * 渲染本身由 sre-skin 侧的 {@code PetRenderLayer} 完成（它通过
 * {@link ClientPetCache#getVisibleSkinId} 与 {@link ClientPetMotion} 读取状态），
 * 因此这里不注册任何渲染钩子，主模组与皮肤模组之间只共享这两个公开入口。
 */
@Environment(EnvType.CLIENT)
public final class PetClientDriver {

    private PetClientDriver() {
    }

    /** 在客户端初始化时调用一次。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPetMotion.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPetCache.clear();
            ClientPetMotion.clear();
        });
    }
}

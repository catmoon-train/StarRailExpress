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

package org.agmas.noellesroles.client.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.content.block.scene.FogZoneBlock;

/**
 * 客户端：判断本地玩家是否处于迷雾区域内（用于关闭本能）。
 */
public final class SceneFogClient {
    private SceneFogClient() {
    }

    /**
     * 防重入标志。调用链为 isInstinctEnabled -> (InstinctMixin) -> isLocalPlayerInFog -> getBlockState，
     * 而读取世界（getBlockState/区块访问）在某些渲染/效果 mixin 路径下会再次回调 isInstinctEnabled，
     * 从而无限递归导致 StackOverflowError。此处以标志位保证嵌套调用直接返回 false，切断递归。
     * 客户端渲染/tick 为单线程，普通静态布尔即可。
     */
    private static boolean computing = false;

    public static boolean isLocalPlayerInFog() {
        if (computing) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        computing = true;
        try {
            BlockPos feet = mc.player.blockPosition();
            if (mc.level.getBlockState(feet).getBlock() instanceof FogZoneBlock) {
                return true;
            }
            BlockPos eye = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            return mc.level.getBlockState(eye).getBlock() instanceof FogZoneBlock;
        } finally {
            computing = false;
        }
    }
}

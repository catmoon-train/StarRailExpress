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

package io.wifi.starrailexpress.compat.sodium;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;

/**
 * 混入接口：由 DefaultShaderInterface 实现，用于向着色器绑定景色偏移 UBO。
 * 参考 wathe 的 SodiumShaderInterface，适配 SRE 的命名空间。
 */
public interface SRESceneryShaderInterface {
    void sre$setSceneryOffsets(GlMutableBuffer buffer);
}
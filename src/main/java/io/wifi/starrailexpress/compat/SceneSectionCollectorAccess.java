package io.wifi.starrailexpress.compat;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

/** 本次 Sodium 收集器的实际访问记录，独立于主画面/阴影共享的区段可见帧。 */
public interface SceneSectionCollectorAccess {
    boolean sre$hasVisited(RenderSection section);
}

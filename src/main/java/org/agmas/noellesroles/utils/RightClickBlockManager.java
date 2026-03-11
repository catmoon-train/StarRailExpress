package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.util.CantRightClickBlocks;

public class RightClickBlockManager {
    public static void init() {
        // 不能交互的方块
        CantRightClickBlocks.CANNOT_INTERACT_IDS.add("supplementaries:doormat");
    }
}

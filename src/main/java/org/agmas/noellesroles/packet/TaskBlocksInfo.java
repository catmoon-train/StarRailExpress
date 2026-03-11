package org.agmas.noellesroles.packet;

import net.minecraft.core.Vec3i;

public class TaskBlocksInfo {
    public Vec3i pos;
    public int type;

    public TaskBlocksInfo(Vec3i pos, int type) {
        this.pos = pos;
        this.type = type;
    }
}
package org.agmas.noellesroles.game.c4;

import java.util.UUID;

public record C4PlacementPreset(float offsetX, float offsetY, float offsetZ,
        float rotationX, float rotationY, float rotationZ, float slant, float scale) {
    public static final C4PlacementPreset DEFAULT =
        new C4PlacementPreset(0.0F, 0.24F, 0.28F, 0.0F, 0.0F, 0.0F, 0.0F, 0.42F);

    public static int indexFor(UUID uuid, int presetCount) {
        if (presetCount <= 1) return 0;
        if (uuid == null) return 0;
        return Math.floorMod(uuid.hashCode(), presetCount);
    }
}

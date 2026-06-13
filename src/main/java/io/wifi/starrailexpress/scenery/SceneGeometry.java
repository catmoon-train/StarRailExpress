package io.wifi.starrailexpress.scenery;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SceneGeometry {
    private SceneGeometry() {
    }

    public static SectionBounds sectionBounds(AABB area) {
        int minX = SectionPos.blockToSectionCoord((int) Math.floor(area.minX));
        int minY = SectionPos.blockToSectionCoord((int) Math.floor(area.minY));
        int minZ = SectionPos.blockToSectionCoord((int) Math.floor(area.minZ));
        int maxX = SectionPos.blockToSectionCoord((int) Math.ceil(area.maxX) - 1);
        int maxY = SectionPos.blockToSectionCoord((int) Math.ceil(area.maxY) - 1);
        int maxZ = SectionPos.blockToSectionCoord((int) Math.ceil(area.maxZ) - 1);
        return new SectionBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static AABB expandedArea(AABB area) {
        return sectionBounds(area).toBlockBox();
    }

    public static boolean isSectionAligned(AABB area) {
        AABB expanded = expandedArea(area);
        return area.minX == expanded.minX && area.minY == expanded.minY && area.minZ == expanded.minZ
                && area.maxX == expanded.maxX && area.maxY == expanded.maxY && area.maxZ == expanded.maxZ;
    }

    public static Vec3 baseOffset(AABB sceneArea, AABB playArea) {
        return baseOffset(sceneArea, playArea, Vec3.ZERO);
    }

    public static Vec3 baseOffset(AABB sceneArea, AABB playArea, Vec3 displayOffset) {
        return playArea.getCenter().subtract(sceneArea.getCenter()).add(displayOffset);
    }

    public static AABB targetArea(AABB sceneArea, AABB playArea, Vec3 displayOffset) {
        return sceneArea.move(baseOffset(sceneArea, playArea, displayOffset));
    }

    public static Vec3 renderOffset(AABB sceneArea, AABB playArea, AreasWorldComponent.ScrollAxis axis,
            Vec3 displayOffset, double scrollDistance) {
        Vec3 base = baseOffset(sceneArea, playArea, displayOffset);
        if (axis == AreasWorldComponent.ScrollAxis.NONE) {
            return base;
        }

        AABB expanded = expandedArea(sceneArea);
        double period = switch (axis) {
            case X -> expanded.getXsize();
            case Y -> expanded.getYsize();
            case Z -> expanded.getZsize();
            case NONE -> 0.0D;
        };
        if (period <= 0.0D) {
            return base;
        }

        // Use [0, period) so the current and previous copies always meet. At the
        // wrap point the two identical copies exchange roles without exposing void.
        double dynamic = positiveModulo(scrollDistance, period);
        return switch (axis) {
            case X -> base.add(dynamic, 0.0D, 0.0D);
            case Y -> base.add(0.0D, dynamic, 0.0D);
            case Z -> base.add(0.0D, 0.0D, dynamic);
            case NONE -> base;
        };
    }

    public static Vec3 renderOffset(AABB sceneArea, AABB playArea, AreasWorldComponent.ScrollAxis axis,
            double scrollDistance, Vec3 ignoredCameraPosition) {
        return renderOffset(sceneArea, playArea, axis, Vec3.ZERO, scrollDistance);
    }

    public static boolean containsSection(AABB area, int sectionX, int sectionY, int sectionZ) {
        return sectionBounds(area).contains(sectionX, sectionY, sectionZ);
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    public record SectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public int sectionCount() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        public int chunkCount() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        public AABB toBlockBox() {
            return new AABB(
                    SectionPos.sectionToBlockCoord(minX),
                    SectionPos.sectionToBlockCoord(minY),
                    SectionPos.sectionToBlockCoord(minZ),
                    SectionPos.sectionToBlockCoord(maxX + 1),
                    SectionPos.sectionToBlockCoord(maxY + 1),
                    SectionPos.sectionToBlockCoord(maxZ + 1));
        }
    }
}

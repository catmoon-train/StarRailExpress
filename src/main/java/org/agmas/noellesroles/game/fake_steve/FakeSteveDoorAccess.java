package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Identifies blocks that the possessed body may open while following a route. */
final class FakeSteveDoorAccess {
    private FakeSteveDoorAccess() {
    }

    static boolean isOpenablePassage(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof SmallDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock
                // Covers registered glass/sliding doors and future door implementations
                // that expose the standard open property without subclassing vanilla DoorBlock.
                || state.hasProperty(BlockStateProperties.OPEN);
    }

    static boolean isOpen(BlockState state) {
        return !state.hasProperty(BlockStateProperties.OPEN)
                || state.getValue(BlockStateProperties.OPEN);
    }

    static boolean isInsideApproachCorridor(double bodyX, double bodyZ,
                                            double routeX, double routeZ,
                                            double doorX, double doorZ) {
        double routeDx = routeX - bodyX;
        double routeDz = routeZ - bodyZ;
        double routeLengthSqr = routeDx * routeDx + routeDz * routeDz;
        if (routeLengthSqr < 1.0E-4D) {
            double dx = doorX - bodyX;
            double dz = doorZ - bodyZ;
            return dx * dx + dz * dz <= 1.0D;
        }
        double projection = ((doorX - bodyX) * routeDx + (doorZ - bodyZ) * routeDz)
                / routeLengthSqr;
        if (projection < -0.15D || projection > 1.35D) {
            return false;
        }
        double closestX = bodyX + routeDx * Math.max(0.0D, Math.min(1.0D, projection));
        double closestZ = bodyZ + routeDz * Math.max(0.0D, Math.min(1.0D, projection));
        double distanceX = doorX - closestX;
        double distanceZ = doorZ - closestZ;
        return distanceX * distanceX + distanceZ * distanceZ <= 1.0D;
    }
}

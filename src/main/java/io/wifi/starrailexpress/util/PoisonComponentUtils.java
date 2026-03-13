package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.block_entity.TrimmedBedBlockEntity;
import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PoisonComponentUtils {
    public static float getFovMultiplier(float tickDelta, StarPlayerPoisonComponent poisonComponent) {
        if (!poisonComponent.pulsing) return 1f;

        poisonComponent.pulseProgress += tickDelta * 0.1f;

        if (poisonComponent.pulseProgress >= 1f) {
            poisonComponent.pulsing = false;
            poisonComponent.pulseProgress = 0f;
            return 1f;
        }

        float maxAmplitude = 0.1f;
        float minAmplitude = 0.025f;

        float result = getResult(poisonComponent, minAmplitude, maxAmplitude);

        return result;
    }

    private static float getResult(StarPlayerPoisonComponent poisonComponent, float minAmplitude, float maxAmplitude) {
        float amplitude = minAmplitude + (maxAmplitude - minAmplitude) * (1f - ((float) poisonComponent.poisonTicks / 1200f));

        float result;

        if (poisonComponent.pulseProgress < 0.25f) {
            result = 1f - amplitude * (float) Math.sin(Math.PI * (poisonComponent.pulseProgress / 0.25f));
        } else if (poisonComponent.pulseProgress < 0.5f) {
            result = 1f - amplitude * (float) Math.sin(Math.PI * ((poisonComponent.pulseProgress - 0.25f) / 0.25f));
        } else {
            result = 1f;
        }
        return result;
    }

    public static void bedPoison(ServerPlayer player) {
        Level world = player.getCommandSenderWorld();
        BlockPos bedPos = player.blockPosition();

        TrimmedBedBlockEntity blockEntity = findHeadInBoxWithObstacles(world, bedPos);
        if (blockEntity == null) return;

        if (!world.isClientSide) {
            blockEntity.setHasScorpion(false, null);
            int poisonTicks = StarPlayerPoisonComponent.KEY.get(player).poisonTicks;

            UUID poisoner = blockEntity.getPoisoner();

            if (poisonTicks == -1) {
                StarPlayerPoisonComponent.KEY.get(player).setPoisonTicks(
                        world.getRandom().nextIntBetweenInclusive(StarPlayerPoisonComponent.clampTime.getA(), StarPlayerPoisonComponent.clampTime.getB()),
                        poisoner
                );
            } else {
                StarPlayerPoisonComponent.KEY.get(player).setPoisonTicks(
                        Mth.clamp(poisonTicks - world.getRandom().nextIntBetweenInclusive(100, 300), 0, StarPlayerPoisonComponent.clampTime.getB()),
                        poisoner
                );
            }

            PacketTracker.sendToClient(player, new PoisonOverlayPayload());
        }
    }

    private static TrimmedBedBlockEntity findHeadInBoxWithObstacles(Level world, BlockPos centerPos) {
        int radius = 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    TrimmedBedBlockEntity entity = resolveHead(world, pos);
                    if (entity != null && entity.hasScorpion()) {
                        if (isLineClear(world, centerPos, pos)) {
                            return entity;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isLineClear(Level world, BlockPos start, BlockPos end) {
        // Use simple 3D Bresenham line algorithm
        int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ();
        int x1 = end.getX(), y1 = end.getY(), z1 = end.getZ();

        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err1, err2;

        int ax = 2 * dx, ay = 2 * dy, az = 2 * dz;

        if (dx >= dy && dx >= dz) {
            err1 = ay - dx;
            err2 = az - dx;
            while (x0 != x1) {
                x0 += sx;
                if (err1 > 0) {
                    y0 += sy;
                    err1 -= 2 * dx;
                }
                if (err2 > 0) {
                    z0 += sz;
                    err2 -= 2 * dx;
                }
                err1 += ay;
                err2 += az;

                if (isBlocking(world, new BlockPos(x0, y0, z0))) return false;
            }
        } else if (dy >= dx && dy >= dz) {
            err1 = ax - dy;
            err2 = az - dy;
            while (y0 != y1) {
                y0 += sy;
                if (err1 > 0) {
                    x0 += sx;
                    err1 -= 2 * dy;
                }
                if (err2 > 0) {
                    z0 += sz;
                    err2 -= 2 * dy;
                }
                err1 += ax;
                err2 += az;

                if (isBlocking(world, new BlockPos(x0, y0, z0))) return false;
            }
        } else {
            err1 = ay - dz;
            err2 = ax - dz;
            while (z0 != z1) {
                z0 += sz;
                if (err1 > 0) {
                    y0 += sy;
                    err1 -= 2 * dz;
                }
                if (err2 > 0) {
                    x0 += sx;
                    err2 -= 2 * dz;
                }
                err1 += ay;
                err2 += ax;

                if (isBlocking(world, new BlockPos(x0, y0, z0))) return false;
            }
        }

        return true;
    }

    private static boolean isBlocking(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !(state.getBlock() instanceof BedBlock);
    }


    /**
     * Resolve a bed block (head or foot) into its head entity.
     */
    private static TrimmedBedBlockEntity resolveHead(Level world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof TrimmedBedBlockEntity entity)) return null;

        BedPart part = world.getBlockState(pos).getValue(BedBlock.PART);
        Direction facing = world.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING);

        if (part == BedPart.HEAD) return entity;

        if (part == BedPart.FOOT) {
            BlockPos headPos = pos.relative(facing);
            if (world.getBlockEntity(headPos) instanceof TrimmedBedBlockEntity headEntity &&
                    world.getBlockState(headPos).getValue(BedBlock.PART) == BedPart.HEAD) return headEntity;
        }

        return null;
    }


    public record PoisonOverlayPayload() implements CustomPacketPayload {
        public static final Type<PoisonOverlayPayload> ID =
                new Type<>(SRE.id("poisoned_text"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PoisonOverlayPayload> CODEC =
                StreamCodec.unit(new PoisonOverlayPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        @Environment(EnvType.CLIENT)
        public static class Receiver implements ClientPlayNetworking.PlayPayloadHandler<PoisonOverlayPayload> {
            @Override
            public void receive(@NotNull PoisonOverlayPayload payload, ClientPlayNetworking.@NotNull Context context) {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> client.gui.setOverlayMessage(Component.translatable("game.player.stung"), false));
            }
        }
    }
}
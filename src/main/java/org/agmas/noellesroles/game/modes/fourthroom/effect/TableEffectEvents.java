package org.agmas.noellesroles.game.modes.fourthroom.effect;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class TableEffectEvents {

    private static final Map<ResourceLocation, Function<FriendlyByteBuf, EffectEvent>> DECODERS = new HashMap<>();

    static {
        register(CardMotion.ID, CardMotion::decode);
        register(Pulse.ID, Pulse::decode);
        register(Banner.ID, Banner::decode);
        register(CameraFocus.ID, CameraFocus::decode);
    }

    private TableEffectEvents() {
    }

    public static void encode(FriendlyByteBuf buf, EffectEvent event) {
        switch (event) {
            case CardMotion motion -> {
                buf.writeResourceLocation(CardMotion.ID);
                motion.encode(buf);
            }
            case Pulse pulse -> {
                buf.writeResourceLocation(Pulse.ID);
                pulse.encode(buf);
            }
            case Banner banner -> {
                buf.writeResourceLocation(Banner.ID);
                banner.encode(buf);
            }
            case CameraFocus focus -> {
                buf.writeResourceLocation(CameraFocus.ID);
                focus.encode(buf);
            }
            default -> throw new IllegalArgumentException("Unsupported fourth room effect type: " + event.getClass().getName());
        }
    }

    public static EffectEvent decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Function<FriendlyByteBuf, EffectEvent> decoder = DECODERS.get(id);
        if (decoder == null) {
            throw new IllegalArgumentException("Unknown fourth room effect id: " + id);
        }
        return decoder.apply(buf);
    }

    private static void register(ResourceLocation id, Function<FriendlyByteBuf, EffectEvent> decoder) {
        DECODERS.put(id, decoder);
    }

    @Nullable
    private static FourthRoomTableBlockEntity getTable(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(origin);
        if (blockEntity instanceof FourthRoomTableBlockEntity table) {
            return table;
        }
        return null;
    }

    public enum TableAnchor {
        DRAW_PILE(-0.65D, -0.80D),
        DISCARD_PILE(0.65D, -0.80D),
        CENTER(0.00D, 0.00D),
        SLOT_A(-1.18D, 0.10D),
        SLOT_B(1.18D, 0.10D);

        private final double localX;
        private final double localZ;

        TableAnchor(double localX, double localZ) {
            this.localX = localX;
            this.localZ = localZ;
        }

        public static TableAnchor fromSeatIndex(int seatIndex) {
            return seatIndex <= 0 ? SLOT_A : SLOT_B;
        }

        public double localX() {
            return localX;
        }

        public double localZ() {
            return localZ;
        }

        public Vec3 localOffset(Direction facing) {
            Direction resolvedFacing = facing == null ? Direction.NORTH : facing;
            double worldX;
            double worldZ;
            switch (resolvedFacing) {
                case SOUTH -> {
                    worldX = -localX;
                    worldZ = localZ;
                }
                case EAST -> {
                    worldX = localZ;
                    worldZ = localX;
                }
                case WEST -> {
                    worldX = -localZ;
                    worldZ = -localX;
                }
                case NORTH -> {
                    worldX = localX;
                    worldZ = -localZ;
                }
                default -> {
                    worldX = localX;
                    worldZ = localZ;
                }
            }
            return new Vec3(worldX, 0.0D, worldZ);
        }

        public Vec3 worldPos(BlockPos origin, Direction facing) {
            Vec3 offset = localOffset(facing);
            return new Vec3(origin.getX() + 0.5D + offset.x, origin.getY() + 0.94D, origin.getZ() + 0.5D + offset.z);
        }
    }

    public record CardMotion(long timeOffset, String label, boolean gold, TableAnchor from, TableAnchor to, int color,
                             long durationMs) implements EffectEvent {
        public static final ResourceLocation ID = SRE.id("fourth_room_table_card_motion");

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(timeOffset);
            buf.writeUtf(label);
            buf.writeBoolean(gold);
            buf.writeEnum(from);
            buf.writeEnum(to);
            buf.writeInt(color);
            buf.writeVarLong(durationMs);
        }

        public static CardMotion decode(FriendlyByteBuf buf) {
            return new CardMotion(
                    buf.readVarLong(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readEnum(TableAnchor.class),
                    buf.readEnum(TableAnchor.class),
                    buf.readInt(),
                    buf.readVarLong());
        }

        @Override
        public void executeClient(BlockPos origin) {
            FourthRoomTableBlockEntity table = getTable(origin);
            if (table != null) {
                table.startCardAnimation(label, gold, from, to, color, durationMs);
            }
        }
    }

    public record Pulse(long timeOffset, TableAnchor anchor, int color, float intensity, long durationMs)
            implements EffectEvent {
        public static final ResourceLocation ID = SRE.id("fourth_room_table_pulse");

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(timeOffset);
            buf.writeEnum(anchor);
            buf.writeInt(color);
            buf.writeFloat(intensity);
            buf.writeVarLong(durationMs);
        }

        public static Pulse decode(FriendlyByteBuf buf) {
            return new Pulse(
                    buf.readVarLong(),
                    buf.readEnum(TableAnchor.class),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readVarLong());
        }

        @Override
        public void executeClient(BlockPos origin) {
            FourthRoomTableBlockEntity table = getTable(origin);
            if (table != null) {
                table.startPulse(anchor, color, intensity, durationMs);
            }
        }
    }

    public record Banner(long timeOffset, String text, int color, long durationMs) implements EffectEvent {
        public static final ResourceLocation ID = SRE.id("fourth_room_table_banner");

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(timeOffset);
            buf.writeUtf(text);
            buf.writeInt(color);
            buf.writeVarLong(durationMs);
        }

        public static Banner decode(FriendlyByteBuf buf) {
            return new Banner(
                    buf.readVarLong(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readVarLong());
        }

        @Override
        public void executeClient(BlockPos origin) {
            FourthRoomTableBlockEntity table = getTable(origin);
            if (table != null) {
                table.showBanner(text, color, durationMs);
            }
        }
    }

    public record CameraFocus(long timeOffset, TableAnchor anchor, long durationMs, float strength,
                              boolean cinematic, int edgeColor) implements EffectEvent {
        public static final ResourceLocation ID = SRE.id("fourth_room_table_camera_focus");

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(timeOffset);
            buf.writeEnum(anchor);
            buf.writeVarLong(durationMs);
            buf.writeFloat(Mth.clamp(strength, 0.0F, 1.0F));
            buf.writeBoolean(cinematic);
            buf.writeInt(edgeColor);
        }

        public static CameraFocus decode(FriendlyByteBuf buf) {
            return new CameraFocus(
                    buf.readVarLong(),
                    buf.readEnum(TableAnchor.class),
                    buf.readVarLong(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readInt());
        }

        @Override
        public void executeClient(BlockPos origin) {
            FourthRoomCameraDirector.focusTable(origin, anchor, durationMs, strength, cinematic, edgeColor);
        }
    }
}
package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class TicketPayload {
    public record OpenOfficeConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenOfficeConfig> TYPE = new Type<>(SRE.id("ticket_office_open_config"));
        public static final StreamCodec<FriendlyByteBuf, OpenOfficeConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenOfficeConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenOfficeConfig::data,
                OpenOfficeConfig::new);

        @Override
        public Type<OpenOfficeConfig> type() {
            return TYPE;
        }
    }

    public record OpenOfficeShop(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenOfficeShop> TYPE = new Type<>(SRE.id("ticket_office_open_shop"));
        public static final StreamCodec<FriendlyByteBuf, OpenOfficeShop> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenOfficeShop::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenOfficeShop::data,
                OpenOfficeShop::new);

        @Override
        public Type<OpenOfficeShop> type() {
            return TYPE;
        }
    }

    public record SaveOfficeConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SaveOfficeConfig> TYPE = new Type<>(SRE.id("ticket_office_save_config"));
        public static final StreamCodec<FriendlyByteBuf, SaveOfficeConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SaveOfficeConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, SaveOfficeConfig::data,
                SaveOfficeConfig::new);

        @Override
        public Type<SaveOfficeConfig> type() {
            return TYPE;
        }
    }

    public record BuyTicket(BlockPos pos) implements CustomPacketPayload {
        public static final Type<BuyTicket> TYPE = new Type<>(SRE.id("ticket_office_buy"));
        public static final StreamCodec<FriendlyByteBuf, BuyTicket> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BuyTicket::pos,
                BuyTicket::new);

        @Override
        public Type<BuyTicket> type() {
            return TYPE;
        }
    }
}

package io.wifi.starrailexpress.content.vote.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.vote.ClientPlayerOption;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public record VoteSyncS2CPacket(
        boolean active,
        Component title,
        boolean hasOptions,
        List<VoteOption> options,
        long endTick,
        boolean showResults,
        Map<Integer, Integer> results,
        int totalVotes,
        boolean allowReVote,
        int maxSelectCount) implements CustomPacketPayload {

    public static final Type<VoteSyncS2CPacket> TYPE = new Type<>(SRE.id("vote_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoteSyncS2CPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.active);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.title);
                buf.writeBoolean(packet.hasOptions);
                if (packet.hasOptions) {
                    buf.writeVarInt(packet.options.size());
                    for (VoteOption opt : packet.options)
                        writeOption(buf, opt);
                }
                buf.writeVarLong(packet.endTick);
                buf.writeBoolean(packet.showResults);
                if (packet.showResults) {
                    buf.writeMap(packet.results,
                            (b, k) -> b.writeVarInt(k),
                            (b, v) -> b.writeVarInt(v));
                    buf.writeVarInt(packet.totalVotes);
                }
                buf.writeBoolean(packet.allowReVote);
                buf.writeVarInt(packet.maxSelectCount);
            },
            buf -> {
                boolean active = buf.readBoolean();
                Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                boolean hasOptions = buf.readBoolean();
                List<VoteOption> options = List.of();
                if (hasOptions) {
                    int size = buf.readVarInt();
                    options = new ArrayList<>(size);
                    for (int i = 0; i < size; i++)
                        options.add(readOption(buf));
                }
                long endTick = buf.readVarLong();
                boolean show = buf.readBoolean();
                Map<Integer, Integer> results = Map.of();
                int totalVotes = 0;
                if (show) {
                    results = buf.readMap(b -> b.readVarInt(), b -> b.readVarInt());
                    totalVotes = buf.readVarInt();
                }
                boolean allowRe = buf.readBoolean();
                int maxSelect = buf.readVarInt();
                return new VoteSyncS2CPacket(active, title, hasOptions, options, endTick, show, results, totalVotes,
                        allowRe, maxSelect);
            });

    // ── 工厂方法 ──────────────────────────────────────
    public static VoteSyncS2CPacket fullSync(VoteSession session) {
        long endTick = session.isPaused() ? -1 : session.getEndTick();
        return new VoteSyncS2CPacket(true, session.getTitle(), true, session.getOptions(),
                endTick, session.isShowResults(), session.getIndexResults(), session.getTotalVotes(),
                session.isAllowReVote(), session.getMaxSelectCount());
    }

    public static VoteSyncS2CPacket update(VoteSession session) {
        long endTick = session.isPaused() ? -1 : session.getEndTick();
        return new VoteSyncS2CPacket(true, session.getTitle(), false, List.of(),
                endTick, session.isShowResults(), session.getIndexResults(), session.getTotalVotes(),
                session.isAllowReVote(), session.getMaxSelectCount());
    }

    public static VoteSyncS2CPacket end() {
        return new VoteSyncS2CPacket(false, Component.empty(), false, List.of(), 0, false, Map.of(), 0, false, 1);
    }

    // ── 序列化工具（不变） ─────────────────────────────
    private static final byte TYPE_TEXT = 0;
    private static final byte TYPE_PLAYER = 1;
    private static final byte TYPE_ITEM = 2;

    private static void writeOption(RegistryFriendlyByteBuf buf, VoteOption option) {

        Component writeData = Component.empty();
        if (option.description() != null) {
            writeData = option.description();
        }
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, writeData);

        if (option.isPlayer()) {
            buf.writeByte(TYPE_PLAYER);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, option.display());
            buf.writeUUID(((VoteOption.PlayerOption) option).uuid());
        } else if (option.isItem()) {
            buf.writeByte(TYPE_ITEM);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, option.display());
            ItemStack stack = ((VoteOption.ItemOption) option).stack();
            CompoundTag tag = (CompoundTag) stack.save(buf.registryAccess());
            buf.writeNbt(tag);
        } else {
            buf.writeByte(TYPE_TEXT);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, option.display());
        }
    }

    private static VoteOption readOption(RegistryFriendlyByteBuf buf) {
        Component description = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        byte type = buf.readByte();
        Component display = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        return switch (type) {
            case TYPE_PLAYER -> {
                UUID uid = buf.readUUID();
                yield new ClientPlayerOption(display, uid, description);
            }
            case TYPE_ITEM -> {
                CompoundTag tag = buf.readNbt();
                ItemStack stack = ItemStack.parseOptional(buf.registryAccess(), tag);
                yield VoteOption.item(stack, description);
            }
            default -> VoteOption.text(display, description);
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
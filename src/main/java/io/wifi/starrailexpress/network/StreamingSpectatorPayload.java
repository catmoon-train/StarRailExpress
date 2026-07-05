package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record StreamingSpectatorPayload(boolean active, UUID targetUuid, int cameraMode, List<ItemStack> inventory,
        int selectedHotbarSlot)
        implements CustomPacketPayload {
    public static final ResourceLocation PACKET_ID = SRE.id("streaming_spectator");
    public static final Type<StreamingSpectatorPayload> ID = new Type<>(PACKET_ID);
    public static final int HOTBAR_SLOTS = 9;
    public static final int CAMERA_NONE = 0;
    public static final int CAMERA_FIRST_PERSON = 1;
    public static final int CAMERA_THIRD_PERSON_BACK = 2;

    public static final StreamCodec<RegistryFriendlyByteBuf, StreamingSpectatorPayload> CODEC = StreamCodec.ofMember(
            StreamingSpectatorPayload::write,
            StreamingSpectatorPayload::read);

    public StreamingSpectatorPayload {
        inventory = copyInventory(inventory);
    }

    public static StreamingSpectatorPayload stop() {
        return new StreamingSpectatorPayload(false, null, CAMERA_NONE, List.of(), -1);
    }

    public static StreamingSpectatorPayload waiting() {
        return new StreamingSpectatorPayload(true, null, CAMERA_NONE, List.of(), -1);
    }

    public static StreamingSpectatorPayload watch(UUID targetUuid, int cameraMode, List<ItemStack> hotbar,
            int selectedHotbarSlot) {
        return new StreamingSpectatorPayload(true, targetUuid, cameraMode, hotbar, selectedHotbarSlot);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buf.writeUUID(targetUuid);
        }
        buf.writeVarInt(cameraMode);
        buf.writeVarInt(selectedHotbarSlot);
        buf.writeVarInt(inventory.size());
        for (ItemStack stack : inventory) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    private static StreamingSpectatorPayload read(RegistryFriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        UUID targetUuid = buf.readBoolean() ? buf.readUUID() : null;
        int cameraMode = buf.readVarInt();
        int selectedHotbarSlot = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Invalid streaming spectator hotbar size: " + size);
        }
        List<ItemStack> inventory = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            inventory.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new StreamingSpectatorPayload(active, targetUuid, cameraMode, inventory, selectedHotbarSlot);
    }

    private static List<ItemStack> copyInventory(List<ItemStack> inventory) {
        if (inventory == null || inventory.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(Math.min(inventory.size(), HOTBAR_SLOTS));
        for (int i = 0; i < inventory.size() && i < HOTBAR_SLOTS; i++) {
            ItemStack stack = inventory.get(i);
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

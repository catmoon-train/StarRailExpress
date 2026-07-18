package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvPart;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsRvPartItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

/**
 * 客户端→服务端：房车控制台操作。
 * {@code action}：0=安装配件、1=卸下配件、2=升级、3=选座上车。{@code partOrdinal} 安装/卸下用，选座时为座位 index (0..3)。
 */
public record RvConsoleActionC2SPacket(int entityId, int action, int partOrdinal)
        implements CustomPacketPayload {

    public static final int ACTION_INSTALL = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_UPGRADE = 2;
    public static final int ACTION_BOARD = 3;

    public static final Type<RvConsoleActionC2SPacket> ID = new Type<>(Noellesroles.id("rv_console_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RvConsoleActionC2SPacket> CODEC =
            StreamCodec.ofMember(RvConsoleActionC2SPacket::encode, RvConsoleActionC2SPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(action);
        buf.writeVarInt(partOrdinal);
    }

    private static RvConsoleActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RvConsoleActionC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RvConsoleActionC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        Entity entity = player.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof SixtySecondsRvEntity rv) || !rv.canUse(player)) {
            return;
        }
        switch (payload.action()) {
            case ACTION_UPGRADE -> {
                if (rv.tryUpgrade(player)) {
                    feedback(player, "message.noellesroles.sixty_seconds.rv_upgrade_ok",
                            ChatFormatting.GREEN, rv.upgradeLevel());
                } else {
                    if (rv.upgradeLevel() >= SixtySecondsRvEntity.MAX_UPGRADE_LEVEL) {
                        feedback(player, "message.noellesroles.sixty_seconds.rv_upgrade_max", ChatFormatting.GRAY);
                    } else {
                        feedback(player, "message.noellesroles.sixty_seconds.rv_upgrade_no_materials",
                                ChatFormatting.RED);
                    }
                }
            }
            case ACTION_BOARD -> boardSeat(player, rv, payload.partOrdinal());
            case ACTION_INSTALL -> install(player, rv, part(payload.partOrdinal()));
            case ACTION_REMOVE -> remove(player, rv, part(payload.partOrdinal()));
            default -> {
            }
        }
    }

    /** 选座上车：seatIndex 0..3（0/1=前座，2/3=车顶座）。 */
    private static void boardSeat(ServerPlayer player, SixtySecondsRvEntity rv, int seatIndex) {
        if (rv.tryBoardSeat(player, seatIndex)) {
            feedback(player, "message.noellesroles.sixty_seconds.rv_board_seat_ok",
                    ChatFormatting.GREEN, seatIndex + 1);
        } else {
            feedback(player, "message.noellesroles.sixty_seconds.rv_board_seat_fail",
                    ChatFormatting.RED, seatIndex + 1);
        }
    }

    private static void install(ServerPlayer player, SixtySecondsRvEntity rv, SixtySecondsRvPart part) {
        if (part == null || rv.hasPart(part)) {
            return;
        }
        if (rv.installedPartCount() >= rv.equipmentSlotCount()) {
            feedback(player, "message.noellesroles.sixty_seconds.rv_slots_full", ChatFormatting.RED);
            return;
        }
        int slot = findPartSlot(player, part);
        if (slot < 0 && !player.isCreative()) {
            return; // 没有对应配件物品
        }
        if (rv.installPart(part)) {
            if (slot >= 0 && !player.isCreative()) {
                player.getInventory().getItem(slot).shrink(1);
            }
        }
    }

    private static void remove(ServerPlayer player, SixtySecondsRvEntity rv, SixtySecondsRvPart part) {
        if (part == null || !rv.hasPart(part)) {
            return;
        }
        if (rv.removePart(part)) {
            ItemStack stack = new ItemStack(ModItems.RV_PART_ITEMS.get(part));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** 找到玩家背包里第一枚对应配件物品的槽位；没有返回 -1。 */
    private static int findPartSlot(ServerPlayer player, SixtySecondsRvPart part) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SixtySecondsRvPartItem partItem && partItem.part() == part) {
                return i;
            }
        }
        return -1;
    }

    private static SixtySecondsRvPart part(int ordinal) {
        SixtySecondsRvPart[] values = SixtySecondsRvPart.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static void feedback(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }
}

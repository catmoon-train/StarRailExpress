package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 投票选项数据模型。
 * <p>
 * 支持三种类型：
 * <ul>
 *   <li>{@link Type#TEXT} - 纯文本选项</li>
 *   <li>{@link Type#PLAYER} - 以玩家为选项（显示玩家名和头像）</li>
 *   <li>{@link Type#ITEM} - 以物品为选项（显示物品图标和名称）</li>
 * </ul>
 */
public class VoteOption {

    public enum Type { TEXT, PLAYER, ITEM }

    private final int index;
    private final Type type;
    /** TEXT: 文本内容；PLAYER: 玩家名；ITEM: 物品显示名 */
    private final String label;
    /** 仅 PLAYER 类型有效 */
    private final UUID playerUuid;
    /** 仅 ITEM 类型有效 */
    private final ItemStack item;

    private VoteOption(int index, Type type, String label, UUID playerUuid, ItemStack item) {
        this.index = index;
        this.type = type;
        this.label = label;
        this.playerUuid = playerUuid;
        this.item = item != null ? item : ItemStack.EMPTY;
    }

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    public static VoteOption ofText(int index, String text) {
        return new VoteOption(index, Type.TEXT, text, null, ItemStack.EMPTY);
    }

    public static VoteOption ofPlayer(int index, ServerPlayer player) {
        return ofPlayer(index, player.getName().getString(), player.getUUID());
    }

    public static VoteOption ofPlayer(int index, String name, UUID uuid) {
        return new VoteOption(index, Type.PLAYER, name, uuid, ItemStack.EMPTY);
    }

    public static VoteOption ofItem(int index, ItemStack item) {
        return new VoteOption(index, Type.ITEM, item.getHoverName().getString(), null, item.copy());
    }

    // ── 访问器 ────────────────────────────────────────────────────────────────

    public int getIndex() { return index; }
    public Type getType() { return type; }
    public String getLabel() { return label; }
    public UUID getPlayerUuid() { return playerUuid; }
    public ItemStack getItem() { return item; }

    // ── 网络序列化 ────────────────────────────────────────────────────────────

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(index);
        buf.writeEnum(type);
        buf.writeUtf(label);
        switch (type) {
            case PLAYER -> buf.writeUUID(playerUuid != null ? playerUuid : new UUID(0, 0));
            case ITEM   -> ItemStack.STREAM_CODEC.encode(buf, item);
            default     -> {} // TEXT: 无额外数据
        }
    }

    public static VoteOption decode(RegistryFriendlyByteBuf buf) {
        int index     = buf.readVarInt();
        Type type     = buf.readEnum(Type.class);
        String label  = buf.readUtf();
        return switch (type) {
            case TEXT   -> new VoteOption(index, type, label, null, ItemStack.EMPTY);
            case PLAYER -> {
                UUID uuid = buf.readUUID();
                yield new VoteOption(index, type, label, uuid, ItemStack.EMPTY);
            }
            case ITEM   -> {
                ItemStack item = ItemStack.STREAM_CODEC.decode(buf);
                yield new VoteOption(index, type, label, null, item);
            }
        };
    }
}

package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.content.vote.VoteManager.VoteBuilder;

/**
 * 投票中的一个选项，可以是玩家、文本或物品。
 * 每个选项都有一个 {@link #resultId()} 用于在结果中标识该选项（替换数字索引）。
 */
public interface VoteOption {

    Component display();

    Component description();

    ResourceLocation typeId();

    boolean isPlayer();

    boolean isItem();

    /**
     * 结果映射中使用的唯一标识符。
     * 默认为选项的数字索引（由 {@link VoteBuilder} 自动分配）。
     */
    String resultId();

    // ── 具体实现 ───────────────────────────────────────────

    class PlayerOption implements VoteOption {
        private final Component displayName;
        private final UUID player;
        private final String resultId;
        private final Component description;

        public PlayerOption(Component text, UUID uuid, String resultId, @Nullable Component desc) {
            this.player = uuid;
            this.displayName = text;
            this.resultId = resultId;
            this.description = desc;
        }

        public PlayerOption(Component text, UUID uuid) {
            this(text, uuid, "", null); // 空字符串表示由 builder 后分配
        }

        /** @deprecated 请使用 {@link #player(Component, UUID)} */
        @Deprecated
        public PlayerOption(UUID player) {
            this(Component.literal(player.toString()), player);
        }

        public PlayerOption(Player player, String resultId) {
            this(player.getDisplayName(), player.getUUID(), resultId, null);
        }

        public PlayerOption(Player player, String resultId, Component description) {
            this(player.getDisplayName(), player.getUUID(), resultId, description);
        }

        public PlayerOption(Player player) {
            this(player.getDisplayName(), player.getUUID(), player.getGameProfile().getName(), null);
        }

        public UUID uuid() {
            return player;
        }

        @Override
        public Component display() {
            return displayName;
        }

        @Override
        public ResourceLocation typeId() {
            return ResourceLocation.withDefaultNamespace("player");
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public boolean isItem() {
            return false;
        }

        @Override
        public String resultId() {
            return resultId;
        }

        @Override
        public Component description() {
            return this.description;
        }
    }

    record TextOption(Component text, ResourceLocation id, String resultId, @Nullable Component description)
            implements VoteOption {
        public TextOption(Component text) {
            this(text, ResourceLocation.withDefaultNamespace("text"), "", null);
        }

        public TextOption(Component text, String resultId) {
            this(text, ResourceLocation.withDefaultNamespace("text"), resultId, null);
        }

        public TextOption(Component text, String resultId, Component description) {
            this(text, ResourceLocation.withDefaultNamespace("text"), resultId, description);
        }

        @Override
        public Component display() {
            return text;
        }

        @Override
        public ResourceLocation typeId() {
            return id;
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public boolean isItem() {
            return false;
        }

        @Override
        public String resultId() {
            return resultId;
        }
    }

    record ItemOption(ItemStack stack, String resultId, Component description) implements VoteOption {
        public ItemOption(ItemStack stack) {
            this(stack, "", null);
        }

        public ItemOption(ItemStack stack, Component description) {
            this(stack, "", description);
        }

        @Override
        public Component display() {
            return stack.getHoverName();
        }

        @Override
        public ResourceLocation typeId() {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public boolean isItem() {
            return true;
        }

        @Override
        public String resultId() {
            return resultId;
        }
    }

    // ── 工厂方法 ──────────────────────────────────────────
    static VoteOption player(Player player) {
        return new PlayerOption(player);
    }

    static VoteOption player(Player player, Component desc) {
        return new PlayerOption(player, "", desc);
    }

    static VoteOption player(Player player, String id, Component desc) {
        return new PlayerOption(player, id, desc);
    }

    static VoteOption player(Player player, String id) {
        return new PlayerOption(player, id);
    }

    static VoteOption player(Component text, UUID uuid) {
        return new PlayerOption(text, uuid);
    }

    static VoteOption player(Component text, UUID uuid, String resultId, Component desc) {
        return new PlayerOption(text, uuid, resultId, desc);
    }

    static VoteOption player(Component text, UUID uuid, String resultId) {
        return new PlayerOption(text, uuid, resultId, null);
    }

    /** @deprecated 请使用 {@link #player(Component, UUID)} */
    @Deprecated
    static VoteOption player(UUID uuid) {
        return new PlayerOption(uuid);
    }

    static VoteOption text(Component text, Component desc) {
        return new TextOption(text, "", desc);
    }

    static VoteOption text(Component text) {
        return new TextOption(text);
    }

    static VoteOption text(Component text, String resultId) {
        return new TextOption(text, resultId);
    }

    static VoteOption text(Component text, String resultId, Component desc) {
        return new TextOption(text, resultId, desc);
    }

    static VoteOption item(ItemStack stack, Component desc) {
        return new ItemOption(stack, "", desc);
    }

    static VoteOption item(ItemStack stack) {
        return new ItemOption(stack);
    }

    static VoteOption item(ItemStack stack, String resultId, Component desc) {
        return new ItemOption(stack, resultId, desc);
    }

    static VoteOption item(ItemStack stack, String resultId) {
        return new ItemOption(stack, resultId, null);
    }
}
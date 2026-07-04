package cn.zbx1425.minopp.game;

import cn.zbx1425.minopp.platform.DummyLookupProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public record ActionMessage(Type type, Component message) {

    public ActionMessage(CompoundTag tag) {
        this(
            Type.valueOf(tag.getString("type")),
            Component.Serializer.fromJson(tag.getString("message"), DummyLookupProvider.INSTANCE)
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("message", Component.Serializer.toJson(message, DummyLookupProvider.INSTANCE));
        return tag;
    }

    public enum Type {
        STATE,
        FAIL,
        MESSAGE_ALL;

        public boolean isEphemeral() {
            return this == FAIL || this == MESSAGE_ALL;
        }
    }

    public static final ActionMessage NO_GAME = new ActionMessage(Type.STATE, Component.translatable("game.minopp.play.no_game"));
}

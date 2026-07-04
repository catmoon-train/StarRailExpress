package cn.zbx1425.minopp.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CardPlayer {

    public UUID uuid;
    public String name;

    public List<Card> hand = new ArrayList<>();

    public boolean hasShoutedMino = false;

    public CardPlayer(Player mcPlayer) {
        this.uuid = mcPlayer.getGameProfile().getId();
        this.name = mcPlayer.getGameProfile().getName();
    }

    public CardPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public CardPlayer(CompoundTag tag) {
        this.uuid = tag.getUUID("uuid");
        this.name = tag.getString("name");
        this.hand = new ArrayList<>(tag.getList("hand", CompoundTag.TAG_COMPOUND).stream().map(t -> new Card((CompoundTag) t)).toList());
        this.hasShoutedMino = tag.getBoolean("hasShoutedMino");
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putString("name", name);
        ListTag handTag = new ListTag();
        handTag.addAll(hand.stream().map(Card::toTag).toList());
        tag.put("hand", handTag);
        tag.putBoolean("hasShoutedMino", hasShoutedMino);
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardPlayer that = (CardPlayer) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}

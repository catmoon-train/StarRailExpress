package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.*;

public class SREPlayerClueComponent implements RoleComponent {
    public static final ComponentKey<SREPlayerClueComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("clue"), SREPlayerClueComponent.class);

    public record ClueEntry(UUID clueEntityUuid, String title, String content, long createdAt) {}

    private final Player player;
    public final List<ClueEntry> clues = new ArrayList<>();
    public final Set<UUID> sentClues = new HashSet<>();
    public int sendTimesLeft = 0;

    public SREPlayerClueComponent(Player player) { this.player = player; }

    @Override
    public Player getPlayer() { return player; }

    public void sync() {
        if (player instanceof ServerPlayer sp) KEY.sync(sp);
    }

    @Override
    public void init() {
        clues.clear();
        sentClues.clear();
        sendTimesLeft = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        writeToNbt(tag, provider);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        readFromNbt(tag, provider);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag.putInt("send_times_left", sendTimesLeft);
        ListTag clueList = new ListTag();
        for (var c : clues) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("uuid", c.clueEntityUuid());
            ct.putString("title", c.title());
            ct.putString("content", c.content());
            ct.putLong("created_at", c.createdAt());
            clueList.add(ct);
        }
        tag.put("clues", clueList);
        ListTag sent = new ListTag();
        for (UUID uuid : sentClues) sent.add(StringTag.valueOf(uuid.toString()));
        tag.put("sent", sent);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        sendTimesLeft = tag.getInt("send_times_left");
        clues.clear();
        sentClues.clear();
        ListTag clueList = tag.getList("clues", 10);
        for (int i = 0; i < clueList.size(); i++) {
            CompoundTag ct = clueList.getCompound(i);
            clues.add(new ClueEntry(ct.getUUID("uuid"), ct.getString("title"), ct.getString("content"), ct.getLong("created_at")));
        }
        ListTag sent = tag.getList("sent", 8);
        for (int i = 0; i < sent.size(); i++) sentClues.add(UUID.fromString(sent.getString(i)));
    }
}

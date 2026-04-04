package io.wifi.starrailexpress.cca;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SREGameRoundEndComponent implements AutoSyncedComponent {
    public static final ComponentKey<SREGameRoundEndComponent> KEY = ComponentRegistry
            .getOrCreate(io.wifi.starrailexpress.SRE.id("round_end"), SREGameRoundEndComponent.class);
    private final Level world;
    public final List<RoundEndData> players = new ArrayList<>();
    private GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
    public ArrayList<UUID> CustomWinnerPlayers = new ArrayList<>();

    public Component CustomWinnerTitle = null;
    public Component CustomWinnerSubtitle = null;
    public String CustomWinnerID = "";
    public int CustomWinnerColor = 0;

    public SREGameRoundEndComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void setPlayerWin(UUID playerUid, boolean hasWin) {
        if (playerUid == null)
            return;
        for (RoundEndData playerInfo : this.players) {
            if (playerUid.equals(playerInfo.player.getId())) {
                playerInfo.setHasWin(hasWin);
                return;
            }
        }
    }

    public void setRoundEndData(@NotNull List<ServerPlayer> players, GameUtils.WinStatus winStatus) {
        this.players.clear();
        for (ServerPlayer player : players) {
            this.players.add(new RoundEndData(player.getGameProfile(),
                    !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player), false));
        }
        this.winStatus = winStatus;
        this.sync();
    }

    public Component getCustomWinners() {
        if (CustomWinnerPlayers != null) {
            if (CustomWinnerPlayers.size() > 0) {
                MutableComponent winners = ComponentUtils.formatList(CustomWinnerPlayers, Component.literal(", "),
                        (uid) -> {
                            var p = world.getPlayerByUUID(uid);
                            if (p != null)
                                return p.getName();
                            else
                                return Component.literal("Unknown");
                        });
                return winners;
            }
        }
        return Component.empty();
    }

    public GameUtils.WinStatus getWinStatus() {
        return winStatus;
    }

    public void setWinStatus(GameUtils.WinStatus winStatus) {
        this.winStatus = winStatus;
        this.sync();
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.players.clear();
        this.CustomWinnerPlayers.clear();
        // for (Tag element : tag.getList("winners", 10))
        // this.CustomWinnerPlayers.add(NbtUtils.loadUUID((CompoundTag) element));
        for (Tag element : tag.getList("players", 10))
            this.players.add(new RoundEndData((CompoundTag) element));
        this.winStatus = GameUtils.WinStatus.values()[tag.getInt("winstatus")];
        if (tag.contains("winner_title")) {
            String winner_title = tag.getString("winner_title");
            try {
                this.CustomWinnerTitle = Component.Serializer.fromJson(winner_title, registryLookup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (tag.contains("winner_subtitle")) {
            String winner_subtitle = tag.getString("winner_subtitle");
            try {
                this.CustomWinnerSubtitle = Component.Serializer.fromJson(winner_subtitle, registryLookup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        CustomWinnerID = tag.getString("winner_id");
        CustomWinnerColor = tag.getInt("winner_color");
    }

    public boolean didWin(UUID uuid) {
        if (GameUtils.WinStatus.NONE == this.winStatus)
            return false;
        for (RoundEndData detail : this.players) {
            if (!detail.player.getId().equals(uuid))
                continue;
            return detail.hasWin;
        }
        return false;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (RoundEndData detail : this.players)
            list.add(detail.writeToNbt());
        // ListTag clist = new ListTag();
        // for (var detail : this.CustomWinnerPlayers)
        // clist.add(NbtUtils.createUUID(detail));
        tag.put("players", list);
        // tag.put("winners", clist);
        if (winStatus.equals(WinStatus.CUSTOM) || winStatus.equals(WinStatus.CUSTOM_COMPONENT)) {
            if (CustomWinnerID == null) {
                tag.putString("winner_id", "");
            } else {
                tag.putString("winner_id", CustomWinnerID);
            }
            if (CustomWinnerTitle != null) {
                try {
                    tag.putString("winner_title", Component.Serializer.toJson(CustomWinnerTitle, registryLookup));
                } catch (Exception e) {
                    e.printStackTrace();
                    tag.putString("winner_title", "[\"ERROR! " + e.getMessage() + "\"]");
                }
            }
            if (CustomWinnerSubtitle != null) {
                try {
                    tag.putString("winner_subtitle", Component.Serializer.toJson(CustomWinnerSubtitle, registryLookup));
                } catch (Exception e) {
                    e.printStackTrace();
                    tag.putString("winner_subtitle", "[\"ERROR! " + e.getMessage() + "\"]");
                }
            }
            tag.putInt("winner_color", CustomWinnerColor);
        }
        tag.putInt("winstatus", this.winStatus.ordinal());
    }

    public class RoundEndData {
        public GameProfile player;
        public boolean wasDead;
        public boolean hasWin;

        public boolean hasWin() {
            return this.hasWin;
        }

        public boolean wasDead() {
            return this.wasDead;
        }

        public GameProfile player() {
            return this.player;
        }

        public RoundEndData setHasWin(boolean hasWin) {
            this.hasWin = hasWin;
            return this;
        }

        public RoundEndData(GameProfile player, boolean wasDead,
                boolean hasWin) {
            this.player = player;
            this.wasDead = wasDead;
            this.hasWin = hasWin;
        }

        public RoundEndData(@NotNull CompoundTag tag) {
            this(new GameProfile(tag.getUUID("uuid"), tag.getString("name")),
                    tag.getBoolean("wasDead"),
                    tag.getBoolean("hasWin"));
        }

        public @NotNull CompoundTag writeToNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", this.player.getId());
            tag.putString("name", this.player.getName());
            tag.putBoolean("wasDead", this.wasDead);
            tag.putBoolean("hasWin", this.hasWin);
            return tag;
        }
    }
}
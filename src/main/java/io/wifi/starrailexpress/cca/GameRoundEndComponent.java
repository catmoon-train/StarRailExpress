package io.wifi.starrailexpress.cca;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts;
import io.wifi.starrailexpress.game.GameFunctions;
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

public class GameRoundEndComponent implements AutoSyncedComponent {
    public static final ComponentKey<GameRoundEndComponent> KEY = ComponentRegistry
            .getOrCreate(io.wifi.starrailexpress.SRE.id("round_end"), GameRoundEndComponent.class);
    private final Level world;
    public final List<RoundEndData> players = new ArrayList<>();
    private GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
    public ArrayList<UUID> CustomWinnerPlayers = new ArrayList<>();

    public Component CustomWinnerTitle = null;
    public Component CustomWinnerSubtitle = null;
    public String CustomWinnerID = "";
    public int CustomWinnerColor = 0;

    public GameRoundEndComponent(Level world) {
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

    public void setRoundEndData(@NotNull List<ServerPlayer> players, GameFunctions.WinStatus winStatus) {
        this.players.clear();
        for (ServerPlayer player : players) {
            RoleAnnouncementTexts.RoleAnnouncementText role = RoleAnnouncementTexts.BLANK;
            GameWorldComponent game = GameWorldComponent.KEY.get(this.world);
            if (game.canUseKillerFeatures(player)) {
                role = RoleAnnouncementTexts.getRoleAnnouncementText(TMMRoles.KILLER.identifier());
            } else if (game.isRole(player, TMMRoles.VIGILANTE)) {
                role = RoleAnnouncementTexts.getRoleAnnouncementText(TMMRoles.VIGILANTE.identifier());
            } else {
                // 尝试获取玩家的实际角色
                // io.wifi.starrailexpress.api.Role actualRole = game.getRole(player);
                // if (actualRole != null) {
                // role =
                // RoleAnnouncementTexts.getRoleAnnouncementText(actualRole.identifier());
                // } else {
                // 默认为平民
                role = RoleAnnouncementTexts.CIVILIAN;
                // }
            }
            this.players.add(new RoundEndData(player.getGameProfile(), role,
                    !io.wifi.starrailexpress.game.GameFunctions.isPlayerAliveAndSurvival(player), false));
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
                                return p.getDisplayName();
                            else
                                return Component.literal("Unknown");
                        });
                return winners;
            }
        }
        return Component.empty();
    }

    public GameFunctions.WinStatus getWinStatus() {
        return winStatus;
    }

    public void setWinStatus(GameFunctions.WinStatus winStatus) {
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
        this.winStatus = GameFunctions.WinStatus.values()[tag.getInt("winstatus")];
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
        if (GameFunctions.WinStatus.NONE == this.winStatus)
            return false;
        for (RoundEndData detail : this.players) {
            if (!detail.player.getId().equals(uuid))
                continue;
            return switch (this.winStatus) {
                case KILLERS -> detail.role == RoleAnnouncementTexts.KILLER;
                case PASSENGERS, TIME -> detail.role != RoleAnnouncementTexts.KILLER;
                default -> false;
            };
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
        tag.putInt("winstatus", this.winStatus.ordinal());
    }

    public class RoundEndData {
        public GameProfile player;
        public RoleAnnouncementTexts.RoleAnnouncementText role;
        public boolean wasDead;
        public boolean hasWin;

        public boolean hasWin() {
            return this.hasWin;
        }

        public boolean wasDead() {
            return this.wasDead;
        }

        public RoleAnnouncementTexts.RoleAnnouncementText role() {
            return this.role;
        }

        public GameProfile player() {
            return this.player;
        }

        public RoundEndData setHasWin(boolean hasWin) {
            this.hasWin = hasWin;
            return this;
        }

        public RoundEndData(GameProfile player, RoleAnnouncementTexts.RoleAnnouncementText role, boolean wasDead,
                boolean hasWin) {
            this.player = player;
            this.role = role;
            this.wasDead = wasDead;
            this.hasWin = hasWin;
        }

        public RoundEndData(@NotNull CompoundTag tag) {
            this(new GameProfile(tag.getUUID("uuid"), tag.getString("name")),
                    RoleAnnouncementTexts.getFromName((tag.getString("role"))),
                    tag.getBoolean("wasDead"),
                    tag.getBoolean("hasWin"));
        }

        public @NotNull CompoundTag writeToNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", this.player.getId());
            tag.putString("name", this.player.getName());
            tag.putString("role", this.role != null ? this.role.getId().getPath() : "blank"); // 存储角色名称
            tag.putBoolean("wasDead", this.wasDead);
            tag.putBoolean("hasWin", this.hasWin);
            return tag;
        }
    }
}
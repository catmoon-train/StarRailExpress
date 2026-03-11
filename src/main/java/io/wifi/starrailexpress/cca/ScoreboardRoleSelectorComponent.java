package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.*;

public class ScoreboardRoleSelectorComponent implements AutoSyncedComponent {
    public static final ComponentKey<ScoreboardRoleSelectorComponent> KEY = ComponentRegistry
            .getOrCreate(SRE.id("rolecounter"), ScoreboardRoleSelectorComponent.class);
    public final Scoreboard scoreboard;
    public final MinecraftServer server;
    public final Map<UUID, Integer> killerRounds = new HashMap<>();
    public final Map<UUID, Integer> vigilanteRounds = new HashMap<>();
    public final List<UUID> forcedKillers = new ArrayList<>();
    public final List<UUID> forcedVigilantes = new ArrayList<>();

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return false;
    }

    public ScoreboardRoleSelectorComponent(Scoreboard scoreboard, @Nullable MinecraftServer server) {
        this.scoreboard = scoreboard;
        this.server = server;
    }

    public int reset() {
        this.killerRounds.clear();
        this.vigilanteRounds.clear();
        return 1;
    }

    public void checkWeights(@NotNull CommandSourceStack source) {
        var killerTotal = 0d;
        var vigilanteTotal = 0d;
        for (var player : source.getLevel().players()) {
            killerTotal += Math.exp(-this.killerRounds.getOrDefault(player.getUUID(), 0) * 4);
            vigilanteTotal += Math.exp(-this.vigilanteRounds.getOrDefault(player.getUUID(), 0) * 4);
        }
        var text = Component.literal("Role Weights:").withStyle(ChatFormatting.GRAY);
        for (var player : source.getLevel().players()) {
            text = text.append("\n").append(player.getDisplayName());
            var killerRounds = this.killerRounds.getOrDefault(player.getUUID(), 0);
            var killerWeight = Math.exp(-killerRounds * 4);
            var killerPercent = killerWeight / killerTotal * 100;
            var vigilanteRounds = this.vigilanteRounds.getOrDefault(player.getUUID(), 0);
            var vigilanteWeight = Math.exp(-vigilanteRounds * 4);
            var vigilantePercent = vigilanteWeight / vigilanteTotal * 100;
            text.append(
                    Component.literal("\n  Killer (").withColor(RoleAnnouncementTexts.KILLER.colour)
                            .append(Component.literal("%d".formatted(killerRounds)).withColor(0x808080))
                            .append(Component.literal("): ").withColor(RoleAnnouncementTexts.KILLER.colour))
                            .append(Component.literal("%.2f%%".formatted(killerPercent)).withColor(0x808080)));
            text.append(
                    Component.literal("\n  Vigilante (").withColor(RoleAnnouncementTexts.VIGILANTE.colour)
                            .append(Component.literal("%d".formatted(vigilanteRounds)).withColor(0x808080))
                            .append(Component.literal("): ").withColor(RoleAnnouncementTexts.VIGILANTE.colour))
                            .append(Component.literal("%.2f%%".formatted(vigilantePercent)).withColor(0x808080)));
        }
        var finalText = text;
        source.sendSuccess(() -> finalText, false);
    }

    public void setKillerRounds(@NotNull CommandSourceStack source, @NotNull ServerPlayer player, int times) {
        if (times < 0)
            times = 0;
        if (times == 0)
            this.killerRounds.remove(player.getUUID());
        else
            this.killerRounds.put(player.getUUID(), times);
        var finalTimes = times;
        source.sendSuccess(() -> Component.literal("Set ").withStyle(ChatFormatting.GRAY)
                .append(player.getDisplayName().copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s Killer rounds to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("%d".formatted(finalTimes)).withColor(0x808080))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)), false);
    }

    public void setVigilanteRounds(@NotNull CommandSourceStack source, @NotNull ServerPlayer player, int times) {
        if (times < 0)
            times = 0;
        if (times == 0)
            this.vigilanteRounds.remove(player.getUUID());
        else
            this.vigilanteRounds.put(player.getUUID(), times);
        var finalTimes = times;
        source.sendSuccess(() -> Component.literal("Set ").withStyle(ChatFormatting.GRAY)
                .append(player.getDisplayName().copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s Vigilante rounds to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("%d".formatted(finalTimes)).withColor(0x808080))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)), false);
    }

    public int assignKillers(ServerLevel world, GameWorldComponent gameComponent, @NotNull List<ServerPlayer> players,
            int killerCount) {
        this.reduceKillers();
        var killers = new ArrayList<UUID>();
        for (var uuid : this.forcedKillers) {
            killers.add(uuid);
            killerCount--;
            this.killerRounds.put(uuid, this.killerRounds.getOrDefault(uuid, 1) + 1);
        }
        this.forcedKillers.clear();
        var map = new HashMap<ServerPlayer, Float>();
        var total = 0f;
        for (var player : players) {
            var weight = (float) Math.exp(-this.killerRounds.getOrDefault(player.getUUID(), 0) * 4);
            if (!GameWorldComponent.KEY.get(world).areWeightsEnabled())
                weight = 1;
            map.put(player, weight);
            total += weight;
        }
        for (var i = 0; i < killerCount; i++) {
            var random = world.getRandom().nextFloat() * total;
            for (var entry : map.entrySet()) {
                random -= entry.getValue();
                if (random <= 0) {
                    killers.add(entry.getKey().getUUID());
                    total -= entry.getValue();
                    map.remove(entry.getKey());
                    this.killerRounds.put(entry.getKey().getUUID(),
                            this.killerRounds.getOrDefault(entry.getKey().getUUID(), 1) + 1);
                    break;
                }
            }
        }
        for (var uuid : killers) {
            gameComponent.addRole(uuid, TMMRoles.KILLER, false);
            var player = world.getPlayerByUUID(uuid);
            if (player != null) {
                PlayerShopComponent.KEY.get(player).setBalance(GameConstants.getMoneyStart());
            }
        }
        return killers.size();
    }

    private void reduceKillers() {
        var minimum = Integer.MAX_VALUE;
        for (var times : this.killerRounds.values())
            minimum = Math.min(minimum, times);
        for (var times : this.killerRounds.keySet())
            this.killerRounds.put(times, this.killerRounds.get(times) - minimum);
    }

    public void assignVigilantes(ServerLevel world, GameWorldComponent gameComponent,
            @NotNull List<ServerPlayer> players, int vigilanteCount) {
        this.reduceVigilantes();
        var vigilantes = new ArrayList<ServerPlayer>();
        for (var uuid : this.forcedVigilantes) {
            var player = world.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer serverPlayer && players.contains(serverPlayer)
                    && !gameComponent.canUseKillerFeatures(player)) {
                player.addItem(new ItemStack(TMMItems.REVOLVER));
                gameComponent.addRole(player, TMMRoles.VIGILANTE, false);
                vigilanteCount--;
                this.vigilanteRounds.put(player.getUUID(), this.vigilanteRounds.getOrDefault(player.getUUID(), 1) + 1);
            }
        }
        this.forcedVigilantes.clear();
        var map = new HashMap<ServerPlayer, Float>();
        var total = 0f;
        for (var player : players) {
            if (gameComponent.isRole(player, TMMRoles.KILLER))
                continue;
            var weight = (float) Math.exp(-this.vigilanteRounds.getOrDefault(player.getUUID(), 0) * 4);
            if (!GameWorldComponent.KEY.get(world).areWeightsEnabled())
                weight = 1;
            map.put(player, weight);
            total += weight;
        }
        for (var i = 0; i < vigilanteCount; i++) {
            var random = world.getRandom().nextFloat() * total;
            for (var entry : map.entrySet()) {
                random -= entry.getValue();
                if (random <= 0) {
                    vigilantes.add(entry.getKey());
                    total -= entry.getValue();
                    map.remove(entry.getKey());
                    this.vigilanteRounds.put(entry.getKey().getUUID(),
                            this.vigilanteRounds.getOrDefault(entry.getKey().getUUID(), 1) + 1);
                    break;
                }
            }
        }
        for (var player : vigilantes) {
            player.addItem(new ItemStack(TMMItems.REVOLVER));
            gameComponent.addRole(player, TMMRoles.VIGILANTE, false);
        }
    }

    private void reduceVigilantes() {
        var minimum = Integer.MAX_VALUE;
        for (var times : this.vigilanteRounds.values())
            minimum = Math.min(minimum, times);
        for (var times : this.vigilanteRounds.keySet())
            this.vigilanteRounds.put(times, this.vigilanteRounds.get(times) - minimum);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        var killerRounds = new ListTag();
        for (var detail : this.killerRounds.entrySet()) {
            var compound = new CompoundTag();
            compound.putUUID("uuid", detail.getKey());
            compound.putInt("times", detail.getValue());
            killerRounds.add(compound);
        }
        tag.put("killerRounds", killerRounds);
        var vigilanteRounds = new ListTag();
        for (var detail : this.vigilanteRounds.entrySet()) {
            var compound = new CompoundTag();
            compound.putUUID("uuid", detail.getKey());
            compound.putInt("times", detail.getValue());
            vigilanteRounds.add(compound);
        }
        tag.put("vigilanteRounds", vigilanteRounds);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killerRounds.clear();
        for (var element : tag.getList("killerRounds", 10)) {
            var compound = (CompoundTag) element;
            if (!compound.contains("uuid") || !compound.contains("times"))
                continue;
            this.killerRounds.put(compound.getUUID("uuid"), compound.getInt("times"));
        }
        this.vigilanteRounds.clear();
        for (var element : tag.getList("vigilanteRounds", 10)) {
            var compound = (CompoundTag) element;
            if (!compound.contains("uuid") || !compound.contains("times"))
                continue;
            this.vigilanteRounds.put(compound.getUUID("uuid"), compound.getInt("times"));
        }
    }
}
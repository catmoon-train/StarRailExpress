package org.agmas.noellesroles.game.roles.neutral.pelican;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class PelicanPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PelicanPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pelican"),
            PelicanPlayerComponent.class);

    private static final double EAT_RANGE = 3.15D;
    public static final int INSTINCT_RANGE = 25;

    private final Player player;

    public int eatenCount = 0;
    public int requiredEaten = 1;
    public int cooldownTicks = 0;
    public List<String> bellyNames = new ArrayList<>();
    public List<UUID> bellyPlayerIds = new ArrayList<>();
    public Set<UUID> uniqueEaten = new HashSet<>();
    public long eatCooldownUntil = 0;

    public PelicanPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        eatenCount = 0;
        requiredEaten = 1;
        cooldownTicks = 0;
        bellyNames.clear();
        bellyPlayerIds.clear();
        uniqueEaten.clear();
        eatCooldownUntil = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PELICAN)) return;
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        int totalParticipants = gameWorld.getPlayerCount();
        double percent = org.agmas.noellesroles.config.NoellesRolesConfig.HANDLER.instance().pelicanEatPercentage;
        int newRequired = Math.max(1, (int) Math.ceil(totalParticipants * (percent / 100.0D)) - 1);
        if (requiredEaten != newRequired) {
            requiredEaten = newRequired;
            sync();
        }

        int prevCooldown = cooldownTicks;
        if (eatCooldownUntil > 0 && player.level().getGameTime() >= eatCooldownUntil) {
            eatCooldownUntil = 0;
        }
        if (eatCooldownUntil > 0) {
            cooldownTicks = Math.max(0, (int)(eatCooldownUntil - player.level().getGameTime()));
        } else {
            cooldownTicks = 0;
        }
        // 冷却变化时同步到客户端（每秒同步一次足够）
        if (prevCooldown != cooldownTicks && cooldownTicks % 20 == 0) {
            sync();
        }
    }

    public boolean tryEat(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (target == null) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) return false;
        if (target.getUUID().equals(player.getUUID())) return false;

        if (eatCooldownUntil > 0) {
            long remaining = Math.max(1, (eatCooldownUntil - player.level().getGameTime()) / 20);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.cooldown", remaining)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        if (bellyPlayerIds.contains(target.getUUID())) return false;
        if (PelicanManager.isStashed(target)) return false;

        // 吞噬玩家
        PelicanManager.stashPlayer(sp, target);

        long cooldownTicks = 45 * 20L; // 硬编码：45秒冷却
        eatCooldownUntil = player.level().getGameTime() + cooldownTicks;

        bellyPlayerIds.add(target.getUUID());
        bellyNames.add(target.getName().getString());
        uniqueEaten.add(target.getUUID());
        eatenCount = uniqueEaten.size();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.9F, 0.65F);

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.pelican.swallowed",
                                target.getName().getString(), eatenCount, requiredEaten)
                        .withStyle(ChatFormatting.GOLD),
                true);

        sync();
        checkWinCondition();
        return true;
    }

    public boolean releaseLast() {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (bellyPlayerIds.isEmpty()) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.belly_empty")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        UUID targetId = bellyPlayerIds.remove(bellyPlayerIds.size() - 1);
        if (!bellyNames.isEmpty()) bellyNames.remove(bellyNames.size() - 1);
        // 不减少 uniqueEaten（重复吞噬同一玩家不再计数，释放后再次吞噬也不增加计数）

        ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            PelicanManager.releasePlayer(target);
        }

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.pelican.released_one")
                        .withStyle(ChatFormatting.GREEN),
                true);
        sync();
        return true;
    }

    public void checkWinCondition() {
        if (eatenCount >= requiredEaten && requiredEaten > 0) {
            if (player.level() instanceof ServerLevel serverLevel) {
                RoleUtils.customWinnerWin(serverLevel,
                        GameUtils.WinStatus.CUSTOM,
                        ModRoles.PELICAN_ID.getPath(),
                        OptionalInt.of(ModRoles.PELICAN.color()));
            }
        }
    }

    public static boolean checkPelicanVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;
            if (!gameWorld.isRole(sp, ModRoles.PELICAN)) continue;
            if (!KEY.isProvidedBy(sp)) continue;
            PelicanPlayerComponent comp = KEY.get(sp);
            if (comp.eatenCount >= comp.requiredEaten && comp.requiredEaten > 0) {
                RoleUtils.customWinnerWin(serverLevel,
                        GameUtils.WinStatus.CUSTOM,
                        ModRoles.PELICAN_ID.getPath(),
                        OptionalInt.of(ModRoles.PELICAN.color()));
                return true;
            }
        }
        return false;
    }

    public static boolean isLocalVulture(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.PELICAN);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("EatenCount", eatenCount);
        tag.putInt("RequiredEaten", requiredEaten);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putLong("EatCooldownUntil", eatCooldownUntil);

        ListTag nameList = new ListTag();
        for (String name : bellyNames) {
            nameList.add(StringTag.valueOf(name));
        }
        tag.put("BellyNames", nameList);

        ListTag idList = new ListTag();
        for (UUID id : bellyPlayerIds) {
            idList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("BellyPlayerIds", idList);

        ListTag uniqueList = new ListTag();
        for (UUID id : uniqueEaten) {
            uniqueList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("UniqueEaten", uniqueList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        eatenCount = tag.getInt("EatenCount");
        requiredEaten = tag.getInt("RequiredEaten");
        cooldownTicks = tag.getInt("CooldownTicks");
        eatCooldownUntil = tag.getLong("EatCooldownUntil");

        bellyNames.clear();
        if (tag.contains("BellyNames", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BellyNames", Tag.TAG_STRING);
            for (Tag t : list) {
                bellyNames.add(t.getAsString());
            }
        }

        bellyPlayerIds.clear();
        if (tag.contains("BellyPlayerIds", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BellyPlayerIds", Tag.TAG_STRING);
            for (Tag t : list) {
                try {
                    bellyPlayerIds.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {}
            }
        }

        uniqueEaten.clear();
        if (tag.contains("UniqueEaten", Tag.TAG_LIST)) {
            ListTag list = tag.getList("UniqueEaten", Tag.TAG_STRING);
            for (Tag t : list) {
                try {
                    uniqueEaten.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {}
            }
        }

        // 清除本能透视缓存，确保 bellyPlayerIds 变化后高亮颜色即时更新
        SREClient.cachedHighLightMap.clear();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}

package org.agmas.noellesroles.roles.candlebearer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public class CandleBearerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<CandleBearerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "candlebearer"),
            CandleBearerPlayerComponent.class);

    public static final int MAX_INVISIBILITY_CHARGES = 3;
    public static final int INVISIBILITY_DURATION_TICKS = 30 * 20;
    public static final int GLOW_DELAY_TICKS = 10 * 20;
    public static final int GLOW_DURATION_TICKS = 10 * 20;

    private final Player player;

    private final Set<UUID> candleLitPlayers = new HashSet<>();
    private final Set<UUID> corpseCandleCompleted = new HashSet<>();
    private final Set<UUID> deathRewardGranted = new HashSet<>();

    private final Map<UUID, Integer> pendingPlayerGlow = new HashMap<>();
    private final Map<UUID, Integer> pendingCorpseGlow = new HashMap<>();
    private final List<Integer> pendingCampfireSounds = new ArrayList<>();

    public int invisibilityCharges = 0;
    public int invisibilityTicks = 0;
    public int successfulCandles = 0;
    public int requiredCandles = 0;

    public CandleBearerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        candleLitPlayers.clear();
        corpseCandleCompleted.clear();
        deathRewardGranted.clear();
        pendingPlayerGlow.clear();
        pendingCorpseGlow.clear();
        pendingCampfireSounds.clear();
        invisibilityCharges = 0;
        invisibilityTicks = 0;
        successfulCandles = 0;
        int totalPlayers = player.level().players().size();
        requiredCandles = Math.max(1, totalPlayers / 5);
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
    public void clientTick() {
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.CANDLE_BEARER)) {
            return;
        }
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        int totalPlayers = player.level().players().size();
        int newRequired = Math.max(1, totalPlayers / 5);
        if (requiredCandles != newRequired) {
            requiredCandles = newRequired;
            sync();
        }

        boolean changed = false;

        if (invisibilityTicks > 0) {
            invisibilityTicks--;
            if (invisibilityTicks % 20 == 0 || invisibilityTicks <= 0) {
                changed = true;
            }
        }

        if (processGlowTasks()) {
            changed = true;
        }

        if (processDeathRewards()) {
            changed = true;
        }

        if (processSuccessSounds()) {
            changed = true;
        }

        if (changed) {
            sync();
        }
    }

    public boolean candleLivingPlayer(Player target) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.target_not_alive")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (target.getUUID().equals(player.getUUID())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.cannot_target_self")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (candleLitPlayers.contains(target.getUUID())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.already_lit",
                                    target.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        candleLitPlayers.add(target.getUUID());
        pendingPlayerGlow.put(target.getUUID(), GLOW_DELAY_TICKS);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.candlebearer.lit_player",
                                target.getDisplayName())
                        .withStyle(ChatFormatting.GOLD),
                true);
        sync();
        return true;
    }

    public boolean candleCorpse(PlayerBodyEntity body) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        UUID owner = body.getPlayerUuid();
        if (owner == null) {
            return false;
        }

        if (!candleLitPlayers.contains(owner)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.corpse_not_linked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        if (corpseCandleCompleted.contains(owner)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.corpse_already_lit")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        corpseCandleCompleted.add(owner);
        successfulCandles++;
        pendingCorpseGlow.put(owner, GLOW_DELAY_TICKS);
        pendingCampfireSounds.add(GLOW_DELAY_TICKS);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.candlebearer.corpse_success",
                                successfulCandles,
                                requiredCandles)
                        .withStyle(ChatFormatting.GREEN),
                true);

        if (successfulCandles >= requiredCandles && player.level() instanceof ServerLevel serverLevel) {
            RoleUtils.customWinnerWin(serverLevel,
                    GameUtils.WinStatus.CANDLE_BEARER,
                    ModRoles.CANDLE_BEARER_ID.getPath(),
                    OptionalInt.of(ModRoles.CANDLE_BEARER.color()));
        } else {
            sync();
        }

        return true;
    }

    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        if (invisibilityCharges <= 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.candlebearer.no_charge")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        invisibilityCharges--;
        invisibilityTicks = INVISIBILITY_DURATION_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0,
                false, false, true));

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.candlebearer.use_invisibility",
                                INVISIBILITY_DURATION_TICKS / 20,
                                invisibilityCharges)
                        .withStyle(ChatFormatting.AQUA),
                true);
        sync();
        return true;
    }

    public boolean isCandleLit(UUID playerId) {
        return candleLitPlayers.contains(playerId);
    }

    public static boolean checkCandleBearerVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                continue;
            }
            if (!gameWorld.isRole(sp, ModRoles.CANDLE_BEARER)) {
                continue;
            }
            CandleBearerPlayerComponent component = KEY.get(sp);
            if (component.successfulCandles >= component.requiredCandles && component.requiredCandles > 0) {
                RoleUtils.customWinnerWin(serverLevel,
                        GameUtils.WinStatus.CANDLE_BEARER,
                        ModRoles.CANDLE_BEARER_ID.getPath(),
                        OptionalInt.of(ModRoles.CANDLE_BEARER.color()));
                return true;
            }
        }
        return false;
    }

    private boolean processGlowTasks() {
        boolean changed = false;

        Iterator<Map.Entry<UUID, Integer>> playerIter = pendingPlayerGlow.entrySet().iterator();
        while (playerIter.hasNext()) {
            Map.Entry<UUID, Integer> entry = playerIter.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }

            Player target = player.level().getPlayerByUUID(entry.getKey());
            if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0,
                        false, false, true));
            }
            playerIter.remove();
            changed = true;
        }

        Iterator<Map.Entry<UUID, Integer>> corpseIter = pendingCorpseGlow.entrySet().iterator();
        while (corpseIter.hasNext()) {
            Map.Entry<UUID, Integer> entry = corpseIter.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }

            PlayerBodyEntity body = findBodyByOwner(entry.getKey());
            if (body != null && !body.isRemoved()) {
                body.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0,
                        false, false, true));
            }
            corpseIter.remove();
            changed = true;
        }

        return changed;
    }

    private boolean processDeathRewards() {
        boolean changed = false;
        for (UUID uuid : candleLitPlayers) {
            if (deathRewardGranted.contains(uuid)) {
                continue;
            }
            Player litPlayer = player.level().getPlayerByUUID(uuid);
            boolean dead = litPlayer == null || !GameUtils.isPlayerAliveAndSurvival(litPlayer);
            if (!dead) {
                continue;
            }

            deathRewardGranted.add(uuid);
            if (invisibilityCharges < MAX_INVISIBILITY_CHARGES) {
                invisibilityCharges++;
            }
            pendingCorpseGlow.putIfAbsent(uuid, GLOW_DELAY_TICKS);
            changed = true;

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.candlebearer.charge_gained",
                                        invisibilityCharges,
                                        MAX_INVISIBILITY_CHARGES)
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
        return changed;
    }

    private boolean processSuccessSounds() {
        boolean changed = false;
        for (int i = pendingCampfireSounds.size() - 1; i >= 0; i--) {
            int left = pendingCampfireSounds.get(i) - 1;
            if (left > 0) {
                pendingCampfireSounds.set(i, left);
                continue;
            }

            if (player.level() instanceof ServerLevel level) {
                for (Player p : level.players()) {
                    if (p.isSpectator()) {
                        continue;
                    }
                    level.playSound(
                            p,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.CAMPFIRE_CRACKLE,
                            SoundSource.MASTER,
                            5.0F,
                            1.0F);
                }
            }
            pendingCampfireSounds.remove(i);
            changed = true;
        }
        return changed;
    }

    private PlayerBodyEntity findBodyByOwner(UUID owner) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        List<PlayerBodyEntity> bodies = level.getEntitiesOfClass(PlayerBodyEntity.class,
                player.getBoundingBox().inflate(500.0D));
        for (PlayerBodyEntity body : bodies) {
            if (owner.equals(body.getPlayerUuid())) {
                return body;
            }
        }
        return null;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("InvisibilityCharges", invisibilityCharges);
        tag.putInt("InvisibilityTicks", invisibilityTicks);
        tag.putInt("SuccessfulCandles", successfulCandles);
        tag.putInt("RequiredCandles", requiredCandles);

        ListTag litList = new ListTag();
        for (UUID uuid : candleLitPlayers) {
            litList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("CandleLitPlayers", litList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        invisibilityCharges = tag.getInt("InvisibilityCharges");
        invisibilityTicks = tag.getInt("InvisibilityTicks");
        successfulCandles = tag.getInt("SuccessfulCandles");
        requiredCandles = tag.getInt("RequiredCandles");

        candleLitPlayers.clear();
        if (tag.contains("CandleLitPlayers", Tag.TAG_LIST)) {
            ListTag litList = tag.getList("CandleLitPlayers", Tag.TAG_STRING);
            for (Tag t : litList) {
                try {
                    candleLitPlayers.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}

package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.ShouldDropOnDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.item.GhostArtItem;
import org.agmas.noellesroles.content.item.GhostWardRelicItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 对局物品归属与权威消耗记录，不新建玩家CCA；局末和世界卸载均释放。 */
public final class SackGhostItems {
    private static final String KEY = "SackGhost";
    private static final Map<ServerLevel, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean registered;

    static final class RelicState { long wardReadyAt; long breakReadyAt; }
    static final class Round {
        final UUID id = UUID.randomUUID();
        final Set<UUID> issuedGuests = new HashSet<>();
        final Set<UUID> spentArts = new HashSet<>();
        final Map<UUID, RelicState> relics = new HashMap<>();
        final Map<UUID, Long> wards = new HashMap<>();
        long activeTicks;
    }

    private SackGhostItems() {}
    static Round round(ServerLevel level) { return ROUNDS.computeIfAbsent(level, ignored -> new Round()); }
    public static long now(ServerLevel level) { return round(level).activeTicks; }
    public static int initialPlayers(ServerLevel level) {
        return Math.max(1, SREGameWorldComponent.KEY.get(level).getStartingPlayerCount());
    }
    public static MaChenXuRoleData data(Player player) { return RoleData.getNullable(MaChenXuRoleData.class, player); }

    public static void grantWard(ServerPlayer target) {
        round(target.serverLevel()).wards.put(target.getUUID(), now(target.serverLevel()) + SackGhostRules.WARD_DURATION);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(org.agmas.noellesroles.init.ModEffects.GHOST_WARD,
                SackGhostRules.WARD_DURATION, 0, false, false, false));
    }

    public static void clearWard(ServerPlayer target) {
        Round round = ROUNDS.get(target.serverLevel());
        if (round != null) round.wards.remove(target.getUUID());
        target.removeEffect(org.agmas.noellesroles.init.ModEffects.GHOST_WARD);
    }

    private static void maintainWards(ServerLevel level, Round round) {
        for (var iterator = round.wards.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(entry.getKey());
            int left = SackGhostRules.remaining(round.activeTicks, entry.getValue());
            if (target == null || target.level() != level || left == 0) {
                if (target != null) target.removeEffect(org.agmas.noellesroles.init.ModEffects.GHOST_WARD);
                iterator.remove();
                continue;
            }
            var current = target.getEffect(org.agmas.noellesroles.init.ModEffects.GHOST_WARD);
            int refresh = SackGhostRules.effectRefreshDuration(left, current == null ? 0 : current.getDuration());
            if (refresh > 0) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    org.agmas.noellesroles.init.ModEffects.GHOST_WARD, refresh, 0, false, false, false));
        }
    }

    public static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY);
    }

    static void put(ItemStack stack, CompoundTag state) {
        CompoundTag all = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        all.put(KEY, state);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(all));
    }

    public static boolean eligibleRelicCarrier(Player player) {
        var game = SREGameWorldComponent.KEY.get(player.level());
        var role = game.getAllegianceRole(player);
        // 必须是真正的好人职业，包含警长，但不包含好人方中立或伪装成好人的杀手。
        return role != null && role.isInnocent() && !role.isNeutrals() && !role.canUseKiller() && game.isInnocentTeam(player);
    }

    static boolean validRound(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.hasUUID("Round") && tag.getUUID("Round").equals(round(player.serverLevel()).id);
    }

    public static Item artItem(GhostArt art) {
        return switch (art) {
            case VEIL -> ModItems.GHOST_ART_VEIL;
            case PUSH -> ModItems.GHOST_ART_PUSH;
            case EFFIGY -> ModItems.GHOST_ART_EFFIGY;
            case ECHO -> ModItems.GHOST_ART_ECHO;
            case WAIL -> ModItems.GHOST_ART_WAIL;
            case PARASITE -> ModItems.GHOST_ART_PARASITE;
            case SHROUD -> ModItems.GHOST_ART_SHROUD;
            case SEIZE -> ModItems.GHOST_ART_SEIZE;
        };
    }

    public static ItemStack createArt(ServerPlayer owner, UUID session, GhostArt art) {
        ItemStack stack = artItem(art).getDefaultInstance();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Round", round(owner.serverLevel()).id);
        tag.putUUID("Owner", owner.getUUID());
        tag.putUUID("Session", session);
        tag.putUUID("Token", UUID.randomUUID());
        put(stack, tag);
        return stack;
    }

    public static boolean validArt(ServerPlayer owner, ItemStack stack, UUID session) {
        if (!(stack.getItem() instanceof GhostArtItem) || stack.getCount() != 1 || !validRound(owner, stack)) return false;
        CompoundTag tag = tag(stack);
        return tag.hasUUID("Owner") && tag.getUUID("Owner").equals(owner.getUUID())
                && tag.hasUUID("Session") && tag.getUUID("Session").equals(session) && tag.hasUUID("Token")
                && !round(owner.serverLevel()).spentArts.contains(tag.getUUID("Token"));
    }

    public static void consumeArt(ServerPlayer owner, ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag.hasUUID("Token")) round(owner.serverLevel()).spentArts.add(tag.getUUID("Token"));
        stack.shrink(1);
    }

    /** 回响在返回或超时后结清同一令牌，搬动物品与回溯副本都不能增加次数。 */
    public static void consumeToken(ServerPlayer owner, UUID token, ServerLevel origin) {
        round(origin).spentArts.add(token);
        for (int i = 0; i <= owner.getInventory().getContainerSize(); i++) {
            boolean cursor = i == owner.getInventory().getContainerSize();
            ItemStack stack = cursor ? owner.containerMenu.getCarried() : owner.getInventory().getItem(i);
            CompoundTag state = tag(stack);
            if (stack.getItem() instanceof GhostArtItem && state.hasUUID("Token") && token.equals(state.getUUID("Token"))) {
                if (cursor) owner.containerMenu.setCarried(ItemStack.EMPTY); else owner.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        owner.getInventory().setChanged();
        owner.containerMenu.broadcastChanges();
    }

    public static int artCount(Player owner) {
        int count = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack stack = owner.getInventory().getItem(i);
            if (stack.getItem() instanceof GhostArtItem) count += stack.getCount();
        }
        ItemStack carried = owner.containerMenu.getCarried();
        if (carried.getItem() instanceof GhostArtItem) count += carried.getCount();
        return count;
    }

    public static boolean canReceive(Player player, ItemStack stack) {
        if (player.level().isClientSide()) return true;
        if (!(player instanceof ServerPlayer owner)) return false;
        if (stack.getItem() instanceof GhostWardRelicItem) {
            RelicState state = relicState(owner, stack);
            if (!eligibleRelicCarrier(owner) || !validRound(owner, stack) || state == null || stack.getCount() != 1) return false;
            updateRelicTag(owner, stack, state);
            return true;
        }
        if (!(stack.getItem() instanceof GhostArtItem)) return true;
        MaChenXuRoleData data = data(owner);
        if (data == null || !data.isActiveMaChenXu() || !validArt(owner, stack, data.sessionId())) return false;
        int represented = 0;
        ItemStack carried = owner.containerMenu.getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, stack)) represented = carried.getCount();
        return SackGhostRules.canReceiveArts(artCount(owner) - represented, stack.getCount());
    }

    static RelicState relicState(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.hasUUID("Relic") ? round(player.serverLevel()).relics.get(tag.getUUID("Relic")) : null;
    }

    static void updateRelicTag(ServerPlayer player, ItemStack stack, RelicState state) {
        CompoundTag tag = tag(stack);
        tag.putLong("Clock", now(player.serverLevel()));
        tag.putLong("WardReadyAt", state.wardReadyAt);
        tag.putLong("BreakReadyAt", state.breakReadyAt);
        put(stack, tag);
    }

    /** 包含鼠标持有物，保证魂噬清包和无尸体死亡仍保全可传承法器。 */
    public static void dropRelics(ServerPlayer player) {
        for (int i = 0; i <= player.getInventory().getContainerSize(); i++) {
            boolean cursor = i == player.getInventory().getContainerSize();
            ItemStack stack = cursor ? player.containerMenu.getCarried() : player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof GhostWardRelicItem)) continue;
            if (cursor) player.containerMenu.setCarried(ItemStack.EMPTY); else player.getInventory().setItem(i, ItemStack.EMPTY);
            player.drop(stack, true, false);
        }
    }

    public static void giveRelic(ServerPlayer player) {
        if (!eligibleRelicCarrier(player)) return;
        Round round = round(player.serverLevel());
        if (!round.issuedGuests.add(player.getUUID())) return;
        UUID id = UUID.randomUUID();
        round.relics.put(id, new RelicState());
        ItemStack stack = ModItems.GHOST_WARD_RELIC.getDefaultInstance();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Round", round.id);
        tag.putUUID("Relic", id);
        put(stack, tag);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static void clearOwnerArts(ServerPlayer owner) {
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).getItem() instanceof GhostArtItem) owner.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (owner.containerMenu.getCarried().getItem() instanceof GhostArtItem) owner.containerMenu.setCarried(ItemStack.EMPTY);
        owner.containerMenu.broadcastChanges();
    }

    private static void sanitize(ServerPlayer player, Set<UUID> relics, Set<UUID> arts) {
        int count = 0;
        MaChenXuRoleData data = data(player);
        for (int i = 0; i <= player.getInventory().getContainerSize(); i++) {
            boolean cursor = i == player.getInventory().getContainerSize();
            ItemStack stack = cursor ? player.containerMenu.getCarried() : player.getInventory().getItem(i);
            if (stack.getItem() instanceof GhostArtItem) {
                boolean valid = data != null && data.isActiveMaChenXu() && validArt(player, stack, data.sessionId());
                if (valid) valid = arts.add(tag(stack).getUUID("Token")) && ++count <= SackGhostRules.MAX_ART_ITEMS;
                if (!valid) { if (cursor) player.containerMenu.setCarried(ItemStack.EMPTY); else player.getInventory().setItem(i, ItemStack.EMPTY); }
            } else if (stack.getItem() instanceof GhostWardRelicItem) {
                boolean valid = stack.getCount() == 1 && validRound(player, stack) && relicState(player, stack) != null;
                if (valid && !eligibleRelicCarrier(player)) {
                    if (cursor) player.containerMenu.setCarried(ItemStack.EMPTY); else player.getInventory().setItem(i, ItemStack.EMPTY);
                    player.drop(stack, false);
                } else if (!valid || !relics.add(tag(stack).getUUID("Relic"))) {
                    if (cursor) player.containerMenu.setCarried(ItemStack.EMPTY); else player.getInventory().setItem(i, ItemStack.EMPTY);
                } else {
                    updateRelicTag(player, stack, relicState(player, stack));
                }
            }
        }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        SackGhostPackets.register();
        SackGhostRewind.register();
        ShouldDropOnDeath.EVENT.register(stack -> stack.getItem() instanceof GhostWardRelicItem);
        OnPlayerDeath.EVENT.register((victim, reason) -> {
            if (victim instanceof ServerPlayer player) {
                dropRelics(player);
                clearWard(player);
                for (var owner : player.serverLevel().players()) {
                    var data = data(owner);
                    if (data != null) data.cleanse(player);
                }
            }
        });
        io.wifi.starrailexpress.event.OnPlayerDeathWithBody.EVENT.register((victim, killer, reason, body) -> {
            var inventory = body.getComponent().getCorpseInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() instanceof GhostArtItem || stack.getItem() instanceof GhostWardRelicItem) inventory.setItem(i, ItemStack.EMPTY);
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (ServerPlayer player : level.players()) {
                MaChenXuRoleData data = data(player);
                if (data != null) data.clear();
                clearWard(player);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() instanceof GhostArtItem || stack.getItem() instanceof GhostWardRelicItem) player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                ItemStack carried = player.containerMenu.getCarried();
                if (carried.getItem() instanceof GhostArtItem || carried.getItem() instanceof GhostWardRelicItem) player.containerMenu.setCarried(ItemStack.EMPTY);
            }
            for (var entity : level.getEntities(EntityType.ITEM, item -> item.getItem().getItem() instanceof GhostArtItem
                    || item.getItem().getItem() instanceof GhostWardRelicItem)) entity.discard();
            ROUNDS.remove(level);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (ServerPlayer owner : handler.player.serverLevel().players()) {
                var data = data(owner);
                if (data != null && data.otherworldActive) SackGhostPackets.send(handler.player, owner, data);
                if (data != null && data.formFlags() != 0) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player,
                        new SackGhostPackets.FormS2C(owner.getUUID(), data.formFlags(), data.formRemaining()));
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            Round round = ROUNDS.get(level);
            if (round == null) return;
            var game = SREGameWorldComponent.KEY.get(level);
            if (game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE && game.isSkillAvailable
                    && !SREGameTimeComponent.KEY.get(level).isTimeFrozen()) round.activeTicks++;
            maintainWards(level, round);
            if (level.getGameTime() % 20 != 0) return;
            Set<UUID> relics = new HashSet<>(), arts = new HashSet<>();
            for (ServerPlayer player : level.players()) sanitize(player, relics, arts);
            for (var item : level.getEntities(EntityType.ITEM, entity -> entity.getItem().getItem() instanceof GhostWardRelicItem
                    || entity.getItem().getItem() instanceof GhostArtItem)) {
                ItemStack stack = item.getItem();
                CompoundTag tag = tag(stack);
                if (stack.getItem() instanceof GhostArtItem || !tag.hasUUID("Round") || !tag.getUUID("Round").equals(round.id)
                        || !tag.hasUUID("Relic") || !round.relics.containsKey(tag.getUUID("Relic"))
                        || !relics.add(tag.getUUID("Relic"))) item.discard();
            }
        });
        ServerWorldEvents.UNLOAD.register((server, level) -> ROUNDS.remove(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ROUNDS.clear());
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!(held.getItem() instanceof GhostArtItem) && !(held.getItem() instanceof GhostWardRelicItem)) return InteractionResult.PASS;
            return held.getItem().use(level, player, hand).getResult();
        });
    }
}

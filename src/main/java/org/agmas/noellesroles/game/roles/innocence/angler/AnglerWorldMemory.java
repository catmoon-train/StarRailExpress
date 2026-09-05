/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.innocence.angler;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.ErrorAnglerEntity;
import org.agmas.noellesroles.content.item.angler.LivingCarpItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对局级垂钓者状态：钓点、物品史、转化、车票回程等。开局/结束清空。
 */
public final class AnglerWorldMemory {
    private static final List<CatchSpot> CATCH_SPOTS = new ArrayList<>();
    private static final ArrayDeque<ItemStack> ITEM_HISTORY = new ArrayDeque<>();
    private static final Set<UUID> CONVERTED = new HashSet<>();
    private static final Set<UUID> TICKET_USED = new HashSet<>();
    private static final Map<UUID, PendingReturn> TICKET_RETURNS = new HashMap<>();
    private static final Map<UUID, Long> FALSE_TOOTH_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> RIDE_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> WATCH_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> HEART_UNTIL = new HashMap<>();
    private static final List<InkPuddle> INK_PUDDLES = new ArrayList<>();
    private static final List<DelayedAction> DELAYED = new ArrayList<>();

    private static boolean errorRodClaimed = false;
    private static UUID echoSkin = null;
    private static Long pendingRespawnAt = null;

    public record CatchSpot(ResourceKey<Level> dimension, BlockPos pos) {
        public double distSqr(CatchSpot other) {
            if (!dimension.equals(other.dimension)) {
                return Double.MAX_VALUE;
            }
            return pos.distSqr(other.pos);
        }
    }

    private record PendingReturn(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, long returnTick) {
    }

    private record InkPuddle(ResourceKey<Level> dimension, Vec3 pos, long expireTick) {
    }

    private record DelayedAction(long runAt, Runnable task) {
    }

    private AnglerWorldMemory() {
    }

    public static void register() {
        OnGameServerTick.EVENT.register(AnglerWorldMemory::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity itemEntity && GameUtils.isGameRunning(world)) {
                recordItem(itemEntity.getItem());
            }
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!isFalseToothActive(sender)) {
                return true;
            }
            String scrambled = scramble(message.signedContent());
            Component fake = Component.translatable("chat.type.text", sender.getDisplayName(),
                    Component.literal(scrambled).withStyle(ChatFormatting.DARK_PURPLE));
            sender.server.getPlayerList().broadcastSystemMessage(fake, false);
            return false;
        });
    }

    public static void reset(ServerLevel level) {
        CATCH_SPOTS.clear();
        ITEM_HISTORY.clear();
        CONVERTED.clear();
        TICKET_USED.clear();
        TICKET_RETURNS.clear();
        FALSE_TOOTH_UNTIL.clear();
        RIDE_READY_AT.clear();
        WATCH_UNTIL.clear();
        HEART_UNTIL.clear();
        INK_PUDDLES.clear();
        DELAYED.clear();
        errorRodClaimed = false;
        echoSkin = null;
        pendingRespawnAt = null;
        if (level != null && level.getServer() != null) {
            for (ServerLevel world : level.getServer().getAllLevels()) {
                for (ErrorAnglerEntity echo : world.getEntities(ModEntities.ERROR_ANGLER, e -> true)) {
                    echo.discard();
                }
            }
        }
    }

    public static boolean isErrorRodClaimed() {
        return errorRodClaimed;
    }

    public static void markErrorRodClaimed() {
        errorRodClaimed = true;
        pendingRespawnAt = null;
    }

    public static UUID getEchoSkin() {
        return echoSkin;
    }

    public static void recordCatchSpot(Level level, BlockPos pos) {
        CatchSpot spot = new CatchSpot(level.dimension(), pos.immutable());
        for (CatchSpot existing : CATCH_SPOTS) {
            if (existing.distSqr(spot) < AnglerRules.CATCH_SPOT_DEDUP * AnglerRules.CATCH_SPOT_DEDUP) {
                return;
            }
        }
        CATCH_SPOTS.add(spot);
    }

    public static List<CatchSpot> catchSpots() {
        return CATCH_SPOTS;
    }

    public static void recordItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() == ModItems.ANGLER_ERROR_AIR) {
            return;
        }
        ITEM_HISTORY.addLast(stack.copy());
        while (ITEM_HISTORY.size() > AnglerRules.HISTORY_CAP) {
            ITEM_HISTORY.removeFirst();
        }
    }

    public static List<ItemStack> itemHistory() {
        return List.copyOf(ITEM_HISTORY);
    }

    public static boolean hasHistory() {
        return !ITEM_HISTORY.isEmpty();
    }

    public static boolean tryConvertFromDeath(Player victim) {
        if (!(victim instanceof ServerPlayer player) || player.level().isClientSide()) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning() || !game.isRole(player, BounsRoles.ANGLER)) {
            return false;
        }
        if (errorRodClaimed || !CONVERTED.add(player.getUUID())) {
            return false;
        }
        echoSkin = player.getUUID();
        if (CATCH_SPOTS.isEmpty()) {
            recordCatchSpot(player.level(), player.blockPosition());
        }
        spawnEcho(player.serverLevel(), CATCH_SPOTS.get(CATCH_SPOTS.size() - 1), echoSkin);
        return true;
    }

    public static void spawnEcho(ServerLevel level, CatchSpot spot, UUID skin) {
        if (errorRodClaimed || countEchoes(level.getServer()) > 0) {
            return;
        }
        ServerLevel target = level.getServer().getLevel(spot.dimension());
        if (target == null) {
            target = level;
        }
        ErrorAnglerEntity echo = ModEntities.ERROR_ANGLER.create(target);
        if (echo == null) {
            return;
        }
        echo.moveTo(spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5, 0, 0);
        echo.setup(skin);
        target.addFreshEntity(echo);
        pendingRespawnAt = null;
    }

    public static void scheduleEchoRespawn(ServerLevel level) {
        if (errorRodClaimed) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level);
        pendingRespawnAt = now + AnglerRules.randomPatrolInterval(level.random);
    }

    public static CatchSpot nextPatrolSpot(CatchSpot current, ServerLevel level) {
        if (CATCH_SPOTS.isEmpty()) {
            return current;
        }
        if (CATCH_SPOTS.size() == 1) {
            return CATCH_SPOTS.get(0);
        }
        List<CatchSpot> others = new ArrayList<>();
        for (CatchSpot spot : CATCH_SPOTS) {
            if (current == null || spot.distSqr(current) > 0.5) {
                others.add(spot);
            }
        }
        if (others.isEmpty()) {
            return CATCH_SPOTS.get(0);
        }
        return others.get(level.random.nextInt(others.size()));
    }

    public static boolean canRide(Player player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        Long ready = RIDE_READY_AT.get(player.getUUID());
        return ready == null || now >= ready;
    }

    public static void markDismount(Player player) {
        markDismount(player, AnglerRules.RIDE_COOLDOWN_TICKS);
    }

    public static void markDismount(Player player, int cooldownTicks) {
        if (player == null) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        RIDE_READY_AT.put(player.getUUID(), now + cooldownTicks);
    }

    public static boolean tryUseTicket(ServerPlayer player) {
        if (TICKET_USED.contains(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.ticket_used")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Vec3 from = player.position();
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        ResourceKey<Level> dim = player.level().dimension();
        GameUtils.teleportToRandomRoom(player);
        if (player.position().distanceToSqr(from) < 0.25) {
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.ticket_fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        TICKET_USED.add(player.getUUID());
        long returnTick = GameUtils.getTicksFromGameStart(player.level()) + AnglerRules.TICKET_RETURN_TICKS;
        TICKET_RETURNS.put(player.getUUID(), new PendingReturn(dim, from, yRot, xRot, returnTick));
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.ticket_go")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    public static void startFalseTooth(ServerPlayer player) {
        FALSE_TOOTH_UNTIL.put(player.getUUID(),
                GameUtils.getTicksFromGameStart(player.level()) + AnglerRules.TOOTH_TICKS);
    }

    public static boolean isFalseToothActive(Player player) {
        if (player == null) {
            return false;
        }
        Long until = FALSE_TOOTH_UNTIL.get(player.getUUID());
        return until != null && GameUtils.getTicksFromGameStart(player.level()) < until;
    }

    public static void startWatch(ServerPlayer player) {
        WATCH_UNTIL.put(player.getUUID(),
                GameUtils.getTicksFromGameStart(player.level()) + AnglerRules.WATCH_TICKS);
    }

    public static void startHeart(ServerPlayer player) {
        HEART_UNTIL.put(player.getUUID(),
                GameUtils.getTicksFromGameStart(player.level()) + AnglerRules.HEART_TICKS);
    }

    public static void addInkPuddle(ServerLevel level, Vec3 pos) {
        INK_PUDDLES.add(new InkPuddle(level.dimension(), pos,
                GameUtils.getTicksFromGameStart(level) + AnglerRules.INK_TICKS));
    }

    public static void delay(Level level, int ticks, Runnable task) {
        DELAYED.add(new DelayedAction(GameUtils.getTicksFromGameStart(level) + ticks, task));
    }

    public static void grantCarpCoins(ServerLevel level, Vec3 pos, boolean inverted) {
        int amount = inverted ? -AnglerRules.CARP_ERROR_COINS : AnglerRules.CARP_COINS;
        AABB box = new AABB(pos, pos).inflate(AnglerRules.CARP_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            MoneyUtils.addToBalance(player, amount);
            player.displayClientMessage(Component.translatable(
                    inverted ? "message.noellesroles.angler.carp_error" : "message.noellesroles.angler.carp_gift",
                    Math.abs(amount)).withStyle(inverted ? ChatFormatting.DARK_RED : ChatFormatting.GOLD), true);
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    private static void tick(ServerLevel level) {
        if (!GameUtils.isGameRunning(level)) {
            return;
        }
        if (SREGameTimeComponent.KEY.get(level).isTimeFrozen()) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (game == null || !game.isSkillAvailable) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level);
        tickCarpItems(level, now);
        tickTicketReturns(level, now);
        tickWatch(level, now);
        tickHeart(level, now);
        tickInk(level, now);
        tickDelayed(now);
        tickPendingRespawn(level, now);
        pruneExpired(now);
    }

    private static void tickCarpItems(ServerLevel level, long now) {
        for (ItemEntity item : level.getEntities(EntityType.ITEM,
                e -> e.getItem().getItem() == ModItems.ANGLER_LIVING_CARP)) {
            if (LivingCarpItem.tryKill(item.getItem(), now)) {
                Vec3 pos = item.position();
                boolean inverted = AnglerItemTags.isInverted(item.getItem());
                item.setItem(new ItemStack(ModItems.ANGLER_DEAD_CARP));
                grantCarpCoins(level, pos, inverted);
            }
        }
    }

    private static void tickTicketReturns(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, PendingReturn>> it = TICKET_RETURNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingReturn> entry = it.next();
            if (now < entry.getValue().returnTick()) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && GameUtils.isPlayerAliveAndSurvival(player)) {
                PendingReturn ret = entry.getValue();
                ServerLevel dest = player.server.getLevel(ret.dimension());
                if (dest != null) {
                    player.teleportTo(dest, ret.pos().x, ret.pos().y, ret.pos().z, ret.yRot(), ret.xRot());
                }
                player.displayClientMessage(Component.translatable("message.noellesroles.angler.ticket_back")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            it.remove();
        }
    }

    private static void tickWatch(ServerLevel level, long now) {
        for (ServerPlayer player : level.players()) {
            Long until = WATCH_UNTIL.get(player.getUUID());
            if (until == null || now >= until) {
                continue;
            }
            int fakeMin = player.getRandom().nextInt(60);
            int fakeSec = player.getRandom().nextInt(60);
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.fake_time",
                    fakeMin, fakeSec).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    private static void tickHeart(ServerLevel level, long now) {
        for (ServerPlayer player : level.players()) {
            Long until = HEART_UNTIL.get(player.getUUID());
            if (until == null || now >= until) {
                continue;
            }
            ServerPlayer nearest = nearestOther(player);
            if (nearest != null) {
                nearest.addEffect(ModEffects.of(MobEffects.GLOWING, 40, 0, false, false, true));
            }
            if (now % 20 == 0) {
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.7f);
            }
        }
    }

    private static void tickInk(ServerLevel level, long now) {
        INK_PUDDLES.removeIf(p -> now >= p.expireTick());
        for (InkPuddle puddle : INK_PUDDLES) {
            if (!puddle.dimension().equals(level.dimension())) {
                continue;
            }
            AABB box = new AABB(puddle.pos(), puddle.pos()).inflate(1.6);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box,
                    GameUtils::isPlayerAliveAndSurvival)) {
                player.addEffect(ModEffects.of(MobEffects.DARKNESS, 40, 0, false, true, true));
            }
        }
    }

    private static void tickDelayed(long now) {
        Iterator<DelayedAction> it = DELAYED.iterator();
        while (it.hasNext()) {
            DelayedAction action = it.next();
            if (now >= action.runAt()) {
                try {
                    action.task().run();
                } catch (Exception ignored) {
                }
                it.remove();
            }
        }
    }

    private static void tickPendingRespawn(ServerLevel level, long now) {
        if (errorRodClaimed || pendingRespawnAt == null || now < pendingRespawnAt) {
            return;
        }
        if (countEchoes(level.getServer()) > 0) {
            pendingRespawnAt = null;
            return;
        }
        CatchSpot spot = CATCH_SPOTS.isEmpty() ? null : nextPatrolSpot(null, level);
        if (spot != null) {
            spawnEcho(level, spot, echoSkin);
        }
        pendingRespawnAt = null;
    }

    private static void pruneExpired(long now) {
        FALSE_TOOTH_UNTIL.entrySet().removeIf(e -> now >= e.getValue());
        WATCH_UNTIL.entrySet().removeIf(e -> now >= e.getValue());
        HEART_UNTIL.entrySet().removeIf(e -> now >= e.getValue());
    }

    private static int countEchoes(MinecraftServer server) {
        int count = 0;
        for (ServerLevel world : server.getAllLevels()) {
            count += world.getEntities(ModEntities.ERROR_ANGLER, e -> true).size();
        }
        return count;
    }

    public static ServerPlayer nearestOther(ServerPlayer player) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double d = other.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        return nearest;
    }

    public static String scramble(String text) {
        if (text == null || text.isEmpty()) {
            return "????";
        }
        char[] chars = text.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = Math.floorMod(chars[i] * 31 + i, i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    public static String corruptName(String name) {
        if (name == null || name.isEmpty()) {
            return "????";
        }
        return name + name.charAt(name.length() - 1) + name.charAt(name.length() - 1);
    }
}

package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.network.SupplySearchS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 物资箱「搜刮」定时会话（搜打撤式）：右键物资箱不再即时给物资，而是开始一段
 * {@link SixtySecondsBalance#SUPPLY_SEARCH_TICKS} 的搜刮——客户端显示进度条动画，期间玩家须留在箱旁；
 * 离箱过远 / 死亡 / 箱被破坏则中断，完成才发放物资。物资仍走 {@link SupplyBoxBlockEntity#claim}（每人每刷新周期一次）。
 * <p>会话由全局 {@link ServerTickEvents#END_WORLD_TICK} 推进（{@link #register()}），
 * <b>不依赖 60s 相位机运行</b>——修复游戏未开局（如冒险模式踩点/测试）时进度条走满却永不发放的问题。
 */
public final class SixtySecondsLootSearch {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SixtySecondsLootSearch() {
    }

    private record Session(ResourceKey<Level> dimension, BlockPos pos, long endTick) {
    }

    /** 模组初始化时注册一次：每世界 tick 推进本世界的搜刮会话（与游戏相位无关）。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsLootSearch::tick);
    }

    /** 开始搜刮：已在搜刮则忽略；箱已被他人占用/无可领物资则提示后返回。 */
    public static void start(ServerLevel level, ServerPlayer player, SupplyBoxBlockEntity box, BlockPos pos) {
        UUID id = player.getUUID();
        if (SESSIONS.containsKey(id)) {
            return;
        }
        // 同一箱同时只允许一人搜刮：物资箱每日全局一次，后到者搜完也拿不到，直接拒绝开搜
        for (Session other : SESSIONS.values()) {
            if (other.dimension.equals(level.dimension()) && other.pos.equals(pos)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.sixty_seconds.supply_occupied"), true);
                return;
            }
        }
        if (!box.hasLootFor(level, player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.supply_empty"), true);
            return;
        }
        long end = level.getGameTime() + SixtySecondsBalance.SUPPLY_SEARCH_TICKS;
        SESSIONS.put(id, new Session(level.dimension(), pos.immutable(), end));
        ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                SupplySearchS2CPacket.STATE_START, pos, SixtySecondsBalance.SUPPLY_SEARCH_TICKS));
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F);
    }

    /** 每 tick 推进本世界的搜刮会话：判断中断条件，到点则完成并发放。 */
    private static void tick(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Session> e = it.next();
            Session session = e.getValue();
            if (!session.dimension.equals(level.dimension())) {
                continue; // 会话属于其它维度，由那个世界的 tick 处理
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            // 玩家掉线/死亡/旁观 → 中断
            if (player == null || !player.isAlive() || player.isSpectator()) {
                notify(player, session.pos, SupplySearchS2CPacket.STATE_CANCEL);
                it.remove();
                continue;
            }
            // 箱被破坏 → 中断
            if (!(level.getBlockEntity(session.pos) instanceof SupplyBoxBlockEntity box)) {
                cancel(player, session.pos);
                it.remove();
                continue;
            }
            // 离箱过远 → 中断
            double dx = player.getX() - (session.pos.getX() + 0.5);
            double dy = player.getY() - session.pos.getY();
            double dz = player.getZ() - (session.pos.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz > SixtySecondsBalance.SUPPLY_SEARCH_MAX_DIST_SQR) {
                cancel(player, session.pos);
                it.remove();
                continue;
            }
            // 完成 → 发放
            if (now >= session.endTick) {
                complete(level, player, box, session.pos);
                it.remove();
            }
        }
    }

    private static void complete(ServerLevel level, ServerPlayer player, SupplyBoxBlockEntity box, BlockPos pos) {
        List<ItemStack> items = box.claim(level, player);
        List<ItemStack> display = new java.util.ArrayList<>(items.size());
        for (ItemStack item : items) {
            display.add(item.copy()); // 先留展示副本；add() 会清空/合并原 stack
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
        }
        ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                SupplySearchS2CPacket.STATE_COMPLETE, pos, 0, display));
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.7F, 1.1F);
        if (items.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.supply_empty"), true);
        }
        // 一次性箱（空投奖励箱）：搜刮完成后整箱消失
        if (!items.isEmpty() && box.isOneShot()) {
            level.removeBlock(pos, false);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private static void cancel(ServerPlayer player, BlockPos pos) {
        notify(player, pos, SupplySearchS2CPacket.STATE_CANCEL);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.search_interrupted"), true);
    }

    private static void notify(ServerPlayer player, BlockPos pos, int state) {
        if (player != null) {
            ServerPlayNetworking.send(player, new SupplySearchS2CPacket(state, pos, 0));
        }
    }

    /** 玩家是否正在搜刮（避免重复触发）。 */
    public static boolean isSearching(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void reset() {
        SESSIONS.clear();
    }
}

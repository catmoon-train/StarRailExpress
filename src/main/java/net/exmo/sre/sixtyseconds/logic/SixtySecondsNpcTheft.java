package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.network.SupplySearchS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对旅者的偷窃 / 抢劫（服务端）。
 * <ul>
 *   <li><b>偷窃</b>：潜行右键 → 3 秒频道（<b>复用物资箱搜刮 HUD</b> {@link SupplySearchS2CPacket}，
 *       零新客户端代码）→ 按成功率结算。<b>成功</b>抽走一件随身物资且 NPC 不敌对
 *       （成功 = 没被发现，这是让偷窃有正收益的关键）；<b>失败</b>无 san 代价但 NPC 反击并记仇。</li>
 *   <li><b>抢劫</b>：明牌版，跳过潜行与概率，直接拿一件 + 立即敌对，代价与偷窃成功相同。</li>
 * </ul>
 * <b>代价</b>（仅成功偷窃/抢劫时）：san 直接 -{@link SixtySecondsBalance#NPC_STEAL_SANITY_LOSS}，
 * 理智上限<b>永久</b>随机扣 1~3——逐条照抄 {@code SixtySecondsHealthSystem.applyKillSanityCapLoss}
 * （含 {@link SixtySecondsBalance#SANITY_CAP_FLOOR} 保底，防连偷锁死到 0）。
 * <p>⚠ san 归零会触发 {@code SixtySecondsMonsterSystem} 的变怪倒计时——这是<b>刻意的代价</b>，不做保护。
 */
public final class SixtySecondsNpcTheft {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SixtySecondsNpcTheft() {
    }

    private record Session(ResourceKey<Level> dimension, int npcId, long endTick) {
    }

    /** 偷窃成功率（0~100，服务端算好下发给菜单显示；结算时重算，不信客户端）。 */
    public static int successPercent(ServerPlayer player, SixtySecondsNpcEntity npc) {
        return Math.round(successChance(player, npc) * 100.0F);
    }

    /**
     * 成功率：基础 55%，NPC 没看向玩家 +15%，玩家在其正面 4 格内 -20%，最后 clamp 到 10%~90%。
     * 「看向」用 NPC 朝向与「NPC→玩家」向量的夹角判断（点积 > 0.5 ≈ 正面 60° 锥内）。
     */
    private static float successChance(ServerPlayer player, SixtySecondsNpcEntity npc) {
        float chance = SixtySecondsBalance.NPC_STEAL_BASE_CHANCE;
        Vec3 toPlayer = player.position().subtract(npc.position());
        boolean facing = false;
        if (toPlayer.lengthSqr() > 1.0E-4) {
            Vec3 look = npc.getLookAngle().normalize();
            double dot = look.dot(toPlayer.normalize());
            facing = dot > 0.5;
        }
        if (!facing) {
            chance += SixtySecondsBalance.NPC_STEAL_BEHIND_BONUS;
        } else if (npc.distanceToSqr(player) <= 4 * 4) {
            chance -= SixtySecondsBalance.NPC_STEAL_FRONT_PENALTY;
        }
        return Mth.clamp(chance, SixtySecondsBalance.NPC_STEAL_MIN_CHANCE,
                SixtySecondsBalance.NPC_STEAL_MAX_CHANCE);
    }

    /** 开始偷窃频道（复用搜刮 HUD 画进度条）。 */
    public static void startSteal(ServerPlayer player, SixtySecondsNpcEntity npc) {
        ServerLevel level = player.serverLevel();
        if (!canTarget(level, npc) || SESSIONS.containsKey(player.getUUID())) {
            return;
        }
        long end = level.getGameTime() + SixtySecondsBalance.NPC_STEAL_TICKS;
        SESSIONS.put(player.getUUID(), new Session(level.dimension(), npc.getId(), end));
        ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                SupplySearchS2CPacket.STATE_START, npc.blockPosition(),
                SixtySecondsBalance.NPC_STEAL_TICKS));
    }

    /** 抢劫：明牌版，跳过潜行与概率——直接拿 + 立即敌对，代价与偷窃成功相同。 */
    public static void rob(ServerPlayer player, SixtySecondsNpcEntity npc) {
        ServerLevel level = player.serverLevel();
        if (!canTarget(level, npc)) {
            return;
        }
        giveLoot(player, npc);
        applyTheftCost(player, level);
        npc.setHostile(true);
        npc.addAngryAt(player.getUUID());
        npc.onAttackedBy(player);
        npc.setTarget(player);
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** 目标合法性：模式激活 + 白天 + 是旅者 + 活着 + 未记仇（记仇的不给偷，直接打）。 */
    private static boolean canTarget(ServerLevel level, SixtySecondsNpcEntity npc) {
        return SixtySecondsMod.isActive(level)
                && SixtySecondsState.get(level).phase == SixtySecondsPhase.DAY
                && npc.isAlive()
                && npc.getVariant() == SixtySecondsNpcEntity.Variant.TRAVELER
                && !npc.isHostile();
    }

    /** 每 tick 推进偷窃会话（由 {@code SixtySecondsNpcSystem.tick} 调用）。 */
    public static void tick(ServerLevel level) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Session> e = it.next();
            Session session = e.getValue();
            if (!session.dimension.equals(level.dimension())) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            SixtySecondsNpcEntity npc = level.getEntity(session.npcId) instanceof SixtySecondsNpcEntity found
                    ? found : null;
            // 中断：NPC 没了/死了/已敌对，玩家死亡或倒地，或走远了
            boolean broken = npc == null || !npc.isAlive() || npc.isHostile()
                    || !player.isAlive() || stats.downed
                    || npc.distanceToSqr(player) > SixtySecondsBalance.NPC_STEAL_MAX_DISTANCE_SQR;
            if (broken) {
                it.remove();
                ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                        SupplySearchS2CPacket.STATE_CANCEL, player.blockPosition(), 0));
                continue;
            }
            if (now < session.endTick) {
                continue;
            }
            it.remove();
            resolveSteal(level, player, npc);
        }
    }

    /** 频道走完：掷骰判定成功/失败。 */
    private static void resolveSteal(ServerLevel level, ServerPlayer player, SixtySecondsNpcEntity npc) {
        // 成功率结算时重算（开屏时下发的 param 只用于显示，不能信）
        boolean success = level.getRandom().nextFloat() < successChance(player, npc);
        if (!success) {
            // 失败：无 san 代价，但被发现——反击 + 记仇 + 传染给附近同类
            ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                    SupplySearchS2CPacket.STATE_CANCEL, npc.blockPosition(), 0));
            npc.setHostile(true);
            npc.addAngryAt(player.getUUID());
            npc.onAttackedBy(player);
            npc.setTarget(player);
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.steal_fail")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack loot = giveLoot(player, npc);
        // 成功 = 没被发现：NPC 不敌对（否则偷窃永远是净亏，没人会玩）
        ServerPlayNetworking.send(player, new SupplySearchS2CPacket(
                SupplySearchS2CPacket.STATE_COMPLETE, npc.blockPosition(), 0,
                loot.isEmpty() ? List.of() : List.of(loot)));
        applyTheftCost(player, level);
    }

    /** 抽走一件随身物资给玩家；随身空则给 1~3 枚实体币保证获得感。 */
    private static ItemStack giveLoot(ServerPlayer player, SixtySecondsNpcEntity npc) {
        ItemStack loot = npc.getCarry().takeRandom(player.serverLevel().getRandom());
        if (loot.isEmpty()) {
            loot = new ItemStack(ModItems.SIXTY_SECONDS_COIN,
                    1 + player.serverLevel().getRandom().nextInt(3));
        }
        ItemStack give = loot.copy();
        if (!player.getInventory().add(give)) {
            player.drop(give, false);
        }
        return loot;
    }

    /**
     * 偷窃/抢劫的理智代价：san 直接扣，理智上限<b>永久</b>随机扣 1~3。
     * 逐条照抄 {@code SixtySecondsHealthSystem.applyKillSanityCapLoss}（含保底与红字提示）。
     */
    private static void applyTheftCost(ServerPlayer player, ServerLevel level) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.sanity = Math.max(0, stats.sanity - SixtySecondsBalance.NPC_STEAL_SANITY_LOSS);
        int loss = SixtySecondsBalance.NPC_STEAL_SANITY_CAP_MIN + level.getRandom().nextInt(
                SixtySecondsBalance.NPC_STEAL_SANITY_CAP_MAX
                        - SixtySecondsBalance.NPC_STEAL_SANITY_CAP_MIN + 1);
        int before = stats.sanityMax;
        stats.sanityMax = Math.max(SixtySecondsBalance.SANITY_CAP_FLOOR, stats.sanityMax - loss);
        if (stats.sanity > stats.sanityMax) {
            stats.sanity = stats.sanityMax;
        }
        stats.sync();
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.steal_sanity",
                SixtySecondsBalance.NPC_STEAL_SANITY_LOSS, before - stats.sanityMax, stats.sanityMax)
                .withStyle(ChatFormatting.DARK_RED), false);
    }

    /** 局末清理：清空会话表（挂 {@code SixtySecondsGameMode.stopGame}）。 */
    public static void reset() {
        SESSIONS.clear();
    }
}

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 更夫（平民阵营）的职业数据与全部规则实现。
 *
 * <ul>
 * <li><b>敲钟</b>（技能键，CD 90 秒）：使周围玩家短暂透视游戏时间 15 秒。</li>
 * <li><b>锣</b>（一次性道具）：使周围「非平民 / 非警长」阵营玩家 10 秒内无法使用技能与背包。</li>
 * <li><b>梆</b>（一次性道具）：持续 10 秒，每 2 秒按周围玩家人数扣减游戏时间（每名 4 秒，单次至多 1 分钟），
 * 并使周围玩家获得 1 分钟「入梦」。</li>
 * </ul>
 *
 * 道具冷却：固定 120 秒（不随购买次数增加）；价格每购买一次 +25。
 */
public class WatchmanRoleData extends SimpleRoleData {

    /** 敲钟 / 锣 / 梆 的作用半径（格）。 */
    public static final double RADIUS = 15.0D;

    /** 敲钟：使周围玩家透视游戏时间的时长。 */
    public static final int BELL_REVEAL_TICKS = GameConstants.getInTicks(0, 15);
    /** 敲钟技能冷却（秒）。 */
    public static final int BELL_COOLDOWN_SECONDS = 90;

    /** 锣：封禁技能 / 背包的时长。 */
    public static final int GONG_BAN_TICKS = GameConstants.getInTicks(0, 10);
    /** 锣的基础冷却（秒）。 */
    public static final int GONG_BASE_COOLDOWN_SECONDS = 120;

    /** 梆：持续时间。 */
    public static final int BANG_DURATION_TICKS = GameConstants.getInTicks(0, 10);
    /** 梆：结算间隔（每 2 秒一次）。 */
    public static final int BANG_INTERVAL_TICKS = GameConstants.getInTicks(0, 2);
    /** 梆：每名周围玩家每次结算减少的游戏时间（4 秒）。 */
    public static final int BANG_TIME_PER_PLAYER_TICKS = GameConstants.getInTicks(0, 4);
    /**
     * 梆：本局**合计**最多能减少的游戏时间（1 分钟）。
     * 注意是跨多次使用的总量上限：一旦累计减少达到 1 分钟，之后再敲梆都不会再减少时间
     * （但仍然会敲击出声、并给予周围玩家入梦）。
     */
    public static final int BANG_MAX_REDUCTION_TICKS = GameConstants.getInTicks(1, 0);
    /** 梆：给予周围玩家的入梦时长（1 分钟）。 */
    public static final int BANG_DREAM_TICKS = GameConstants.getInTicks(1, 0);
    /** 梆的基础冷却（秒）。 */
    public static final int BANG_BASE_COOLDOWN_SECONDS = 120;

    // ==================== 梆的持续结算状态 ====================
    /** 梆剩余持续时间。 */
    public int bangTicksLeft;
    /** 距离下一次时间结算还有多少 tick。 */
    public int bangNextIn;
    /**
     * 本局使用梆已累计扣减的游戏时间（跨多次使用累计）。
     * 达到 {@link #BANG_MAX_REDUCTION_TICKS} 后不再扣减时间。
     */
    public int bangTotalReducedTicks;

    public WatchmanRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        return serverPlayer == this.player;
    }

    // ==================== 敲钟 ====================

    /**
     * 敲钟：使周围玩家短暂透视游戏时间。
     *
     * @return 是否成功消耗技能（false = 不进冷却）
     */
    public boolean ringBell() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning() || !gameWorld.isRole(serverPlayer, ModRoles.WATCHMAN)
                || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return false;
        }

        int affected = 0;
        for (ServerPlayer target : nearbyPlayers(serverPlayer)) {
            target.addEffect(new MobEffectInstance(ModEffects.TIME_REVEAL, BELL_REVEAL_TICKS, 0,
                    false, false, true));
            affected++;
        }

        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.7F);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.watchman.bell_used", affected)
                        .withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    // ==================== 锣 ====================

    /**
     * 使用锣：封禁周围「非平民 / 非警长」阵营玩家的技能与背包。
     *
     * @return 是否成功使用（false = 不消耗物品）
     */
    public boolean useGong(ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }

        for (ServerPlayer target : nearbyPlayers(serverPlayer)) {
            if (!isNonInnocentNonVigilante(target)) {
                continue;
            }
            // 参考破法者：给予技能/背包封印；不给滤镜效果，也不显示气泡粒子
            target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, GONG_BAN_TICKS, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, GONG_BAN_TICKS, 0,
                    false, false, true));
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.watchman.gong_hit")
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        }

        serverPlayer.getCooldowns().addCooldown(stack.getItem(), itemCooldownTicks(stack.getItem()));
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        // 使用锣时不给使用者显示「压制了多少人」的 actionbar 提示
        return true;
    }

    // ==================== 梆 ====================

    /**
     * 使用梆：开始持续 10 秒的时间扣减，并给予周围玩家 1 分钟入梦。
     *
     * @return 是否成功使用（false = 不消耗物品）
     */
    public boolean useBang(ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }

        this.bangTicksLeft = BANG_DURATION_TICKS;
        this.bangNextIn = BANG_INTERVAL_TICKS;

        // 本局梆的时间扣减总量已用尽：仍然可以敲（给周围玩家入梦），但不会再减少时间
        if (bangTotalReducedTicks >= BANG_MAX_REDUCTION_TICKS) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.watchman.bang_no_time")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }

        int affected = 0;
        for (ServerPlayer target : nearbyPlayers(serverPlayer)) {
            target.addEffect(new MobEffectInstance(ModEffects.ENTER_DREAM, BANG_DREAM_TICKS, 0,
                    false, false, true));
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.watchman.bang_hit")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
            affected++;
        }

        serverPlayer.getCooldowns().addCooldown(stack.getItem(), itemCooldownTicks(stack.getItem()));
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
                SoundSource.PLAYERS, 1.2F, 0.8F);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.watchman.bang_used", affected)
                        .withStyle(ChatFormatting.GOLD),
                true);
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (bangTicksLeft <= 0) {
            return;
        }
        bangTicksLeft--;
        if (bangNextIn > 0) {
            bangNextIn--;
        }
        if (bangNextIn <= 0) {
            bangNextIn = BANG_INTERVAL_TICKS;
            strikeBang();
        }
        if (bangTicksLeft <= 0) {
            bangNextIn = 0;
            sync();
        }
    }

    /** 每 2 秒敲击一次：发出敲钟声，并按周围玩家人数扣减游戏时间（本局合计至多减少 1 分钟）。 */
    private void strikeBang() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 每 2 秒的敲击音效
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);

        int remaining = BANG_MAX_REDUCTION_TICKS - bangTotalReducedTicks;
        if (remaining <= 0) {
            return;
        }
        int count = nearbyPlayers(serverPlayer).size();
        if (count <= 0) {
            return;
        }
        int reduce = Math.min(remaining, BANG_TIME_PER_PLAYER_TICKS * count);
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(level);
        gameTime.setTime(Math.max(0, gameTime.getTime() - reduce));
        bangTotalReducedTicks += reduce;
    }

    // ==================== 工具方法 ====================

    /** 把 {@link #RADIUS} 内所有存活玩家（含自己）收集起来。 */
    public static List<ServerPlayer> nearbyPlayers(ServerPlayer source) {
        List<ServerPlayer> result = new ArrayList<>();
        ServerLevel level = source.serverLevel();
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class,
                source.getBoundingBox().inflate(RADIUS), GameUtils::isPlayerAliveAndSurvival)) {
            if (target.distanceToSqr(source) <= RADIUS * RADIUS) {
                result.add(target);
            }
        }
        return result;
    }

    /** 目标是否属于「非平民 / 非警长」阵营（即杀手与中立）。 */
    public static boolean isNonInnocentNonVigilante(ServerPlayer target) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(target.level());
        return !gameWorld.isInnocent(target) && !gameWorld.isVigilanteTeam(target);
    }

    /** 道具冷却：固定 120 秒，不随购买次数增加。 */
    public static int itemCooldownTicks(Item item) {
        int seconds = item == ModItems.GONG ? GONG_BASE_COOLDOWN_SECONDS : BANG_BASE_COOLDOWN_SECONDS;
        return seconds * 20;
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("bangTicksLeft", bangTicksLeft);
        tag.putInt("bangNextIn", bangNextIn);
        tag.putInt("bangTotalReducedTicks", bangTotalReducedTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        bangTicksLeft = tag.getInt("bangTicksLeft");
        bangNextIn = tag.getInt("bangNextIn");
        bangTotalReducedTicks = tag.getInt("bangTotalReducedTicks");
    }
}

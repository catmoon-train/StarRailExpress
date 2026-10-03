package org.agmas.noellesroles.katana;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.item.KatanaItem;
import org.agmas.noellesroles.content.item.RiotShieldHandler;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 武士刀的服务端战斗结算：三连招（横扫 / 突刺 / 劈砍）。
 *
 * <p>招式只在<b>实际命中玩家</b>后按顺序推进（未命中保持当前招式）。
 * 每次命中都会：
 * <ul>
 * <li>造成 1 点原版伤害作为击退载体（虚拟血量的扣除建立在原版伤害实际生效之上）；</li>
 * <li>扣除目标虚拟血量（横扫 6 / 突刺 7 / 劈砍 7），归零按「武士刀」死因判死；</li>
 * <li>每次挥刀（无论是否命中）按当前招式播放音效；</li>
 * <li>命中后刷新衔接格挡窗口（0 前摇）并清除格挡内置冷却。</li>
 * </ul>
 *
 * <p>突刺（第二招式）例外：左键时立刻向前位移，伤害与连招推进改由
 * <b>位移途中碰撞到的玩家</b>结算（见 {@link #tickThrusts}）。
 *
 * <p>三连招全部命中且目标<b>未死</b>（例如伤害被护盾挡下）→ 不进入物品冷却，
 * 立即衔接回第一招式；目标<b>被杀死</b> → 武士刀进入 10 秒物品冷却。
 */
public final class KatanaCombat {

    private KatanaCombat() {
    }

    static {
        // 突刺位移的逐 tick 结算（途中碰撞伤害、撞墙 / 停滞结束、连招推进）
        OnGameServerTick.EVENT.register(KatanaCombat::tickThrusts);
        // 被武士刀杀死 → 武士刀进入 10 秒原版物品冷却。
        // 注册在「死亡确认后」事件上（参考网警 Dream 武器的做法）：
        // inline 在攻击结算里检查目标存活不可靠——目标带时间回溯标记（TIME_REWIND_MARK）
        // 或人格分裂修饰符时，即使已被击杀，GameUtils#isPlayerAliveAndSurvival 仍返回
        // 存活（见 GameUtils#isPlayerReallyAliveOrDead），导致玩家已死但刀不进冷却。
        OnPlayerDeathWithKiller.EVENT.register(KatanaCombat::onKillWithKatana);
    }

    /**
     * 死亡确认回调：死因为武士刀、且击杀者主手持刀时，给武士刀加 10 秒原版物品冷却。
     */
    private static void onKillWithKatana(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(killer instanceof ServerPlayer sk) || victim == killer) {
            return;
        }
        if (!KatanaItem.DEATH_REASON.equals(deathReason)) {
            return;
        }
        if (!sk.isCreative() && sk.getMainHandItem().is(ModItems.KATANA)) {
            sk.getCooldowns().addCooldown(ModItems.KATANA, KatanaState.KILL_COOLDOWN_TICKS);
        }
    }

    /** 突刺时给自身的向前冲量（约 2 格位移）。 */
    private static final double THRUST_IMPULSE = 0.9D;

    /** 左键攻击玩家的服务端入口（由 {@link KatanaItem#onServerAttack} 分派）。 */
    public static boolean attack(ServerPlayer attacker, ServerPlayer target, ItemStack stack) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        // 武士刀只有开启了 canUseSpVanillaWeapon 的职业才能使用
        var gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        var role = gameWorld == null ? null : gameWorld.getRole(attacker);
        if (role == null || !role.canUseSpVanillaWeapon()) {
            return false;
        }
        // 物品冷却中（击杀后的 10 秒冷却）无法攻击
        if (attacker.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        // 与原版剑一致：必须满蓄力
        if (attacker.getAttackStrengthScale(0.5F) < 1.0F) {
            return false;
        }

        KatanaState.PlayerState state = KatanaState.get(attacker);
        int move = state.nextMove;

        // 广播「本次使用的招式」供客户端播放动画（未命中也要有动画）
        KatanaState.broadcast(attacker, (byte) (KatanaState.EVENT_MOVE_USED_BASE + move));
        attacker.resetAttackStrengthTicker();

        // 每次挥刀（无论是否命中）都按当前招式播放音效
        SoundEvent swingSound = switch (move) {
            case KatanaState.MOVE_SWEEP -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case KatanaState.MOVE_THRUST -> SoundEvents.PLAYER_ATTACK_KNOCKBACK;
            default -> SoundEvents.PLAYER_ATTACK_CRIT;
        };
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, attacker.blockPosition(), swingSound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        // 突刺（第二招式）：左键时立刻向前位移，伤害不再在挥刀瞬间对准星目标结算，
        // 而是改由位移途中碰撞到的玩家结算（见 tickThrusts / tickThrust）。
        if (move == KatanaState.MOVE_THRUST) {
            startThrustDash(attacker, state);
            return false;
        }

        // ── 命中判定：目标即原版选中的玩家 ──
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            broadcastNextMove(attacker, move);
            return false;
        }

        // 格挡判定：目标的武士刀格挡有效窗口优先，其次防暴盾牌
        if (KatanaHandler.tryBlockAttack(target, attacker)
                || RiotShieldHandler.tryBlockAttack(target, attacker)) {
            // 与「被原版盾牌格挡」一致，保持当前招式不推进
            broadcastNextMove(attacker, move);
            return false;
        }

        // 1. 1 点原版伤害（击退载体）：虚拟血量的扣除建立在原版伤害实际生效之上
        target.invulnerableTime = 0;
        boolean vanillaHurt = target.hurt(target.damageSources().playerAttack(attacker), 1.0F);
        if (vanillaHurt && GameUtils.isPlayerAliveAndSurvival(target)) {
            // 追加击退：横扫 0.5 格 / 突刺不追加 / 劈砍 1.5 格
            float knockback = switch (move) {
                case KatanaState.MOVE_SWEEP -> 0.5F;
                case KatanaState.MOVE_SLASH -> 1.5F;
                default -> 0.0F;
            };
            if (knockback > 0.0F && GameUtils.isPlayerAliveAndSurvival(target)) {
                target.knockback(knockback,
                        Math.sin(attacker.getYRot() * (Math.PI / 180.0D)),
                        -Math.cos(attacker.getYRot() * (Math.PI / 180.0D)));
            }
        }

        // 2. 虚拟血量伤害（仅在原版伤害命中的前提下扣除）
        if (vanillaHurt && GameUtils.isPlayerAliveAndSurvival(target)) {
            int virtualDamage = switch (move) {
                case KatanaState.MOVE_SWEEP -> 6;
                case KatanaState.MOVE_THRUST -> 7;
                default -> 7;
            };
            DreamHealthComponent.KEY.get(target).hurt(attacker, virtualDamage, KatanaItem.DEATH_REASON);
        }

        // 3. 连招推进：仅在实际命中后推进（未命中保持当前招式）
        boolean targetDied = vanillaHurt && !GameUtils.isPlayerAliveAndSurvival(target);
        int next;
        if (targetDied) {
            // 被武士刀杀死：连招回到第一招式。
            // 10 秒击杀冷却统一由 OnPlayerDeathWithKiller 事件在死亡确认后结算——
            // inline 检查 isPlayerAliveAndSurvival 不可靠（时间回溯标记 / 人格分裂
            // 状态下目标已死仍会返回"存活"）。
            next = KatanaState.MOVE_SWEEP;
        } else if (!vanillaHurt) {
            // 未命中（如被原版盾牌格挡）：保持当前招式，不推进也不回卷
            next = move;
        } else if (move == KatanaState.MOVE_SLASH) {
            // 三连招全部命中且目标未死（可能被护盾挡下）→ 无冷却，立即衔接第一招式
            next = KatanaState.MOVE_SWEEP;
        } else {
            next = move + 1;
        }
        state.nextMove = next;
        broadcastNextMove(attacker, next);

        // 5. 命中后：刷新衔接格挡窗口 + 清除格挡内置冷却
        if (vanillaHurt) {
            state.linkedUntil = attacker.level().getGameTime() + KatanaState.LINKED_WINDOW_TICKS;
            state.blockCooldownUntil = 0;
            KatanaState.broadcast(attacker, KatanaState.EVENT_BLOCK_COOLDOWN_CLEAR);
        }
        return false;
    }

    // ───────────────────────── 突刺（第二招式）位移结算 ─────────────────────────

    /** 第二招式突刺：左键时立刻沿视线方向位移，并进入逐 tick 的碰撞伤害结算。 */
    private static void startThrustDash(ServerPlayer attacker, KatanaState.PlayerState state) {
        Vec3 look = attacker.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0E-4) {
            return;
        }
        state.thrustDashing = true;
        state.thrustDirection = look.normalize();
        state.thrustTicksLeft = KatanaState.THRUST_DASH_MAX_TICKS;
        state.thrustHitCount = 0;
        state.thrustHasMoved = false;
        state.thrustLastPos = Vec3.ZERO;
        state.thrustHitPlayers.clear();
        attacker.push(state.thrustDirection.x * THRUST_IMPULSE, 0.0D, state.thrustDirection.z * THRUST_IMPULSE);
        attacker.hurtMarked = true;
        // 突刺期间给予短暂无碰撞，保证能穿过玩家
        attacker.addEffect(new MobEffectInstance(
                ModEffects.NO_COLLIDE, KatanaState.THRUST_DASH_MAX_TICKS, 0, true, false, false));
    }

    /** 每 tick 遍历本世界内正在突刺的玩家，结算位移与碰撞伤害。 */
    private static void tickThrusts(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            KatanaState.PlayerState state = KatanaState.get(player);
            if (!state.thrustDashing) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                state.stopThrust();
                continue;
            }
            tickThrust(player, state);
        }
    }

    /**
     * 单 tick 突刺结算：即将撞墙或位移停滞时结束突刺；
     * 位移途中对碰撞到的玩家造成伤害（1 点原版伤害 + 突刺虚拟伤害）。
     */
    private static void tickThrust(ServerPlayer player, KatanaState.PlayerState state) {
        Vec3 currentPos = player.position();
        if (state.thrustLastPos == Vec3.ZERO) {
            state.thrustLastPos = currentPos;
        }
        Vec3 moved = currentPos.subtract(state.thrustLastPos);
        boolean movedThisTick = moved.lengthSqr() > 0.0025D;

        // 前方即将撞墙：立即结束突刺
        Vec3 lookAhead = state.thrustDirection.scale(Math.max(THRUST_IMPULSE, 0.35D));
        BlockHitResult wallHit = player.level().clip(new ClipContext(
                currentPos.add(0, 0.5, 0), currentPos.add(lookAhead).add(0, 0.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wallHit.getType() != HitResult.Type.MISS
                && wallHit.getLocation().distanceToSqr(currentPos.add(0, 0.5, 0)) < THRUST_IMPULSE * THRUST_IMPULSE) {
            player.setDeltaMovement(Vec3.ZERO);
            endThrust(player, state);
            return;
        }

        // 位移停滞（被卡住等）：结束突刺
        if (state.thrustHasMoved && !movedThisTick) {
            player.setDeltaMovement(Vec3.ZERO);
            endThrust(player, state);
            return;
        }

        if (movedThisTick) {
            state.thrustHasMoved = true;
            // 位移途中碰撞到的玩家：按最近距离依次结算伤害（扫掠盒 = 本 tick 位移路径）
            var sweptBox = player.getBoundingBox()
                    .expandTowards(-moved.x, -moved.y, -moved.z)
                    .inflate(KatanaState.THRUST_HIT_MARGIN);
            // Level#players() 的返回类型是 List<? extends Player>，元素类型是捕获通配符，
            // 直接 toList() 得到的 List<CAP> 不能按 ServerPlayer 遍历，必须先收敛成 ServerPlayer。
            List<ServerPlayer> targets = player.level().players().stream()
                    .filter(ServerPlayer.class::isInstance)
                    .map(ServerPlayer.class::cast)
                    .filter(t -> !t.equals(player))
                    .filter(GameUtils::isPlayerAliveAndSurvival)
                    .filter(t -> !state.thrustHitPlayers.contains(t.getUUID()))
                    .filter(t -> sweptBox.intersects(t.getBoundingBox()))
                    .sorted((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                    .toList();
            for (ServerPlayer target : targets) {
                state.thrustHitPlayers.add(target.getUUID());
                // 格挡判定：目标的武士刀格挡有效窗口优先，其次防暴盾牌；
                // 被挡下不算「实际命中」，连招不推进
                if (KatanaHandler.tryBlockAttack(target, player)
                        || RiotShieldHandler.tryBlockAttack(target, player)) {
                    continue;
                }
                state.thrustHitCount++;
                dealThrustDamage(player, target);
            }
        }

        state.thrustLastPos = currentPos;
        state.thrustTicksLeft--;
        if (state.thrustTicksLeft <= 0) {
            endThrust(player, state);
        }
    }

    /** 突刺碰撞伤害：1 点原版伤害（击退载体）+ 突刺虚拟伤害，死因为武士刀。 */
    private static void dealThrustDamage(ServerPlayer attacker, ServerPlayer target) {
        target.invulnerableTime = 0;
        boolean vanillaHurt = target.hurt(target.damageSources().playerAttack(attacker), 1.0F);
        if (vanillaHurt && GameUtils.isPlayerAliveAndSurvival(target)) {
            DreamHealthComponent.KEY.get(target).hurt(attacker, KatanaState.THRUST_VIRTUAL_DAMAGE,
                    KatanaItem.DEATH_REASON);
        }
    }

    /** 结束突刺：碰撞过玩家则推进到第三招式，未碰撞保持第二招式（可再次突刺）。 */
    private static void endThrust(ServerPlayer player, KatanaState.PlayerState state) {
        boolean hit = state.thrustHitCount > 0;
        state.stopThrust();
        if (hit && state.nextMove == KatanaState.MOVE_THRUST) {
            state.nextMove = KatanaState.MOVE_SLASH;
        }
        broadcastNextMove(player, state.nextMove);
    }

    /** 广播「下一招式」预测，供客户端在下一次攻击时无延迟地播放正确动画。 */
    private static void broadcastNextMove(ServerPlayer attacker, int nextMove) {
        KatanaState.broadcast(attacker, (byte) (KatanaState.EVENT_NEXT_MOVE_BASE + nextMove));
    }
}

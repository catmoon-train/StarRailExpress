package org.agmas.noellesroles.katana;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.item.KatanaItem;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModItems;

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
 * <p>三连招全部命中且目标<b>未死</b>（例如伤害被护盾挡下）→ 不进入物品冷却，
 * 立即衔接回第一招式；目标<b>被杀死</b> → 武士刀进入 10 秒物品冷却。
 */
public final class KatanaCombat {

    private KatanaCombat() {
    }

    /** 突刺时给自身的向前冲量（约 2 格位移）。 */
    private static final double THRUST_IMPULSE = 0.9D;

    /** 突刺期间的无碰撞时长（tick）：0.5 秒，覆盖整个突进位移。 */
    private static final int THRUST_NO_COLLIDE_TICKS = 10;

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

        // 突刺：整个身体向前突刺约 2 格（可穿过玩家）
        if (move == KatanaState.MOVE_THRUST) {
            Vec3 look = attacker.getViewVector(1.0F);
            attacker.push(look.x * THRUST_IMPULSE, 0.0D, look.z * THRUST_IMPULSE);
            attacker.hurtMarked = true;
            // 突刺期间给予短暂无碰撞，保证能穿过玩家
            attacker.addEffect(new MobEffectInstance(
                    ModEffects.NO_COLLIDE, THRUST_NO_COLLIDE_TICKS, 0, true, false, false));
        }

        // ── 命中判定：目标即原版选中的玩家 ──
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
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

        // 3. 连招推进：命中才进入下一招式
        boolean targetDied = !GameUtils.isPlayerAliveAndSurvival(target);
        int next;
        if (targetDied) {
            // 击杀玩家：进入 10 秒冷却，连招回到第一招式
            if (!attacker.isCreative()) {
                attacker.getCooldowns().addCooldown(stack.getItem(), KatanaState.KILL_COOLDOWN_TICKS);
            }
            next = KatanaState.MOVE_SWEEP;
        } else if (move == KatanaState.MOVE_SLASH) {
            // 三连招全部命中且目标未死（可能被护盾挡下）→ 无冷却，立即衔接第一招式
            next = KatanaState.MOVE_SWEEP;
        } else {
            next = move + 1;
        }
        state.nextMove = next;
        broadcastNextMove(attacker, next);

        // 5. 命中后：刷新衔接格挡窗口 + 清除格挡内置冷却
        state.linkedUntil = attacker.level().getGameTime() + KatanaState.LINKED_WINDOW_TICKS;
        state.blockCooldownUntil = 0;
        KatanaState.broadcast(attacker, KatanaState.EVENT_BLOCK_COOLDOWN_CLEAR);
        return false;
    }

    /** 广播「下一招式」预测，供客户端在下一次攻击时无延迟地播放正确动画。 */
    private static void broadcastNextMove(ServerPlayer attacker, int nextMove) {
        KatanaState.broadcast(attacker, (byte) (KatanaState.EVENT_NEXT_MOVE_BASE + nextMove));
    }
}

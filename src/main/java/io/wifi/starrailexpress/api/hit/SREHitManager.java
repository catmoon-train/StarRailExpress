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

package io.wifi.starrailexpress.api.hit;

import io.wifi.starrailexpress.event.OnEntityWeaponHit;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 统一武器瞄准与非玩家实体命中结算。
 *
 * <p>客户端用 {@link #getTarget} 替代各物品里散落的 {@code instanceof} 射线；
 * 服务端用 {@link #tryHit} 触发 {@link IsTargetObject#onWeaponHit} 与
 * {@link OnEntityWeaponHit}，这样绊线 / 亡灵 / 傀儡等只需实现接口。
 */
public final class SREHitManager {

    private static final List<TargetFilter> EXTRA_FILTERS = new CopyOnWriteArrayList<>();

    private SREHitManager() {
    }

    /**
     * 额外瞄准过滤器：无法给实体实现 {@link IsTargetObject} 时使用。
     * 返回 {@code null} 表示本过滤器不认这个实体。
     */
    @FunctionalInterface
    public interface TargetFilter {
        @Nullable
        HitPriority test(Entity entity, Player attacker, HitType type);
    }

    /**
     * 射线追踪器。默认走 {@link ProjectileUtil#getHitResultOnViewVector}；
     * 狙击枪可传入穿透屏障的实现。
     */
    @FunctionalInterface
    public interface HitTracer {
        HitResult trace(Entity shooter, Predicate<Entity> filter, double range);
    }

    public static void registerTargetFilter(TargetFilter filter) {
        EXTRA_FILTERS.add(filter);
    }

    /**
     * 统一 getTarget：先拾取玩家与 {@link HitPriority#PRIMARY} 实体，
     * 未命中时再拾取 {@link HitPriority#FALLBACK}（绊线）。
     */
    public static HitResult getTarget(Player user, HitType type, double range) {
        return getTarget(user, type, range, (shooter, filter, r) -> ProjectileUtil.getHitResultOnViewVector(shooter,
                filter, (float) r));
    }

    public static HitResult getTarget(Player user, HitType type, double range, HitTracer tracer) {
        HitResult primary = tracer.trace(user, entity -> isPrimaryTarget(entity, user, type), range);
        if (primary instanceof EntityHitResult) {
            return primary;
        }
        return tracer.trace(user, entity -> isFallbackTarget(entity, user, type), range);
    }

    public static boolean isTarget(Entity entity, Player attacker, HitType type) {
        return resolvePriority(entity, attacker, type) != null;
    }

    public static boolean isPrimaryTarget(Entity entity, Player attacker, HitType type) {
        return resolvePriority(entity, attacker, type) == HitPriority.PRIMARY;
    }

    public static boolean isFallbackTarget(Entity entity, Player attacker, HitType type) {
        return resolvePriority(entity, attacker, type) == HitPriority.FALLBACK;
    }

    @Nullable
    public static HitPriority resolvePriority(Entity entity, Player attacker, HitType type) {
        if (entity == null || entity.isRemoved() || entity == attacker) {
            return null;
        }
        if (entity instanceof Player player) {
            return isAlivePlayerTarget(player, type) ? HitPriority.PRIMARY : null;
        }
        if (entity instanceof IsTargetObject obj && obj.isValidTarget(attacker, type)) {
            return obj.getTargetPriority(type);
        }
        for (TargetFilter filter : EXTRA_FILTERS) {
            HitPriority priority = filter.test(entity, attacker, type);
            if (priority != null) {
                return priority;
            }
        }
        return null;
    }

    /**
     * 服务端命中非玩家实体：调用 {@link IsTargetObject#onWeaponHit}，再广播
     * {@link OnEntityWeaponHit}。玩家击杀仍由各 Payload 自己处理。
     *
     * @return {@code true} 表示已有接口或事件处理了这次命中
     */
    public static boolean tryHit(Player attacker, Entity target, HitType type) {
        if (attacker == null || target == null || target.isRemoved() || target instanceof Player) {
            return false;
        }
        if (!isTarget(target, attacker, type)) {
            return OnEntityWeaponHit.EVENT.invoker().onHit(attacker, target, type);
        }
        if (attacker.distanceTo(target) > getMaxRange(target, type)) {
            return false;
        }
        boolean handled = false;
        if (target instanceof IsTargetObject obj) {
            handled = obj.onWeaponHit(attacker, type);
        }
        if (OnEntityWeaponHit.EVENT.invoker().onHit(attacker, target, type)) {
            handled = true;
        }
        return handled;
    }

    /**
     * 若命中该实体等价于命中某个玩家（傀儡本体），返回该玩家。
     */
    @Nullable
    public static ServerPlayer asProxyPlayer(Entity entity) {
        if (entity instanceof IsTargetObject obj && obj.getProxyPlayer() instanceof ServerPlayer sp) {
            return sp;
        }
        return null;
    }

    public static double getMaxRange(Entity entity, HitType type) {
        if (entity instanceof IsTargetObject obj) {
            double range = obj.getMaxHitRange(type);
            if (range > 0) {
                return range;
            }
        }
        return type.defaultRange;
    }

    private static boolean isAlivePlayerTarget(Player player, HitType type) {
        if (type == HitType.KNIFE) {
            return GameUtils.isPlayerAliveAndSurvival(player);
        }
        return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }
}

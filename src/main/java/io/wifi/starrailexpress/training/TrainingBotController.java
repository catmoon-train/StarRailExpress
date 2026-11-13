package io.wifi.starrailexpress.training;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.StatusAilmentHandler;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;

import java.util.List;

import static io.wifi.starrailexpress.training.TrainingBotNavigation.Point;

/** 只操作 Carpet 输入；攻击仍由训练共用的真实武器入口结算。 */
public final class TrainingBotController {
    private final int slot;
    private int patrolIndex;
    private int side;
    private List<Point> path = List.of();
    private int pathIndex;
    private Point pathGoal;
    private Point progressPosition;
    private long progressAt;
    private long replanAt;
    private long restUntil;
    private long visibleSince = -1;
    private long attackAt;
    private long aimAt;
    private Vec3 aimOffset = Vec3.ZERO;
    private int stuck;
    private boolean walking;

    TrainingBotController(int slot) {
        this.slot = slot;
        patrolIndex = slot;
        side = (slot & 1) == 0 ? 1 : -1;
    }

    public void stop(ServerPlayer bot) {
        actions(bot).stopAll();
        visibleSince = -1;
        walking = false;
        path = List.of();
        pathGoal = null;
        progressPosition = null;
    }

    public void tick(TrainingSession session, ServerPlayer bot, TrainingSettings.BotSettings config, long now) {
        ServerPlayer owner = session.owner();
        TrainingLayout.Area area = session.area();
        boolean combat = "combat".equals(config.behavior);
        if ("stationary".equals(config.behavior) || session.settings().paused || !session.authorized()
                || !TrainingRules.isAlive(bot) || bot.serverLevel() != session.level()
                || !area.box().contains(bot.position()) || locked(bot) || ModEffects.isInAnyDomain(bot)
                || owner == null || !TrainingRules.isAlive(owner) || owner.serverLevel() != session.level()
                || ModEffects.isInAnyDomain(owner) || !area.box().contains(owner.position())) {
            stop(bot);
            return;
        }
        // 便宜的安全闸每 tick 检查，寻路/射线/瞄准按槽位错峰。
        if (Math.floorMod(now + slot, 5) != 0) return;
        EntityPlayerActionPack pack = actions(bot);
        pack.stopAll();
        if (now < restUntil) return;
        Point from = point(bot.position());
        Point goal;
        boolean sight = combat && bot.hasLineOfSight(owner);
        boolean gun = "gun".equals(config.equipment);
        if (combat) {
            if (sight) {
                if (visibleSince < 0) visibleSince = now;
            } else visibleSince = -1;
            if (now >= aimAt) {
                double spread = Math.min(20, bot.distanceTo(owner)) * 0.012;
                aimOffset = new Vec3((bot.getRandom().nextDouble() - 0.5) * spread * 2,
                        (bot.getRandom().nextDouble() - 0.5) * spread, (bot.getRandom().nextDouble() - 0.5) * spread * 2);
                aimAt = now + 10;
            }
            pack.lookAt(owner.getEyePosition().add(aimOffset));
            double distance = bot.distanceToSqr(owner);
            int motion = TrainingBotNavigation.combatMotion(gun, distance, sight);
            if (motion > 0) goal = point(owner.position());
            else if (motion < 0) {
                Vec3 away = bot.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
                goal = point(bot.position().add(away.scale(2)));
            } else if (gun) {
                Vec3 offset = owner.position().subtract(bot.position()).multiply(1, 0, 1).normalize();
                goal = from.add(-offset.z * side * 1.3, 0, offset.x * side * 1.3);
            } else goal = from;
            // 不切回别的职业武器、不补枪；已经消耗/丢失的装备只在重置或重配时恢复。
            boolean equipped = bot.getMainHandItem().is(gun ? TMMItems.REVOLVER : TMMItems.KNIFE);
            if (equipped && now >= attackAt && visibleSince >= 0 && sight && distance <= (gun ? 400 : 9)) {
                var hit = SREHitManager.getTarget(bot, gun ? HitType.GUN : HitType.KNIFE, gun ? 20 : 3);
                boolean tracedOwner = hit instanceof EntityHitResult entityHit && entityHit.getEntity() == owner;
                if (TrainingBotNavigation.mayAttack(gun, distance, sight, tracedOwner, now - visibleSince, 8 + slot % 9)) {
                    if (gun) TrainingCombat.gun(bot);
                    else TrainingCombat.knife(bot);
                    attackAt = now + (gun ? 10 : 5);
                }
            }
            // 职业反噬可能杀死假人，命中也可能杀死主人，不能再发出移动输入。
            if (!TrainingRules.isAlive(bot) || !TrainingRules.isAlive(owner) || locked(bot)) {
                stop(bot);
                return;
            }
        } else {
            List<Vec3> patrol = area.patrolPoints();
            if (patrol.isEmpty()) { stop(bot); return; }
            patrolIndex = Math.floorMod(patrolIndex, patrol.size());
            if (from.distanceSquared(point(patrol.get(patrolIndex))) < 1) patrolIndex = (patrolIndex + 1) % patrol.size();
            goal = point(patrol.get(patrolIndex));
        }
        if (progressPosition == null) { progressPosition = from; progressAt = now; }
        if (now - progressAt >= 40) {
            if (walking && from.distanceSquared(progressPosition) < 0.16) {
                stuck++;
                side = -side;
                path = List.of();
                replanAt = now;
                if (stuck >= 3) { restUntil = now + 40; stuck = 0; }
            } else stuck = 0;
            progressPosition = from;
            progressAt = now;
        }
        if (now < restUntil) return;
        if (from.distanceSquared(goal) < 0.16) { walking = false; return; }
        if (now >= replanAt || pathGoal == null || pathGoal.distanceSquared(goal) > 9) {
            List<Point> nodes = area.nodePoints().stream().map(TrainingBotController::point).toList();
            path = TrainingBotNavigation.route(from, goal, nodes, new CollisionPassage(session.level(), bot, area, 384));
            pathIndex = 0;
            pathGoal = goal;
            replanAt = now + 30;
        }
        while (pathIndex < path.size() && from.distanceSquared(path.get(pathIndex)) < 0.7 * 0.7) pathIndex++;
        Point waypoint = pathIndex < path.size() ? path.get(pathIndex) : goal;
        // 即使图暂时无路，也只尝试身旁一步的真实通路，不直接冲向墙后终点。
        Point step = TrainingBotNavigation.localStep(from, waypoint,
                new CollisionPassage(session.level(), bot, area, 48), side);
        walking = from.distanceSquared(goal) > 0.16;
        if (!combat && from.distanceSquared(step) > 0.01) pack.lookAt(vec(step).add(0, bot.getEyeHeight(), 0));
        float[] input = TrainingBotNavigation.inputs(from, step, bot.getYRot(), combat ? 0.8F : 0.65F);
        pack.setForward(input[0]).setStrafing(input[1]);
    }

    private static boolean locked(ServerPlayer bot) {
        return bot.hasEffect(ModEffects.MOVE_BANED) || bot.hasEffect(ModEffects.TURN_BANED)
                || bot.hasEffect(ModEffects.USED_BANED) || bot.hasEffect(ModEffects.GHOST_CURSE)
                || bot.hasEffect(ModEffects.TIME_REWIND_DAZE) || StatusAilmentHandler.isIntellectLocked(bot)
                || (bot.hasEffect(ModEffects.TIME_STOP) && !TimeStopEffect.canMovePlayers.contains(bot.getUUID()));
    }

    static EntityPlayerActionPack actions(ServerPlayer bot) {
        return ((ServerPlayerInterface) bot).getActionPack();
    }

    private static Point point(Vec3 p) { return new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }

    /** 每次决策独立限额；只读已加载区块，按玩家宽高检测方块和落脚点。 */
    private static final class CollisionPassage implements TrainingBotNavigation.Passage {
        private final ServerLevel level;
        private final ServerPlayer bot;
        private final AABB bounds;
        private int budget;

        CollisionPassage(ServerLevel level, ServerPlayer bot, TrainingLayout.Area area, int budget) {
            this.level = level;
            this.bot = bot;
            this.bounds = area.box();
            this.budget = budget;
        }

        @Override
        public boolean clear(Point from, Point to) {
            double distance = Math.sqrt(from.distanceSquared(to));
            if (distance > TrainingBotNavigation.MAX_LINK_LENGTH || Math.abs(to.y() - from.y()) > 2) return false;
            int samples = Math.max(1, (int) Math.ceil(distance / 0.6));
            if (samples > budget) return false;
            for (int i = 1; i <= samples; i++) {
                if (--budget < 0) return false;
                double t = (double) i / samples;
                Vec3 at = new Vec3(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t,
                        from.z() + (to.z() - from.z()) * t);
                if (!bounds.contains(at.add(-0.31, 0, -0.31)) || !bounds.contains(at.add(0.31, 1.8, 0.31))) return false;
                BlockPos feet = BlockPos.containing(at);
                if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.offset(1, 0, 1))
                        || !level.hasChunkAt(feet.offset(-1, 0, -1))) return false;
                if (!level.getFluidState(feet).isEmpty()) return false;
                var floor = level.getBlockState(BlockPos.containing(at.add(0, -0.1, 0)));
                if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                        || floor.is(Blocks.CACTUS) || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)) return false;
                AABB body = new AABB(at.x - 0.3, at.y + 0.05, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
                // 允许原版 0.6 格自动跨阶；不会把整格墙当作可跳过的地形。
                if (!level.noCollision(bot, body) && !level.noCollision(bot, body.move(0, 0.6, 0))) return false;
                AABB support = new AABB(at.x - 0.25, at.y - 0.65, at.z - 0.25, at.x + 0.25, at.y + 0.01, at.z + 0.25);
                if (level.noCollision(bot, support)) return false;
            }
            return true;
        }
    }
}

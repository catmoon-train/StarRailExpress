package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 4f;

    /**
     * 空间视线模拟的最小可见性阈值
     * — 实体包围盒上至少 15% 的采样点暴露在爆炸中才判定受影响
     * — 替代原版 Explosion.getSeenPercent() == 0.0 的二值判断
     */
    public static final double MIN_VISIBILITY_THRESHOLD = 0.15;

    public GrenadeEntity(EntityType<?> ignored, Level world) {
        super(TMMEntities.GRENADE, world);
    }

    @Override
    protected Item getDefaultItem() {
        return TMMItems.GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            // Consider sending this in one payload to reduce packets sent - SkyNotTheLimit
            world.playSound(null, this.blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 5f,
                    1f + this.getRandom().nextFloat() * .1f - .05f);
            world.sendParticles(TMMParticles.BIG_EXPLOSION, this.getX(), this.getY() + .1f, this.getZ(), 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, .2f);
            world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getDefaultItem().getDefaultInstance()),
                    this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, 1f);

            Vec3 explosionPos = this.position();

            // 空间多层采样：在5个深度层进行爆炸检测，模拟3D空间爆炸波传播
            // 层间距 0.5 格，覆盖 ±1.0 格垂直范围（配合 4 格半径形成球形覆盖）
            var hitted_players = new HashSet<Entity>();
            double[] yOffsets = {0.0, 0.5, -0.5, 1.0, -1.0};
            for (double yOff : yOffsets) {
                hitted_players.addAll(getPlayersAffectedByExplosion(world,
                        explosionPos.x, explosionPos.y + yOff, explosionPos.z, EXPLOSION_RADIUS));
            }

            // 肉汁独处保护：先扫描爆炸范围内是否有肉汁被好人保护
            boolean meatballInRange = false;
            boolean hasInnocentInRange = false;
            for (var entity : hitted_players) {
                
                if (entity instanceof Player player) {
                    var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld.isRole(player, org.agmas.noellesroles.role.ModRoles.MEATBALL)) {
                        meatballInRange = true;
                    } else if (gameWorld.isInnocent(player)) {
                        hasInnocentInRange = true;
                    }
                }
            }

            // 手雷最大收益限制：记录投掷者击杀前余额，击杀后追回超额部分
            Player attacker = this.getOwner() instanceof Player playerEntity ? playerEntity : null;
            SREPlayerShopComponent killerShop = attacker != null
                    ? SREPlayerShopComponent.KEY.get(attacker) : null;
            int balanceBefore = killerShop != null ? killerShop.balance : 0;
            int MAX_KILL_PLAYER_COUNT = SREConfig.instance().grenadeMaxHurtPlayers;
            int count = 0;
            for (var entity : hitted_players) {
                if (entity instanceof Player player) {
                    // 肉汁独处保护：范围内同时有肉汁和好人时，跳过肉汁的击杀
                    if (meatballInRange && hasInnocentInRange) {
                        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                        if (gameWorld.isRole(player, org.agmas.noellesroles.role.ModRoles.MEATBALL)) {
                            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.displayClientMessage(
                                        net.minecraft.network.chat.Component
                                                .translatable("message.noellesroles.meatball.protected")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN),
                                        true);
                            }
                            continue;
                        }
                    }
                    GameUtils.killPlayer(player, true, attacker,
                            GameConstants.DeathReasons.GRENADE);
                }
                if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                    puppeteerBodyEntity.playerHurt(attacker,
                            GameConstants.DeathReasons.GRENADE);
                }
                count++;
                if (count >= MAX_KILL_PLAYER_COUNT)
                    break;
            }

            // 手雷收益结算：按 grenadeMoneyPerKill 单价 × 击杀数，上限 grenadeMaxMoneyReward
            if (killerShop != null) {
                int moneyEarned = killerShop.balance - balanceBefore;
                int grenadePerKill = SREConfig.instance().grenadeMoneyPerKill;
                int maxReward = SREConfig.instance().grenadeMaxMoneyReward;
                // 计算应得收益：击杀数 × 手雷单价，不超过上限
                int targetReward = count * grenadePerKill;
                if (maxReward > 0 && targetReward > maxReward) {
                    targetReward = maxReward;
                }
                int adjustment = targetReward - moneyEarned;
                if (adjustment != 0) {
                    killerShop.addToBalance(adjustment);
                }
            }
            this.discard();
        }
    }

    // ────────────────────────────────────────────────
    //  空间视线模拟 — 核心爆炸判定
    // ────────────────────────────────────────────────

    /**
     * 基于空间视线模拟的爆炸影响判定。
     * <p>
     * 使用多射线空间采样替代原版 {@code Explosion.getSeenPercent()}：
     * <ul>
     *   <li>对实体包围盒的 26+ 个采样点投射射线</li>
     *   <li>计算可见性比例（可见采样点 / 总采样点）</li>
     *   <li>只有可见性达到 {@link #MIN_VISIBILITY_THRESHOLD} 的实体才受爆炸影响</li>
     * </ul>
     * <p>
     * 这解决了原版的畸形判断：
     * <ul>
     *   <li>玩家在掩体后只露出一部分时不再完全免疫</li>
     *   <li>随机采样不稳定的问题被确定性多点采样替代</li>
     *   <li>空间深度感知更准确反映实体与爆炸源的遮挡关系</li>
     * </ul>
     *
     * @param level  世界
     * @param x      爆炸中心 X
     * @param y      爆炸中心 Y
     * @param z      爆炸中心 Z
     * @param radius 爆炸半径
     * @return 受影响的实体列表（按距离排序）
     */
    public static ArrayList<Entity> getPlayersAffectedByExplosion(Level level, double x, double y, double z,
            float radius) {
        float diameter = radius;
        int minX = Mth.floor(x - diameter - 1.0F);
        int maxX = Mth.floor(x + diameter + 1.0F);
        int minY = Mth.floor(y - diameter - 1.0F);
        int maxY = Mth.floor(y + diameter + 1.0F);
        int minZ = Mth.floor(z - diameter - 1.0F);
        int maxZ = Mth.floor(z + diameter + 1.0F);

        List<Entity> candidates = level.getEntities(
                null,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        Vec3 center = new Vec3(x, y, z);
        ArrayList<Entity> affected = new ArrayList<>();

        for (Entity entity : candidates) {
            if (entity instanceof Player player) {
                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;

                double distance = Math.sqrt(entity.distanceToSqr(center));
                double v = distance / diameter;
                if (v > 1.0)
                    continue;

                // —— 空间视线模拟 + 深度计算 ——
                double visibility = computeSpatialVisibility(level, center, entity);
                if (visibility < MIN_VISIBILITY_THRESHOLD)
                    continue;

                affected.add(player);
            }
            if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                var owner = puppeteerBodyEntity.getOwner();
                if (owner instanceof Player player) {
                    if (!GameUtils.isPlayerAliveAndSurvival(player))
                        continue;
                    double distance = Math.sqrt(puppeteerBodyEntity.distanceToSqr(center));
                    double v = distance / diameter;
                    if (v > 1.0)
                        continue;

                    double visibility = computeSpatialVisibility(level, center, puppeteerBodyEntity);
                    if (visibility < MIN_VISIBILITY_THRESHOLD)
                        continue;

                    affected.add(puppeteerBodyEntity);
                }
            }
        }

        // 按距离排序：近的在前，确保有限击杀名额优先给近距离目标
        affected.sort((a, b) -> {
            double da = a.distanceToSqr(x, y, z);
            double db = b.distanceToSqr(x, y, z);
            return Double.compare(da, db);
        });
        return affected;
    }

    // ────────────────────────────────────────────────
    //  空间可见性计算引擎
    // ────────────────────────────────────────────────

    /**
     * 空间视线可见性计算 — 核心算法。
     * <p>
     * 从爆炸中心向目标实体的包围盒投射多条采样射线，
     * 通过统计未遮挡射线的比例来计算空间可见性。
     * <p>
     * 采样点覆盖（共 26+ 个）：
     * <ul>
     *   <li>包围盒 8 个顶点</li>
     *   <li>体积中心</li>
     *   <li>6 个面的中心</li>
     *   <li>12 条边的中点</li>
     *   <li>眼睛位置（如果是生物实体）</li>
     * </ul>
     * 这种均衡的空间分布确保无论实体从哪个方向被遮挡，都能得到准确的曝光评估。
     *
     * @param level  世界
     * @param center 爆炸中心
     * @param entity 目标实体
     * @return 可见性比例 [0.0, 1.0]，0.0 = 完全遮挡，1.0 = 完全可见
     */
    public static double computeSpatialVisibility(Level level, Vec3 center, Entity entity) {
        List<Vec3> samplePoints = generateSpatialSamplePoints(entity);

        int visible = 0;
        int total = samplePoints.size();

        for (Vec3 point : samplePoints) {
            if (isLineOfSightClear(level, center, point, entity)) {
                visible++;
            }
        }

        return (double) visible / (double) total;
    }

    /**
     * 生成实体包围盒的空间采样点。
     * 均匀覆盖实体的整个 3D 体积，确保全方位的遮挡检测。
     */
    private static List<Vec3> generateSpatialSamplePoints(Entity entity) {
        AABB bb = entity.getBoundingBox();
        List<Vec3> points = new ArrayList<>(32);

        double minX = bb.minX, minY = bb.minY, minZ = bb.minZ;
        double maxX = bb.maxX, maxY = bb.maxY, maxZ = bb.maxZ;
        double midX = (minX + maxX) * 0.5;
        double midY = (minY + maxY) * 0.5;
        double midZ = (minZ + maxZ) * 0.5;

        // — 8 个顶点 —
        points.add(new Vec3(minX, minY, minZ));
        points.add(new Vec3(minX, minY, maxZ));
        points.add(new Vec3(minX, maxY, minZ));
        points.add(new Vec3(minX, maxY, maxZ));
        points.add(new Vec3(maxX, minY, minZ));
        points.add(new Vec3(maxX, minY, maxZ));
        points.add(new Vec3(maxX, maxY, minZ));
        points.add(new Vec3(maxX, maxY, maxZ));

        // — 体积中心 —
        points.add(new Vec3(midX, midY, midZ));

        // — 6 个面的中心 —
        points.add(new Vec3(midX, minY, midZ)); // 底面
        points.add(new Vec3(midX, maxY, midZ)); // 顶面
        points.add(new Vec3(minX, midY, midZ)); // -X 面
        points.add(new Vec3(maxX, midY, midZ)); // +X 面
        points.add(new Vec3(midX, midY, minZ)); // -Z 面
        points.add(new Vec3(midX, midY, maxZ)); // +Z 面

        // — 眼睛位置（如果是生物实体，提供最关键的玩家视角参考点）—
        if (entity instanceof LivingEntity living) {
            points.add(living.getEyePosition());
        }

        // — 12 条边的中点 —
        // 垂直边 (4条)
        points.add(new Vec3(minX, midY, minZ));
        points.add(new Vec3(minX, midY, maxZ));
        points.add(new Vec3(maxX, midY, minZ));
        points.add(new Vec3(maxX, midY, maxZ));
        // X 方向边 (4条)
        points.add(new Vec3(minX, minY, midZ));
        points.add(new Vec3(minX, maxY, midZ));
        points.add(new Vec3(maxX, minY, midZ));
        points.add(new Vec3(maxX, maxY, midZ));
        // Z 方向边 (4条)
        points.add(new Vec3(midX, minY, minZ));
        points.add(new Vec3(midX, minY, maxZ));
        points.add(new Vec3(midX, maxY, minZ));
        points.add(new Vec3(midX, maxY, maxZ));

        return points;
    }

    /**
     * 单条射线的视线检测。
     * 使用 {@link ClipContext} 进行方块碰撞检测，判断从起点到终点是否有遮挡。
     *
     * @param level   世界
     * @param from    射线起点（爆炸中心/采样层）
     * @param to      射线终点（实体采样点）
     * @param context 上下文实体（用于碰撞过滤）
     * @return true = 无遮挡，视线畅通
     */
    private static boolean isLineOfSightClear(Level level, Vec3 from, Vec3 to, Entity context) {
        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                context);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == HitResult.Type.MISS) {
            return true; // 无遮挡，完全可见
        }

        // 检查击中点是否在目标附近（容差范围内），防止浮点精度导致误判
        double hitDistSq = hit.getLocation().distanceToSqr(from);
        double targetDistSq = to.distanceToSqr(from);
        return hitDistSq + 0.01 >= targetDistSq;
    }

    // ────────────────────────────────────────────────
    //  公共工具方法 — 供 C4Detonation 等其他爆炸系统复用
    // ────────────────────────────────────────────────

    /**
     * 简化的爆炸视线检测（单射线两位置检测）。
     * 供 C4 等需要快速二值判断（可见/不可见）的外部系统使用。
     * <p>
     * 检测从爆炸中心到目标的眼睛和身体中心两个位置的视线，
     * 任意一个位置畅通即判定可见。
     *
     * @param level       世界
     * @param blastCenter 爆炸中心
     * @param entity      目标实体
     * @return true = 视线畅通
     */
    public static boolean hasExplosionLineOfSight(Level level, Vec3 blastCenter, Entity entity) {
        Vec3 center = blastCenter.add(0.0D, 0.35D, 0.0D); // 略微抬高爆炸中心
        Vec3 eye = entity instanceof LivingEntity le
                ? le.getEyePosition()
                : entity.position().add(0.0D, entity.getBbHeight() * 0.85D, 0.0D);
        Vec3 body = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        return isLineOfSightClear(level, center, eye, entity)
                || isLineOfSightClear(level, center, body, entity);
    }
}

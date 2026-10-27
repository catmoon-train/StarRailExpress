package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.agmas.noellesroles.content.entity.CommandBlockCrackEntity;
import org.agmas.noellesroles.init.ModEntities;

import io.wifi.starrailexpress.api.hit.HitPriority;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.MaceItem;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 任务点旁边的裂缝。每 75 到 112.5 秒只刷一道，并挑一块能站的空位。
 * 击碎限定好人阵营，支持枪械与近战武器。
 */
public final class CommandBlockCracks {
    public static final String TAG = "sre.command_block_crack";
    public static final int MIN_DELAY = 75 * 20;
    public static final int MAX_DELAY = 2250; // 112.5 秒，原间隔增加 25%
    /** 刚刷出来时全场高亮的时长。 */
    public static final int HIGHLIGHT_TICKS = 5 * 20;

    private CommandBlockCracks() {
    }

    public static int rollDelay(ServerLevel level) {
        return MIN_DELAY + level.random.nextInt(MAX_DELAY - MIN_DELAY + 1);
    }

    public static void registerAim() {
        SREHitManager.registerTargetFilter((entity, attacker, type) -> {
            if (type == HitType.GUN && isCrack(entity)) {
                return HitPriority.FALLBACK;
            }
            return null;
        });
        AttackEntityCallback.EVENT.register((attacker, world, hand, target, hit) -> {
            if (!isCrack(target)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = attacker.getItemInHand(hand);
            // 枪械仍由射击包结算，不能用左键敲击替代开枪。
            if (!isMeleeWeapon(stack) || attacker.getCooldowns().isOnCooldown(stack.getItem())) {
                return InteractionResult.FAIL;
            }
            if (attacker instanceof ServerPlayer player && player.distanceTo(target) <= 4.0D
                    && player.getAttackStrengthScale(0.5F) >= 0.9F) {
                if (tryShatterWithWeapon(player, target)) {
                    player.resetAttackStrengthTicker();
                    if (stack.isDamageableItem() && !player.isCreative()) {
                        stack.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND
                                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                                : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static boolean isCrack(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof CommandBlockCrackEntity || entity.getTags().contains(TAG)) {
            return true;
        }
        Component name = entity.getCustomName();
        return name != null && TAG.equals(name.getString());
    }

    public static boolean isVisibleCrack(Entity entity) {
        return entity instanceof CommandBlockCrackEntity && isCrack(entity);
    }

    /** 刷新前清除已有裂缝，再只刷一道。没有合适空位就返回 false，由调用方过一会儿再试。 */
    public static boolean spawnOne(ServerLevel level) {
        discard(level);
        Vec3 spot = chooseSpot(level);
        if (spot == null) {
            return false;
        }
        CommandBlockCrackEntity crack = new CommandBlockCrackEntity(ModEntities.COMMAND_BLOCK_CRACK, level);
        crack.setPos(spot.x, spot.y, spot.z);
        crack.setYRot(level.random.nextFloat() * 360.0F);
        crack.addTag(TAG);
        crack.setCustomName(Component.literal(TAG));
        crack.setCustomNameVisible(false);
        level.addFreshEntity(crack);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.45F, 0.55F);
        announceSpawn(level, crack);
        return true;
    }

    /** 出现时高亮五秒并全局通报，让所有人都知道裂缝在哪。 */
    private static void announceSpawn(ServerLevel level, CommandBlockCrackEntity crack) {
        crack.setHighlighted(true);
        crack.setGlowingTag(true);
        Component text = Component.translatable("message.noellesroles.command_block.crack_spawn")
                .withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendBroadcast(player, text);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, 0.5F, 0.75F);
        }
        Scheduler.schedule(() -> {
            if (crack.isAlive()) {
                crack.setHighlighted(false);
                crack.setGlowingTag(false);
            }
        }, HIGHLIGHT_TICKS);
    }
    /** 普通近战武器；物品和攻击接口共同识别，避免空手或任务道具击碎裂缝。 */
    public static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(TMMItemTags.GUNS)
                && (stack.getItem() instanceof SREItemProperties.TrainWeapon
                        || stack.getItem() instanceof SREItemProperties.LeftClickHurtable
                        || stack.getItem() instanceof TieredItem
                        || stack.getItem() instanceof MaceItem
                        || stack.is(TMMItems.BAT) || stack.is(TMMItems.NUNCHUCK)
                        || stack.is(org.agmas.noellesroles.init.ModItems.BATON));
    }

    /** 手枪入口保留物品检查；其它武器通过各自命中入口调用通用结算。 */
    public static boolean tryShatter(ServerPlayer shooter, Entity target) {
        return shooter != null && shooter.getMainHandItem().is(TMMItemTags.GUNS)
                && tryShatterWithWeapon(shooter, target);
    }

    /** 好人阵营的武器可击碎裂缝；移除成功后只结算一次破坏进度。 */
    public static boolean tryShatterWithWeapon(ServerPlayer shooter, Entity target) {
        if (shooter == null || !isCrack(target) || target.level() != shooter.level()
                || !GameUtils.isPlayerAliveAndSurvival(shooter)
                || Error404Role.isHighDimension(shooter)
                || shooter.hasEffect(org.agmas.noellesroles.init.ModEffects.USED_BANED)
                || org.agmas.noellesroles.content.effects.StatusAilmentHandler.isIntellectLocked(shooter)) {
            return false;
        }
        var role = SREGameWorldComponent.KEY.get(shooter.level()).getRole(shooter);
        if (role == null || !role.isInnocent() || role.canUseKiller() || role.isNeutrals()) {
            return false;
        }
        Vec3 at = target.position();
        if (shooter.level() instanceof ServerLevel level) {
            discardNear(level, at);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.8D, at.z, 16, 0.3D, 0.5D, 0.3D, 0.05D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 0.7F);
            CommandBlockDirector.addDestruction(level);
        }
        shooter.displayClientMessage(Component.translatable("message.noellesroles.command_block.crack_hit"), true);
        return true;
    }

    public static Entity alongLook(ServerPlayer player, double range) {
        if (player == null) {
            return null;
        }
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, CommandBlockCracks::isCrack, (float) range);
        if (hit instanceof EntityHitResult entityHit && isCrack(entityHit.getEntity())) {
            return entityHit.getEntity();
        }
        return null;
    }

    /** 裂缝比当前准星目标更近时，让手枪打到裂缝，而不是穿过它打到后面的人。 */
    public static HitResult prefer(Player user, HitResult current, float range) {
        if (user == null) {
            return current;
        }
        HitResult crack = ProjectileUtil.getHitResultOnViewVector(user, CommandBlockCracks::isCrack, range);
        if (!(crack instanceof EntityHitResult crackHit) || !isCrack(crackHit.getEntity())) {
            return current;
        }
        if (!(current instanceof EntityHitResult currentHit)) {
            return crack;
        }
        return crackHit.getEntity().distanceToSqr(user) < currentHit.getEntity().distanceToSqr(user) ? crack : current;
    }

    public static void discard(ServerLevel level) {
        if (level == null) {
            return;
        }
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isCrack(entity)) {
                found.add(entity);
            }
        }
        for (Entity entity : found) {
            entity.discard();
        }
    }

    private static void discardNear(ServerLevel level, Vec3 at) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(1.2D), CommandBlockCracks::isCrack)) {
            found.add(entity);
        }
        for (Entity entity : found) {
            entity.discard();
        }
    }

    private static Vec3 chooseSpot(ServerLevel level) {
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty()) {
            return null;
        }
        List<Vec3> occupied = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isVisibleCrack(entity)) {
                occupied.add(entity.position());
            }
        }
        // 「坐下」任务的座椅周围不刷裂缝，别挡住玩家做坐下任务
        int chairType = SREPlayerTaskComponent.Task.CHAIR.ordinal();
        List<BlockPos> points = new ArrayList<>();
        for (BlockPos point : GameUtils.taskBlocks.keySet()) {
            Integer type = GameUtils.taskBlocks.get(point);
            if (type != null && type == chairType) {
                continue;
            }
            points.add(point);
        }
        Collections.shuffle(points, new java.util.Random(level.random.nextLong()));
        List<Vec3> found = new ArrayList<>();
        for (BlockPos point : points) {
            collectSpots(level, point, occupied, found);
            if (found.size() >= 6) {
                break;
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.get(level.random.nextInt(found.size()));
    }

    private static void collectSpots(ServerLevel level, BlockPos point, List<Vec3> occupied, List<Vec3> found) {
        int[] radii = { 2, 3 };
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        List<int[]> offsets = new ArrayList<>();
        for (int radius : radii) {
            for (int[] dir : dirs) {
                offsets.add(new int[] { dir[0] * radius, dir[1] * radius });
            }
        }
        Collections.shuffle(offsets, new java.util.Random(point.asLong() ^ level.getGameTime()));
        for (int[] offset : offsets) {
            Vec3 spot = standAt(level, point.offset(offset[0], 0, offset[1]));
            if (spot == null || tooClose(spot, occupied)) {
                continue;
            }
            found.add(spot);
            if (found.size() >= 6) {
                return;
            }
        }
    }

    private static boolean tooClose(Vec3 spot, List<Vec3> others) {
        for (Vec3 other : others) {
            if (spot.distanceToSqr(other) < 16.0D) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 standAt(ServerLevel level, BlockPos origin) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = origin.offset(0, dy, 0);
            BlockState ground = level.getBlockState(feet.below());
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && !ground.isAir()) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }
}
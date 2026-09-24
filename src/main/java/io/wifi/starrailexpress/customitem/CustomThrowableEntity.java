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

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「自定义列车物品·投掷物」的实体（全模组唯一一个注册实体，行为全部按物品配置决定）。
 *
 * <p>
 * 与手榴弹 / 烟雾弹 / 燃烧弹 / 粘性炸弹 / C4 / 滞时雷的关系：
 * <ul>
 * <li>是否需要拉栓：由 {@code CustomItemRuntime.throwCustom} 在投出前决定（按住右键蓄力 / 直接投出）</li>
 * <li>粘附玩家：{@link CustomItemData#throwSticky}，命中玩家后跟随并开始 {@link CustomItemData#throwStickTicks} 倒计时</li>
 * <li>延迟生效：{@link CustomItemData#throwDelayed}，落地弹起后按 {@link CustomItemData#throwDelaySeconds} 生效</li>
 * <li>钳子拆除：{@link CustomItemData#throwDefusable}，见 {@code CustomItemRuntime.beginDefuse}</li>
 * <li>触发内容：爆炸 / 范围指令与药水 / 粒子区域 / 燃烧弹式持续区域，见 {@link #detonate}</li>
 * </ul>
 */
public class CustomThrowableEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 落地停稳时贴住方块表面的偏移量（同粘性雷）。 */
    private static final double SURFACE_OFFSET = 0.05D;

    /** 已经进入「引爆倒计时」，避免贴地反复触发落地逻辑。 */
    private boolean armed;
    /** 已经引爆过，避免重复结算。 */
    private boolean detonated;
    /** 剩余引爆 tick（-1 = 未开始）。 */
    private int fuseTicks = -1;
    /** 粘附到的玩家。 */
    private UUID stuckTo;
    /** 拆除剩余 tick（-1 = 无人拆除）。 */
    private int defuseTicks = -1;
    /** 拆除失败概率（百分比）。 */
    private int defuseFailPercent;
    /** 正在拆除的玩家。 */
    private UUID defuserId;

    // 同步给客户端的字段：拆除进度条 HUD 直接读它，不需要额外网络包
    private static final EntityDataAccessor<Integer> DATA_FUSE_TICKS = SynchedEntityData
            .defineId(CustomThrowableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEFUSE_TICKS = SynchedEntityData
            .defineId(CustomThrowableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEFUSE_TOTAL = SynchedEntityData
            .defineId(CustomThrowableEntity.class, EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUSE_TICKS, -1);
        builder.define(DATA_DEFUSE_TICKS, -1);
        builder.define(DATA_DEFUSE_TOTAL, 0);
    }

    /** 剩余引爆 tick（客户端可用；-1 = 未开始）。 */
    public int syncedFuseTicks() {
        return entityData.get(DATA_FUSE_TICKS);
    }

    /** 拆除剩余 tick（客户端可用；<= 0 = 没在拆）。 */
    public int syncedDefuseTicks() {
        return entityData.get(DATA_DEFUSE_TICKS);
    }

    /** 拆除总时长（客户端可用，用于算进度）。 */
    public int syncedDefuseTotal() {
        return entityData.get(DATA_DEFUSE_TOTAL);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 倒计时 / 粘附 / 拆除状态全部落盘，区块卸载或服务器重启后继续读秒
        tag.putBoolean("CustomArmed", armed);
        tag.putInt("CustomFuse", fuseTicks);
        tag.putInt("CustomDefuse", defuseTicks);
        tag.putInt("CustomDefuseFail", defuseFailPercent);
        if (stuckTo != null) {
            tag.putUUID("CustomStuckTo", stuckTo);
        }
        if (defuserId != null) {
            tag.putUUID("CustomDefuserId", defuserId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        armed = tag.getBoolean("CustomArmed");
        fuseTicks = tag.getInt("CustomFuse");
        defuseTicks = tag.getInt("CustomDefuse");
        defuseFailPercent = tag.getInt("CustomDefuseFail");
        if (tag.hasUUID("CustomStuckTo")) {
            stuckTo = tag.getUUID("CustomStuckTo");
        }
        if (tag.hasUUID("CustomDefuserId")) {
            defuserId = tag.getUUID("CustomDefuserId");
        }
        entityData.set(DATA_FUSE_TICKS, fuseTicks);
        entityData.set(DATA_DEFUSE_TICKS, defuseTicks);
        entityData.set(DATA_DEFUSE_TOTAL, Math.max(0, defuseTicks));
    }

    public CustomThrowableEntity(EntityType<?> entityType, Level level) {
        // 与粘性手雷同一写法：super 需要「本实体的注册类型」，直接取注册表常量，
        // 避免 EntityType.Builder.of 的泛型推断问题
        super(TMMEntities.CUSTOM_THROWABLE, level);
    }

    public CustomThrowableEntity(EntityType<?> entityType, LivingEntity owner, Level level) {
        super(TMMEntities.CUSTOM_THROWABLE, owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return CustomItemLoader.customItem();
    }

    /** 这份投掷物对应的配置（未同步 / 已删除返回 null）。 */
    public CustomItemData config() {
        return CustomItemLoader.getData(getItem());
    }

    // ==================== 每 tick ====================

    @Override
    public void tick() {
        CustomItemData data = config();
        if (data == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        super.tick();

        // 粘附：贴着目标玩家的位置
        if (stuckTo != null) {
            Player target = level().getPlayerByUUID(stuckTo);
            if (target == null || !target.isAlive()) {
                // 目标没了（死亡 / 离线 / 区块卸载后重载）：不再干等，落地即引爆
                // （与粘性手雷一样能自愈，不会变成永远不炸的哑弹）
                stuckTo = null;
                setNoGravity(false);
                if (fuseTicks <= 0) {
                    startFuse(1);
                }
            } else {
                setPos(target.getX(), target.getY() + 1.0D, target.getZ());
                setDeltaMovement(Vec3.ZERO);
            }
        }

        if (level().isClientSide() || !isAlive()) {
            return;
        }
        // 拆除倒计时优先于引爆
        if (defuseTicks > 0 && --defuseTicks == 0) {
            finishDefuse(data);
            return;
        }
        if (defuseTicks > 0) {
            entityData.set(DATA_DEFUSE_TICKS, defuseTicks);
        }
        if (fuseTicks > 0 && --fuseTicks == 0) {
            detonate(data);
            return;
        }
        if (fuseTicks > 0) {
            entityData.set(DATA_FUSE_TICKS, fuseTicks);
        }
    }

    // ==================== 命中 ====================

    @Override
    protected void onHit(HitResult hitResult) {
        CustomItemData data = config();
        if (data == null || detonated) {
            return;
        }
        if (armed) {
            // 已经进入倒计时（延迟生效落地 / 粘附）：贴住方块表面停稳。
            // 这里必须处理碰撞，否则忽略碰撞后实体仍会被物理 tick 推着继续下落，
            // 直接穿过方块沉到地下（爆炸点也跟着跑到地下）。
            // 客户端同样处理：投掷物在客户端也会自行模拟物理，不处理会看到实体穿地。
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                settle(hitResult);
            }
            return;
        }
        // 延迟生效（滞时雷）：落地弹起后开始倒计时。
        // 弹起是纯物理表现，两端都做才能保证客户端与实体位置一致；
        // 倒计时长度以服务端的 fuseTicks 为权威。
        if (data.throwDelayed && hitResult.getType() == HitResult.Type.BLOCK) {
            armed = true;
            bounce();
            if (!level().isClientSide()) {
                startFuse((int) Math.round(data.throwDelaySeconds * 20.0D));
            }
            return;
        }
        // 其余命中判定（粘附玩家 / 立即生效）只在服务端做
        if (level().isClientSide()) {
            return;
        }
        // 粘附玩家
        if (data.throwSticky && hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player target && target.isAlive()) {
            stickTo(target, data);
            return;
        }
        detonate(data);
    }

    /** 粘附到玩家身上，开始「粘附时间」倒计时。 */
    private void stickTo(Player target, CustomItemData data) {
        armed = true;
        stuckTo = target.getUUID();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        startFuse(Math.max(1, data.throwStickTicks));
    }

    /** 滞时雷式的落地弹起。 */
    private void bounce() {
        Vec3 delta = getDeltaMovement();
        double y = Math.abs(delta.y) * 0.6D;
        if (y < 0.05D) {
            y = 0.35D;
        }
        setDeltaMovement(delta.x * 0.6D, y, delta.z * 0.6D);
    }

    /**
     * 已进入倒计时后落地：把实体贴在方块表面停住（清零速度 + 关重力）。
     *
     * <p>
     * 投掷物基类的位移是纯手动的，碰撞只靠 {@code onHit} 兜住；一旦进入倒计时就不再
     * 结算碰撞，实体便会一直往下掉落穿地。这里贴住命中面即可让它稳稳停在落点读秒，
     * 之后爆炸也发生在正确的落点而不是地下。
     */
    private void settle(HitResult hitResult) {
        Vec3 target = position();
        if (hitResult instanceof BlockHitResult blockHit) {
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            target = blockHit.getLocation().add(normal.scale(SURFACE_OFFSET));
        }
        setPos(target.x, target.y, target.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        hasImpulse = true;
    }

    private void startFuse(int ticks) {
        fuseTicks = Math.max(1, ticks);
        entityData.set(DATA_FUSE_TICKS, fuseTicks);
    }

    // ==================== 生效 ====================

    /** 生效：范围指令 + 药水效果 + 爆炸 + 粒子区域 + 持续生效区域。 */
    private void detonate(CustomItemData data) {
        if (detonated) {
            return;
        }
        detonated = true;
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        Vec3 center = position();
        double radius = Math.max(0.5D, data.throwRadius);
        List<ServerPlayer> targets = affectedPlayers(serverLevel, center, radius, data);

        // 投掷者：范围指令里的 <attacker> 指他
        Entity thrower = getOwner();
        ServerPlayer attacker = thrower instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        for (ServerPlayer target : targets) {
            CustomItemLoader.executeCommands(data.throwHitCommands, target, attacker);
            CustomItemRuntime.applyEffects(target, data.throwHitEffects);
        }
        if (data.throwExplode) {
            explode(serverLevel, center, targets, data);
        }
        if (data.throwParticleArea) {
            CustomThrowableAreas.createParticleArea(serverLevel, center, radius,
                    data.throwParticleAreaId, data.throwParticleAreaTicks);
        }
        if (data.throwPersistentArea) {
            CustomThrowableAreas.createPersistentArea(serverLevel, center,
                    Math.max(0.5D, data.throwRadius), data, attacker);
        }
        discard();
    }

    /**
     * 生效范围内的玩家（球形范围，以落点为圆心）。
     *
     * <p>
     * 与手榴弹一致默认做视线判定；勾了「爆炸是否无视墙体」时跳过视线判定，
     * 并把可命中距离放宽「无视多少格墙体」这么多格。
     */
    private List<ServerPlayer> affectedPlayers(ServerLevel serverLevel, Vec3 center, double radius,
            CustomItemData data) {
        List<ServerPlayer> result = new ArrayList<>();
        double limit = radius + (data.throwIgnoreWalls ? Math.max(0, data.throwWallIgnoreBlocks) : 0.0D);
        for (ServerPlayer player : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (player.position().distanceTo(center) > limit) {
                continue;
            }
            if (!data.throwIgnoreWalls && !GrenadeEntity.hasExplosionLineOfSight(serverLevel, center, player)) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    /** 爆炸：范围内玩家按配置的死亡原因出局 + 粒子 + 音效（与手榴弹一致，不破坏方块）。 */
    private void explode(ServerLevel serverLevel, Vec3 center, List<ServerPlayer> targets, CustomItemData data) {
        Entity owner = getOwner();
        ServerPlayer attacker = owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        ResourceLocation deathReason = CustomItemRuntime.parseDeathReason(data.throwDeathReason,
                GameConstants.DeathReasons.GRENADE);
        for (ServerPlayer target : targets) {
            if (GameUtils.isPlayerAliveAndSurvival(target)) {
                GameUtils.killPlayer(target, true, attacker, deathReason);
            }
        }
        ParticleOptions particle = CustomItemRuntime.resolveParticle(data.throwExplosionParticle,
                TMMParticles.BIG_EXPLOSION);
        serverLevel.sendParticles(particle, center.x, center.y + 0.5D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.5D, center.z, 100, 0.5D, 0.5D, 0.5D,
                0.05D);
        SoundEvent sound = CustomItemRuntime.resolveSound(data.throwExplosionSound,
                TMMSounds.ITEM_GRENADE_EXPLODE);
        serverLevel.playSound(null, center.x, center.y, center.z, sound, SoundSource.PLAYERS, 5.0F,
                1.0F + serverLevel.random.nextFloat() * 0.1F - 0.05F);
    }

    // ==================== 钳子拆除 ====================

    /** 是否正在被拆除。 */
    public boolean isDefusing() {
        return defuseTicks > 0;
    }

    /** 开始拆除（{@code failPercent} 为拆除失败概率百分比）。 */
    public void beginDefuse(ServerPlayer user, int failPercent) {
        CustomItemData data = config();
        defuseTicks = data == null ? 1 : Math.max(1, data.throwDefuseTicks);
        defuseFailPercent = Math.max(0, Math.min(100, failPercent));
        defuserId = user.getUUID();
        entityData.set(DATA_DEFUSE_TOTAL, defuseTicks);
        entityData.set(DATA_DEFUSE_TICKS, defuseTicks);
    }

    /** 立刻拆除（拆除时间为 0 的情况，同粘性炸弹）。 */
    public void defuseInstantly() {
        dropSelf();
        discard();
    }

    /** 拆除完成：按失败概率决定「成功拆除」还是「直接引爆」。 */
    private void finishDefuse(CustomItemData data) {
        Player defuser = defuserId == null ? null : level().getPlayerByUUID(defuserId);
        boolean fail = defuser != null && defuser.getRandom().nextInt(100) < defuseFailPercent;
        if (fail) {
            detonate(data);
            return;
        }
        dropSelf();
        discard();
    }

    private void dropSelf() {
        if (!level().isClientSide()) {
            Block.popResource(level(), blockPosition(), getItem());
        }
    }
}

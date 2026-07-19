package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * 飞斧实体 —— 强盗蓄力投掷的斧子。
 *
 * <p>
 * 渲染由 {@code FlyingAxeRenderer} 直接绘制飞斧「物品模型」并翻滚旋转，
 * 因此看起来是一把真正的斧头（而非箭矢渲染器画出的「飞剑」）。
 *
 * <p>
 * 行为（沿用旧 ThrowingAxeEntity）：
 * <ul>
 * <li>直线飞行（无重力），最多穿透并击杀 {@link #MAX_PIERCE} 名玩家；</li>
 * <li>击杀上限后继续穿过后续玩家（不再击杀），直到撞上方块；</li>
 * <li>撞墙后钉在墙上，{@link #STICK_TICKS} tick（5 秒）后消失。</li>
 * </ul>
 *
 * <p>
 * AI 提示：这里刻意不调用 {@code super.onHitEntity}，因为原版飞行物在命中被判定为
 * 无敌 / 被规则取消伤害（PvP 阵营保护）的实体时会被弹开、停飞，破坏穿透效果。
 */
public class DanmukuEntity extends AbstractArrow {

    /** 最多穿透并击杀的玩家数量。 */
    public static final int MAX_PIERCE = 2;
    /** 钉在墙上后存活的 tick 数（20 tick = 1 秒）。 */
    public static final int STICK_TICKS = 20 * 5;
    /** 从未命中任何东西时的兜底存活 tick 数。 */
    private static final int MAX_FLIGHT_TICKS = 20 * 8;

    private final IntOpenHashSet hitPlayers = new IntOpenHashSet(4);
    private int killedPlayers = 0;
    /** 钉墙时记录的 tickCount，用于在渲染端冻结翻滚角度（-1 = 尚未钉住）。 */
    private int stuckTick = -1;

    public DanmukuEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED; // 不可被拾取
    }

    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }

    @Override
    public void playerTouch(Player player) {
    }

    public DanmukuEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity livingEntity, Level level,
            ItemStack itemStack) {
        super(entityType, livingEntity, level, itemStack, null);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED; // 不可被拾取
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** 是否已钉在墙上（供渲染器判断是否停止翻滚）。 */
    public boolean isStuck() {
        return this.inGround;
    }

    /** 钉墙瞬间的 tickCount（渲染器据此冻结翻滚角度）。 */
    public int getStuckTick() {
        return this.stuckTick;
    }

    /** 只允许命中「存活且生存模式」的玩家，且跳过已命中过的实体（配合无原版伤害实现穿透）。 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (this.hitPlayers.contains(entity.getId())) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        // 刻意不调用 super：避免原版弹射物伤害/弹开逻辑打断穿透。
        if (this.level().isClientSide) {
            return;
        }
        if (!(entityHitResult.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        if (this.getOwner() != null && target.getUUID().equals(this.getOwner().getUUID())) {
            return;
        }
        if (!this.hitPlayers.add(target.getId())) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }

        if (this.killedPlayers < MAX_PIERCE && this.getOwner() instanceof ServerPlayer owner) {
            this.killedPlayers++;

            Vec3 location = entityHitResult.getLocation();
            ServerLevel serverLevel = target.serverLevel();
            serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y + 1.0f, location.z, 12, 0.3, 0.3,
                    0.3, 0.2);
            serverLevel.players().forEach(p -> serverLevel.playSound(p, location.x, location.y, location.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.9f));

            GameUtils.killPlayer(target, true, owner, this.deathReason());
        }
        // 不移除、不减速：飞斧保持原速继续飞向下一目标 / 墙壁。
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult); // 钉在墙上（inGround = true，停止移动）
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.inGround && this.stuckTick < 0) {
            this.stuckTick = this.tickCount; // 冻结渲染端翻滚角度
        }
        if (this.inGround) {
            if (this.inGroundTime >= STICK_TICKS) {
                this.remove(RemovalReason.DISCARDED);
            }
        } else if (this.tickCount > MAX_FLIGHT_TICKS) {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    /** 死因归属：优先使用飞斧物品的注册 id，回退到固定 id。 */
    private ResourceLocation deathReason() {
        return Noellesroles.id("danmuku");
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return Items.BEEF.getDefaultInstance(); // 不生成掉落物
    }
}

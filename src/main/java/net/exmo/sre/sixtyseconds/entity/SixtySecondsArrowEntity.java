package net.exmo.sre.sixtyseconds.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 60s 弓/弩发射的箭矢（{@link SixtySecondsBowItem} 生成）：
 * <ul>
 *   <li>命中<b>怪物</b>（非玩家生物）→ 按 {@link #monsterDamage} 结算（Boss/装甲重锤有自己的封顶，
 *       故不用「即死」而是可缩放伤害——弓弱于枪但可叠效果）；</li>
 *   <li>命中<b>玩家</b>→ {@link SixtySecondsHealthSystem#applyInjury}（PvP 时段/同队门控，走倒地路径）；</li>
 *   <li>附加效果（{@link ArrowType.Effect}）：火=点燃、毒=中毒/污染、爆=小范围爆炸（落地也触发）。</li>
 * </ul>
 * 完全接管命中结算（不调用 {@code super.onHitEntity}，避免叠加原版箭伤）。
 */
public class SixtySecondsArrowEntity extends AbstractArrow {

    private float monsterDamage = 8.0F;
    private int playerInjury = 6;
    private int typeId = ArrowType.CRUDE.id;

    public SixtySecondsArrowEntity(EntityType<? extends SixtySecondsArrowEntity> type, Level level) {
        super(type, level);
    }

    public SixtySecondsArrowEntity(Level level, LivingEntity shooter, ItemStack pickup, ItemStack weapon) {
        super(org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_ARROW, shooter, level, pickup, weapon);
    }

    /** 发射时配置：箭矢类型 + 已按弓强度/充能算好的怪物伤害与玩家健康伤害。 */
    public void configure(ArrowType type, float monsterDamage, int playerInjury) {
        this.typeId = type.id;
        this.monsterDamage = monsterDamage;
        this.playerInjury = playerInjury;
        setBaseDamage(monsterDamage);
    }

    private ArrowType type() {
        return ArrowType.byId(typeId);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ArrowType.byId(typeId).item());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            ArrowType.Effect effect = type().effect;
            if (effect == ArrowType.Effect.FIRE) {
                level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            } else if (effect == ArrowType.Effect.POISON) {
                level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
            }
        } else if (tickCount > 20 * 30) {
            discard(); // 超时清理
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity hit = result.getEntity();
        Entity owner = getOwner();
        ServerPlayer shooter = owner instanceof ServerPlayer sp ? sp : null;
        if (SixtySecondsMod.isActive(serverLevel)) {
            if (hit instanceof ServerPlayer target && hit != owner
                    && GameUtils.isPlayerAliveAndSurvival(target)
                    && SixtySecondsMonsterEntity.isValidPrey(target)) {
                if (shooter != null && SixtySecondsHealthSystem.isPvpBlocked(serverLevel, shooter, target)) {
                    shooter.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.noellesroles.sixty_seconds.pvp_blocked"), true);
                } else {
                    SixtySecondsHealthSystem.applyInjury(target, shooter, playerInjury);
                    applyPlayerEffect(serverLevel, target);
                }
            } else if (hit instanceof LivingEntity living && !(hit instanceof net.minecraft.world.entity.player.Player)) {
                living.hurt(shooter != null ? damageSources().arrow(this, shooter)
                        : damageSources().generic(), monsterDamage);
                applyMonsterEffect(serverLevel, living);
            }
        }
        if (type().effect == ArrowType.Effect.EXPLODE) {
            explode(serverLevel);
        }
        playSound(SoundEvents.ARROW_HIT, 1.0F, 1.2F);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (type().effect == ArrowType.Effect.EXPLODE && level() instanceof ServerLevel serverLevel) {
            explode(serverLevel);
            discard();
            return;
        }
        super.onHitBlock(result);
    }

    /** 附加效果——对怪：火=点燃、毒=中毒。 */
    private void applyMonsterEffect(ServerLevel level, LivingEntity monster) {
        switch (type().effect) {
            case FIRE -> monster.igniteForSeconds(5);
            case POISON -> monster.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 6, 1));
            default -> {
            }
        }
    }

    /** 附加效果——对玩家：火=额外健康伤害、毒=污染上升。 */
    private void applyPlayerEffect(ServerLevel level, ServerPlayer player) {
        switch (type().effect) {
            case FIRE -> player.igniteForSeconds(3);
            case POISON -> {
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                stats.pollution = Math.min(100, stats.pollution + 8);
                stats.sync();
            }
            default -> {
            }
        }
    }

    /** 爆炸箭：小范围——怪物受 {@link #monsterDamage}，猎物玩家受健康伤害，均带击退。 */
    private void explode(ServerLevel level) {
        double radius = 3.0;
        Entity owner = getOwner();
        ServerPlayer shooter = owner instanceof ServerPlayer sp ? sp : null;
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 6, 1.0, 0.4, 1.0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.0F);
        for (Entity entity : level.getEntities(this, getBoundingBox().inflate(radius))) {
            if (entity instanceof ServerPlayer target && SixtySecondsMonsterEntity.isValidPrey(target)
                    && target != owner) {
                if (shooter == null || !SixtySecondsHealthSystem.isPvpBlocked(level, shooter, target)) {
                    SixtySecondsHealthSystem.applyInjury(target, shooter, playerInjury);
                }
            } else if (entity instanceof LivingEntity living
                    && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                living.hurt(shooter != null ? damageSources().arrow(this, shooter)
                        : damageSources().generic(), monsterDamage);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("SreMonsterDamage", monsterDamage);
        tag.putInt("SrePlayerInjury", playerInjury);
        tag.putInt("SreArrowType", typeId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        monsterDamage = tag.getFloat("SreMonsterDamage");
        playerInjury = tag.getInt("SrePlayerInjury");
        typeId = tag.getInt("SreArrowType");
    }
}

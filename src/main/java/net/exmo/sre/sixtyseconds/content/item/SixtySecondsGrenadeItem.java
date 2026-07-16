package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.index.TMMSounds;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsGrenadeEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 投掷类武器（燃烧瓶/土制炸弹/闪光弹/燃烧弹/破片手雷）：右键把手雷<b>丢出去</b>
 * （{@link SixtySecondsGrenadeEntity} 实体，带飞行轨迹与渲染），命中方块/实体后才引爆——
 * 对低语怪/夜袭者造成 {@code mobDamage}（可点燃），对范围内其他玩家造成
 * {@code playerHealthDamage} 健康伤害（走倒地/处决路径，PvP 受限期无效）或致盲。
 */
public class SixtySecondsGrenadeItem extends Item {
    private final double radius;
    private final float mobDamage;
    private final int playerHealthDamage;
    private final boolean fire;
    private final boolean blind;

    public SixtySecondsGrenadeItem(Properties properties, double radius, float mobDamage,
            int playerHealthDamage, boolean fire, boolean blind) {
        super(properties);
        this.radius = radius;
        this.mobDamage = mobDamage;
        this.playerHealthDamage = playerHealthDamage;
        this.fire = fire;
        this.blind = blind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.6F, 1.0F + (level.random.nextFloat() - 0.5F) / 10F);
        if (!level.isClientSide()) {
            SixtySecondsGrenadeEntity grenade = new SixtySecondsGrenadeEntity(
                    org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_GRENADE, level);
            grenade.setOwner(player);
            grenade.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            // 决定飞行实体的渲染外观与引爆参数（基类按 EntityData 同步这份 stack 到客户端）
            grenade.setItem(stack);
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.9F, 1.0F);
            level.addFreshEntity(grenade);
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 20 * 3);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 落点引爆（由 {@link SixtySecondsGrenadeEntity} 命中时调用）：对低语怪/夜袭者造成 {@code mobDamage}
     * （可点燃/减速），对范围内其他玩家造成健康伤害或致盲（统一受 {@code isPvpBlocked} 门控）。
     * {@code thrower} 可为 null（投掷者已下线/非玩家来源）。
     */
    public void explode(ServerLevel serverLevel, Vec3 impact, @Nullable ServerPlayer thrower) {
        AABB area = new AABB(impact, impact).inflate(radius);
        // getEntities(thrower, area)：thrower 非 null 时自动排除投掷者自己
        for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities(thrower, area)) {
            if (entity instanceof Mob mob
                    && (mob instanceof net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity
                            || mob.getTags().contains(SixtySecondsWhisperSystem.WHISPER_TAG)
                            || mob.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG))) {
                // 诱饵弹（零伤）：不伤怪，改为清仇恨并把范围内怪物吸引到爆点
                if (mobDamage <= 0 && !fire && !blind) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(impact.x, impact.y, impact.z, 1.25);
                    continue;
                }
                mob.hurt(thrower != null ? serverLevel.damageSources().playerAttack(thrower)
                        : serverLevel.damageSources().generic(), mobDamage);
                if (fire) {
                    mob.igniteForSeconds(4);
                }
                if (blind) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 5, 2,
                            false, false, false));
                }
            } else if (entity instanceof ServerPlayer target && target != thrower) {
                // 致盲同属战斗行为，与伤害一起受 PvP 限制（时段 + 同队友伤）
                if (SixtySecondsHealthSystem.isPvpBlocked(serverLevel, thrower, target)) {
                    if (thrower != null && (playerHealthDamage > 0 || blind)) {
                        thrower.displayClientMessage(Component.translatable(
                                "message.noellesroles.sixty_seconds.pvp_blocked"), true);
                    }
                    continue;
                }
                if (blind) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 5, 0,
                            false, false, false));
                }
                if (playerHealthDamage > 0) {
                    SixtySecondsHealthSystem.applyInjury(target, thrower, playerHealthDamage);
                }
            }
        }
        serverLevel.sendParticles(fire ? ParticleTypes.FLAME : blind ? ParticleTypes.FLASH
                : ParticleTypes.EXPLOSION, impact.x, impact.y + 0.5, impact.z,
                fire ? 40 : 8, radius / 2, 0.5, radius / 2, 0.02);
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                blind ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.2F, blind ? 1.4F : 0.9F);
    }
}

package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 勾爪：右键把准星指向的<b>任何有生命值的实体</b>（LivingEntity——怪物/玩家/动物都行）
 * 拉到自己面前。射程 {@link #RANGE} 格、冷却 {@link #COOLDOWN_TICKS}、耐久消耗 1/次；
 * 没勾中不耗耐久不进冷却。只拉人不造成伤害（不算攻击、不受 PvP 时段限制）。
 * 与钩索（{@link SixtySecondsGrapplingHookItem}，把<b>自己</b>荡向方块）互补。
 */
public class SixtySecondsClawHookItem extends Item {

    /** 射程（格）。 */
    public static final double RANGE = 20.0;
    /** 冷却（8 秒）。 */
    public static final int COOLDOWN_TICKS = 20 * 8;
    /** 目标被拉到距自己多近时不再施加拉力（防止叠模）。 */
    private static final double MIN_PULL_DIST = 2.0;

    public SixtySecondsClawHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        LivingEntity target = pickTarget(serverLevel, serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.claw_miss", (int) RANGE), true);
            serverPlayer.playNotifySound(SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResultHolder.consume(stack);
        }
        Vec3 pull = player.position().subtract(target.position());
        double dist = pull.length();
        if (dist > MIN_PULL_DIST) {
            // 速度随距离放大（封顶），加一点抬升让目标离地好被拽动
            double speed = Math.min(2.8, 0.7 + dist * 0.12);
            Vec3 velocity = pull.normalize().scale(speed).add(0, Math.min(0.5, 0.15 + dist * 0.02), 0);
            target.setDeltaMovement(velocity);
            target.hurtMarked = true; // 强制同步速度（对玩家目标尤其必要）
            target.fallDistance = 0.0F;
        }
        if (target instanceof ServerPlayer pulled) {
            pulled.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.claw_pulled",
                    player.getGameProfile().getName()), true);
        }
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(hand));
        serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.7F);
        return InteractionResultHolder.consume(stack);
    }

    /** 沿准星射线找最近的可拉实体（任何活着的 LivingEntity，不含自己；方块会挡住射线）。 */
    private static LivingEntity pickTarget(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // 先做方块射线，视线被墙挡住时截断勾取距离
        double reach = RANGE;
        var blockHit = player.pick(RANGE, 0, false);
        if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            reach = blockHit.getLocation().distanceTo(start);
        }
        Vec3 end = start.add(look.scale(reach));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, searchBox,
                entity -> entity instanceof LivingEntity living && living.isAlive()
                        && entity != player && !entity.isSpectator());
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }
}

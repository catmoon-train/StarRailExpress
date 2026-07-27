package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.YouluAnchorEntity;
import org.agmas.noellesroles.game.roles.killer.youlu.YouluPlayerComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 幽露技能物品「不请自来」。
 *
 * <p>点按触发，0.3s 前摇（缓慢 + 暗色粒子特效），之后执行技能。
 * 第一次使用：放置沿地面滑行的锚点（仅本人可见）。
 * 第二次使用：传送到锚点位置并回收，进入 30s 冷却。</p>
 */
public class YouluAnchorItem extends Item {

    /** 前摇时长（tick），0.3s = 6 tick。 */
    private static final int WINDUP_TICKS = 6;

    public YouluAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(user)
                || !gameWorld.isRole(user, ModRoles.YOULU)) {
            return InteractionResultHolder.pass(itemStack);
        }
        if (!(user instanceof ServerPlayer sp) || !(world instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
        }

        // 前摇 buff：0.3s 缓慢 II（玩家移动明显变慢）
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                WINDUP_TICKS + 2, 1, false, false, true));
        // 前摇音效
        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 0.4f);

        // 前摇 VFX：多段粒子依次播放
        scheduleWindupVFX(sp, serverLevel);
        // 前摇结束后执行技能
        Scheduler.schedule(() -> {
            if (!sp.isAlive() || sp.isSpectator()) return;
            sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            executeSkill(sp, serverLevel, itemStack);
        }, WINDUP_TICKS);

        return InteractionResultHolder.sidedSuccess(itemStack, false);
    }

    /** 在 0.3s 前摇期间分批播放粒子特效，营造"蓄能传送"的视觉节奏。 */
    private void scheduleWindupVFX(ServerPlayer sp, ServerLevel level) {
        Vec3 pos = sp.getEyePosition().add(sp.getLookAngle().scale(1.5));
        // tick 0（即刻）：一圈烟雾
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            level.sendParticles(ParticleTypes.SMOKE,
                    pos.x + Math.cos(angle) * 0.5, pos.y, pos.z + Math.sin(angle) * 0.5,
                    1, 0, 0.02, 0, 0.01);
        }
        // tick 2：portal 粒子增强传送感
        Scheduler.schedule(() -> {
            if (!sp.isAlive()) return;
            Vec3 p = sp.getEyePosition().add(sp.getLookAngle().scale(1.5));
            level.sendParticles(ParticleTypes.PORTAL,
                    p.x, p.y, p.z, 5, 0.3, 0.3, 0.3, 0.08);
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3f, 1.5f);
        }, 2);
        // tick 4：更大范围的烟雾 + portal
        Scheduler.schedule(() -> {
            if (!sp.isAlive()) return;
            Vec3 p = sp.getEyePosition().add(sp.getLookAngle().scale(1.5));
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12;
                double r = 1.0;
                level.sendParticles(ParticleTypes.SMOKE,
                        p.x + Math.cos(angle) * r, p.y + 0.3, p.z + Math.sin(angle) * r,
                        1, 0, 0.01, 0, 0.02);
            }
            level.sendParticles(ParticleTypes.PORTAL,
                    p.x, p.y, p.z, 8, 0.5, 0.5, 0.5, 0.06);
        }, 4);
    }

    /** 前摇完成后执行技能逻辑。 */
    private void executeSkill(ServerPlayer sp, ServerLevel serverLevel, ItemStack itemStack) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        YouluPlayerComponent component = YouluPlayerComponent.KEY.get(sp);
        YouluAnchorEntity anchor = component.getAnchor();

        if (anchor == null) {
            // 放置锚点
            YouluAnchorEntity fresh = new YouluAnchorEntity(ModEntities.YOULU_ANCHOR, serverLevel);
            float yaw = sp.getYRot();
            double rad = Math.toRadians(yaw);
            Vec3 start = sp.position().add(-Math.sin(rad), 0.1, Math.cos(rad));
            fresh.setPos(start.x, start.y, start.z);
            fresh.setup(sp.getUUID(), yaw, config.youluAnchorSpeed,
                    GameConstants.getInTicks(0, config.youluAnchorLifetimeSeconds));
            serverLevel.addFreshEntity(fresh);
            component.anchorUuid = fresh.getUUID();
            serverLevel.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.youlu.anchor_placed")
                            .withStyle(ChatFormatting.AQUA), true);
        } else {
            // 传送到锚点并回收
            Vec3 target = anchor.position();
            sp.teleportTo(target.x, target.y, target.z);
            component.discardAnchor();
            sp.getCooldowns().addCooldown(this,
                    GameConstants.getInTicks(0, config.youluAnchorCooldownSeconds));
            serverLevel.playSound(null, target.x, target.y, target.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.youlu.anchor_teleported")
                            .withStyle(ChatFormatting.AQUA), true);
            itemStack.shrink(1);
        }
    }

    /**
     * 技能物品防消耗保护。
     */
    public static void guardSkillItem(ServerPlayer sp, Item item) {
        Scheduler.schedule(() -> {
            if (!sp.isAlive() || sp.isSpectator() || sp.hasDisconnected()) return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.YOULU)) return;
            if (io.wifi.starrailexpress.util.SREItemUtils.hasItem(sp, item)) return;
            org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(sp, item.getDefaultInstance());
        }, 2);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.youlu_anchor.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}

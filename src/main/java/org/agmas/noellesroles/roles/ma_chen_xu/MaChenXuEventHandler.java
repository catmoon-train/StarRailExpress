package org.agmas.noellesroles.roles.ma_chen_xu;

import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/**
 * 布袋鬼（诡舍·缚灵）事件处理器
 */
public class MaChenXuEventHandler {

    /** 命中后前摇硬直（tick） */
    private static final int HIT_SELF_LOCK_TICKS = 3;

    /** 鬼缚效果持续时间（tick） */
    private static final int GHOST_CURSE_DURATION = 45 * 20;

    /**
     * 注册事件监听器
     */
    public static void register() {
        // 护盾/无敌事件
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            SREGameWorldComponent sreGameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (sreGameWorldComponent.isRole(victim, ModRoles.MA_CHEN_XU)) {
                var compc = MaChenXuPlayerComponent.KEY.get(victim);
                // 永久护盾（阶段4获得，一次性抵挡致命伤害）
                if (compc.permanentShield) {
                    compc.permanentShield = false;
                    compc.sync();
                    SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                    victim.displayClientMessage(Component.translatable("message.noellesroles.ma_chen_xu.trigger_shield")
                            .withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                // 里世界中布袋鬼无敌
                if (compc.otherworldActive) {
                    return false;
                }
                RoleUtils.changeRole(victim, SERoles.AMNESIAC);


            }
            return true;
        });

        // 布袋鬼攻击事件：命中特效 + 命中后自身3tick硬直（禁移动/禁攻击）
        AttackEntityCallback.EVENT.register(MaChenXuEventHandler::onEntityAttacked);
    }

    private static InteractionResult onEntityAttacked(Player attacker, Level world, InteractionHand hand,
            Entity entity, EntityHitResult hitResult) {
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(attacker instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof Player victim)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return InteractionResult.PASS;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRole(attacker, ModRoles.MA_CHEN_XU)) {
            return InteractionResult.PASS;
        }

        MaChenXuPlayerComponent comp = MaChenXuPlayerComponent.KEY.get(attacker);
        if (comp.stage <= 0) {
            return InteractionResult.PASS;
        }

        // 命中后前摇硬直：3tick无法移动、无法普通攻击、无法技能
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, HIT_SELF_LOCK_TICKS, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, HIT_SELF_LOCK_TICKS, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, HIT_SELF_LOCK_TICKS, 0, false, false, false));

        // 命中特效
        if (world instanceof ServerLevel sl) {
            Vec3 pos = victim.position();
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x, pos.y + 0.9, pos.z, 3, 0.8, 0.4, 0.8, 0.0);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1.0, pos.z, 8, 0.4, 0.6, 0.4, 0.01);
            sl.sendParticles(ParticleTypes.CRIT,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.5, 0.5, 0.5, 0.1);
            sl.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.0F, 0.85F);
        }

        // 里世界中附带鬼缚（用于加强打击感）
        if (comp.otherworldActive) {
            victim.addEffect(new MobEffectInstance(
                    ModEffects.GHOST_CURSE, GHOST_CURSE_DURATION, 0, false, false, true));
        }

        return InteractionResult.PASS;
    }
}
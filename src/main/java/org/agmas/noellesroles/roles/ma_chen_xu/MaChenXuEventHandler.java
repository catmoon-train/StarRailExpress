package org.agmas.noellesroles.roles.ma_chen_xu;

import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/**
 * 布袋鬼（诡舍·缚灵）事件处理器
 */
public class MaChenXuEventHandler {

    /** 鬼缚效果持续时间（tick） - 3秒 */
    private static final int GHOST_CURSE_DURATION = 45*20;

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

        // 布袋鬼攻击事件：命中特效 + 鬼缚诅咒
//        AttackEntityCallback.EVENT.register((attacker, world, hand, entity, hitResult) -> {
//            if (world.isClientSide()) return InteractionResult.PASS;
//            if (!(attacker instanceof ServerPlayer sp)) return InteractionResult.PASS;
//            if (!(entity instanceof Player victim)) return InteractionResult.PASS;
//
//            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
//            if (!gameWorld.isRole(attacker, ModRoles.MA_CHEN_XU)) return InteractionResult.PASS;
//            if (!GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;
//
//            MaChenXuPlayerComponent comp = MaChenXuPlayerComponent.KEY.get(attacker);
//            if (comp.stage <= 0) return InteractionResult.PASS;
//
//
//
//            // 里世界期间：施加鬼缚诅咒（隐身+定身+禁用物品+红粒子）
//            if (comp.otherworldActive) {
////                victim.addEffect(new MobEffectInstance(
////                        ModEffects.GHOST_CURSE, GHOST_CURSE_DURATION, 0, false, false, true));
////                victim.addEffect(new MobEffectInstance(
////                        MobEffects.INVISIBILITY, GHOST_CURSE_DURATION, 0, false, false, false));
//
//                // 鬼缚Title
//                if (victim instanceof ServerPlayer victimSp) {
//                    victimSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
//                    victimSp.connection.send(new ClientboundSetTitleTextPacket(
//                            Component.translatable("message.noellesroles.ma_chen_xu.ghost_curse")
//                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
//                }
//            }
//
//            return InteractionResult.PASS;
//        });
    }
}
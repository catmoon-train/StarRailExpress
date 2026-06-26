package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 骨杖攻击回调：亡灵之主手持骨杖左键攻击玩家时，为目标增加感染值并消耗 1 点耐久。
 * 不造成普通击杀，仅注入感染（感染满值后由 {@link UndeadLordPlayerComponent} 结算转化为亡灵）。
 */
public class BoneStaffHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register(BoneStaffHandler::onEntityAttacked);
    }

    private static InteractionResult onEntityAttacked(Player attacker, Level level, InteractionHand hand,
            Entity entity, EntityHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(attacker instanceof ServerPlayer serverAttacker)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof ServerPlayer victim)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = attacker.getItemInHand(hand);
        if (!stack.is(ModItems.BONE_STAFF)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverAttacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return InteractionResult.PASS;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (gameWorldComponent == null || !gameWorldComponent.isRole(serverAttacker, ModRoles.UNDEAD_LORD)) {
            return InteractionResult.PASS;
        }
        if (victim.getUUID().equals(serverAttacker.getUUID())) {
            return InteractionResult.PASS;
        }

        UndeadLordPlayerComponent comp = UndeadLordPlayerComponent.KEY.maybeGet(serverAttacker).orElse(null);
        if (comp == null) {
            return InteractionResult.PASS;
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        comp.addInfection(victim, (float) config.undeadLordBoneStaffInfection);

        // 消耗 1 点耐久
        if (!attacker.isCreative()) {
            stack.hurtAndBreak(1, attacker,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }

        level.playSound(null, attacker.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.7f, 0.9f);
        serverAttacker.displayClientMessage(
                Component.translatable("message.noellesroles.undead_lord.bone_staff_hit",
                        (int) config.undeadLordBoneStaffInfection).withStyle(ChatFormatting.DARK_PURPLE),
                true);

        // 不触发普通攻击/击杀，仅注入感染
        return InteractionResult.CONSUME;
    }
}

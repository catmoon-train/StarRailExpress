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
 * <p>
 * 采用 {@link AttackEntityCallback}（与警棍一致的可靠攻击钩子），避免依赖攻击力度判定导致漏触发。
 * 不造成普通击杀，仅注入感染（感染满值后由 {@link UndeadLordPlayerComponent} 结算转化为亡灵）。
 * 耐久耗尽后骨杖不会消失，而是进入充能冷却（攻击被阻止），冷却结束后由
 * {@link BoneStaffItem#inventoryTick} 自动恢复满耐久。
 * </p>
 */
public class BoneStaffHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register(BoneStaffHandler::onEntityDamaged);
    }

    private static InteractionResult onEntityDamaged(Player attacker, Level level, InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(attacker instanceof ServerPlayer serverAttacker)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = serverAttacker.getItemInHand(hand);
        if (!stack.is(ModItems.BONE_STAFF)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof ServerPlayer target)) {
            return InteractionResult.PASS;
        }
        if (target.getUUID().equals(serverAttacker.getUUID())) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverAttacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return InteractionResult.PASS;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (gameWorldComponent == null || !gameWorldComponent.isRole(serverAttacker, ModRoles.UNDEAD_LORD)) {
            return InteractionResult.PASS;
        }
        UndeadLordPlayerComponent comp = UndeadLordPlayerComponent.KEY.maybeGet(serverAttacker).orElse(null);
        if (comp == null) {
            return InteractionResult.PASS;
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int max = BoneStaffItem.maxDurability();

        // 充能冷却中：耐久耗尽尚未恢复，阻止攻击（不注入感染）。
        if (serverAttacker.getCooldowns().isOnCooldown(ModItems.BONE_STAFF)) {
            serverAttacker.displayClientMessage(
                    Component.translatable("message.noellesroles.undead_lord.bone_staff_recharging",
                            config.undeadLordBoneStaffRechargeSeconds).withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }

        // 注入感染
        comp.addInfection(target, (float) config.undeadLordBoneStaffInfection);

        // 消耗 1 点耐久（耗尽后不破坏，进入充能冷却）。
        if (!serverAttacker.isCreative()) {
            // 冷却已结束但尚未被 inventoryTick 恢复时，先补满再消耗，避免争用。
            if (stack.getDamageValue() >= max) {
                stack.setDamageValue(0);
            }
            int next = stack.getDamageValue() + 1;
            if (next >= max) {
                stack.setDamageValue(max);
                serverAttacker.getCooldowns().addCooldown(ModItems.BONE_STAFF,
                        config.undeadLordBoneStaffRechargeSeconds * 20);
            } else {
                stack.setDamageValue(next);
            }
        }

        serverAttacker.serverLevel().playSound(null, serverAttacker.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.7f, 0.9f);
        serverAttacker.displayClientMessage(
                Component.translatable("message.noellesroles.undead_lord.bone_staff_hit",
                        (int) config.undeadLordBoneStaffInfection).withStyle(ChatFormatting.DARK_PURPLE),
                true);

        // 取消普通攻击（仅注入感染，不造成普通击杀/伤害）。
        return InteractionResult.SUCCESS;
    }
}

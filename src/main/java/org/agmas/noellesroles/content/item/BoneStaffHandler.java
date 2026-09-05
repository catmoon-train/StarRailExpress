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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
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
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 骨杖攻击：亡灵之主左键命中玩家时注入感染值，并消耗 1 点耐久。
 * <p>
 * 主路径为 {@link BoneStaffItem#onServerAttack}（{@code LeftClickHurtable} 服务端钩子）。
 * {@link AttackEntityCallback} 作为兜底，避免个别调用链漏触发；两者共用冷却，不会重复注入。
 * 不造成普通击杀，仅注入感染（感染满值后由 {@link UndeadLordRoleData} 结算转化为亡灵）。
 * 耐久耗尽后骨杖不会消失，而是进入充能冷却，结束后由
 * {@link BoneStaffItem#inventoryTick} 自动恢复满耐久。
 * </p>
 */
public class BoneStaffHandler {

    /** 两次注入之间的最短间隔，防止同一击在多条钩子里重复结算。 */
    private static final int HIT_COOLDOWN_TICKS = 5;

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
        tryApplyInfection(serverAttacker, target, stack);
        // 取消原版击杀/伤害，无论本次是否成功注入。
        return InteractionResult.SUCCESS;
    }

    /**
     * 尝试为被击中的玩家注入感染。冷却中或条件不满足时不会重复结算。
     *
     * @return 是否实际注入了感染
     */
    public static boolean tryApplyInfection(ServerPlayer attacker, ServerPlayer target, ItemStack stack) {
        if (target.getUUID().equals(attacker.getUUID())) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(attacker.level());
        if (gameWorldComponent == null || !gameWorldComponent.isRole(attacker, ModRoles.UNDEAD_LORD)) {
            return false;
        }
        UndeadLordRoleData comp = RoleData.getNullable(UndeadLordRoleData.class, attacker);
        if (comp == null) {
            return false;
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int max = BoneStaffItem.maxDurability();

        if (attacker.getCooldowns().isOnCooldown(ModItems.BONE_STAFF)) {
            if (stack.getDamageValue() >= max) {
                attacker.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.bone_staff_recharging",
                                config.undeadLordBoneStaffRechargeSeconds).withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        comp.addInfection(target, BoneStaffItem.BONE_STAFF_INFECTION_PER_HIT);

        if (!attacker.isCreative()) {
            // 冷却已结束但尚未被 inventoryTick 恢复时，先补满再消耗，避免争用。
            if (stack.getDamageValue() >= max) {
                stack.setDamageValue(0);
            }
            int next = stack.getDamageValue() + 1;
            if (next >= max) {
                stack.setDamageValue(max);
                attacker.getCooldowns().addCooldown(ModItems.BONE_STAFF,
                        config.undeadLordBoneStaffRechargeSeconds * 20);
            } else {
                stack.setDamageValue(next);
                attacker.getCooldowns().addCooldown(ModItems.BONE_STAFF, HIT_COOLDOWN_TICKS);
            }
        } else {
            attacker.getCooldowns().addCooldown(ModItems.BONE_STAFF, HIT_COOLDOWN_TICKS);
        }

        attacker.serverLevel().playSound(null, attacker.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.7f, 0.9f);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.undead_lord.bone_staff_hit",
                        (int) BoneStaffItem.BONE_STAFF_INFECTION_PER_HIT).withStyle(ChatFormatting.DARK_PURPLE),
                true);
        return true;
    }
}

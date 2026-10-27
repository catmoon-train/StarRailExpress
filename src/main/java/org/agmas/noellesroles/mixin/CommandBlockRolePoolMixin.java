package org.agmas.noellesroles.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;

/**
 * 轮抽和志愿会用 Math.max(1, 杀手数) 保底抽一个杀手。
 * 命令方块在场时把杀手、绑定职业和普通平民换成启用列表中的平民职业，池子人数不变。
 * 魔术师与命令方块事件互斥：事件局不刷新魔术师。
 */
@Mixin(SREMurderGameMode.class)
public class CommandBlockRolePoolMixin {
    @Inject(method = "getAllRoles(IIIIILorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;ZLjava/util/List;I)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void replaceExcludedRoles(CallbackInfoReturnable<List<RoleInstance>> cir) {
        if (!CommandBlockDirector.occupiesKillerSlots()) {
            return;
        }
        List<RoleInstance> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        List<SRERole> enabledRoles = StupidExpress.getEnableRoles();
        enabledRoles.removeIf(role -> role == TMMRoles.CIVILIAN || !role.isInnocent()
                || role.isVigilanteTeam() || role.canUseKiller() || role.isNeutrals()
                || role.isOtherModeRole() || CommandBlockRules.isExcluded(role) || role == ModRoles.MAGICIAN);
        RoleAssignmentPool replacementPool = RoleAssignmentPool.create("CommandBlockCivilian", enabledRoles::contains);
        RoleAssignmentPool repeatPool = RoleAssignmentPool.createUnlimited("CommandBlockCivilianFallback",
                enabledRoles::contains);
        // 先扣掉本次已抽中的职业，优先遵守原有数量限制；职业不足时才允许重复。
        for (RoleInstance instance : original) {
            if (instance != null && enabledRoles.contains(instance.role())) {
                replacementPool.removeRoleCount(instance.role(), 1);
            }
        }
        List<RoleInstance> replaced = new ArrayList<>(original.size());
        boolean changed = false;
        for (RoleInstance instance : original) {
            if (instance != null && (CommandBlockRules.isExcluded(instance.role())
                    || instance.role() == ModRoles.MAGICIAN || instance.role() == TMMRoles.CIVILIAN)) {
                SRERole replacement = replacementPool.selectRole();
                if (replacement == null) {
                    replacement = repeatPool.selectRole();
                }
                // 启用列表为空时仍保留原有兜底，不能绕过禁用设置或减少玩家名额。
                replaced.add(new RoleInstance(UUID.randomUUID(), replacement != null ? replacement : TMMRoles.CIVILIAN));
                changed = true;
            } else {
                replaced.add(instance);
            }
        }
        if (changed) {
            cir.setReturnValue(replaced);
        }
    }
}

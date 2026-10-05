package org.agmas.noellesroles.game.roles.neutral.chef;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkillContext;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.role_data.neutral.ChefRoleData;

/**
 * 厨师（平民阵营）。
 *
 * <p>技能：在脚底放置一个「客户端」食物盘 / 饮料盘（参考建筑师的客户端墙——
 * 服务端只记账，方块由 S2C 包让每个客户端自己画）。
 * <ul>
 * <li>直接按技能切换键在「食物盘 / 饮料盘」之间切换（不需要潜行）；</li>
 * <li>只有厨师能放入，且食物盘只收「烹饪后的食物 / 一包零食」，饮料盘只收「一杯水」；</li>
 * <li>任何玩家都能取用，但同一名玩家两次取用之间有 30 秒冷却；</li>
 * <li>一局只能用 2 次，每次释放冷却 30 秒；盘子可以同时存在多个，直到当局游戏结束。</li>
 * </ul>
 */
public class ChefRole extends NormalRole {

    public ChefRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 技能：在脚底放置当前模式的「客户端」盘子。 */
    public static boolean useTrayAbility(RoleSkillContext ctx) {
        ServerPlayer player = ctx.player();
        ChefRoleData data = RoleData.getNullable(ChefRoleData.class, player);
        if (data == null) {
            return false;
        }
        return placeTray(player, data.isDrinkMode());
    }

    /** 技能切换：在「食物盘 / 饮料盘」之间切换。 */
    public static boolean switchTrayMode(RoleSkillContext ctx) {
        ChefRoleData data = RoleData.getNullable(ChefRoleData.class, ctx.player());
        if (data == null) {
            return false;
        }
        data.switchTrayMode();
        return true;
    }

    /**
     * 在玩家脚底放置盘子。返回 true 表示成功（会消耗技能次数与冷却）。
     */
    public static boolean placeTray(ServerPlayer player, boolean drink) {
        if (player == null) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        if (!ChefTrayManager.placeTray(player, pos, drink)) {
            // placeTray 内部已经针对「没有地面」给出提示；其它失败原因补一条通用提示
            if (!ChefTrayManager.hasTrayAt(pos)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.chef.tray_place_failed")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }
        return true;
    }
}

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.ChefRoleData;

/**
 * 厨师 HUD：显示当前选择的盘子类型（食物盘 / 饮料盘）与切换键。
 *
 * <p>位置压在 {@link UnifiedSkillHud} 那一行之上，避免和技能名/充能行重叠。
 */
public class ChefHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CHEF_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var opt = RoleData.getOptional(ChefRoleData.class, client.player);
            if (opt.isEmpty()) {
                return;
            }
            ChefRoleData data = opt.get();

            Font textRenderer = client.font;
            int x = client.getWindow().getGuiScaledWidth() - 10;
            int y = client.getWindow().getGuiScaledHeight() - 59;

            Component modeText = ChefRoleData.modeDisplayName(data.trayMode);
            int modeColor = data.isDrinkMode() ? 0xFF5EB7D8 : CommonColors.GREEN;
            context.drawString(textRenderer, modeText, x - textRenderer.width(modeText), y, modeColor);

            Component hintText = Component.translatable("hud.noellesroles.chef.mode.hint",
                    NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage());
            context.drawString(textRenderer, hintText, x - textRenderer.width(hintText), y - 11, 0xAAAAAA);
        });
    }
}

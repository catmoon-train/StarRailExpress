package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.MushroomScholarRoleData;

/** 菌菇学者的当前技能、切换键和冷却 HUD。 */
public final class MushroomScholarHud {
    private MushroomScholarHud() {}

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MUSHROOM_SCHOLAR_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()
                    || AdvancedCameraDirector.shouldOverride()) return;
            MushroomScholarRoleData data = RoleData.getNullable(MushroomScholarRoleData.class, client.player);
            if (data == null) return;

            Font font = client.font;
            int right = context.guiWidth() - 8;
            int y = context.guiHeight() - 48;
            Component toggle = Component.translatable("hud.noellesroles.mushroom_scholar.toggle_skill",
                    NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage());
            Component skill = Component.translatable("hud.noellesroles.mushroom_scholar.current_skill",
                    Component.translatable("hud.noellesroles.mushroom_scholar.skill."
                            + data.selectedSkill.name().toLowerCase()));
            int cooldown = data.selectedSkill == MushroomScholarRoleData.Skill.CULTIVATION
                    ? data.cultivationCooldownTicks : data.essenceCooldownTicks;
            Component status = cooldown > 0
                    ? Component.translatable("hud.noellesroles.mushroom_scholar.cooldown",
                            (cooldown + 19) / 20)
                    : Component.translatable("hud.noellesroles.mushroom_scholar.ready");

            context.drawString(font, toggle, right - font.width(toggle), y, 0xFFAAAAAA);
            context.drawString(font, skill, right - font.width(skill), y + 11, 0xFFE6D7FF);
            context.drawString(font, status, right - font.width(status), y + 22,
                    cooldown > 0 ? 0xFFFF5555 : 0xFF55FF77);
        });
    }
}

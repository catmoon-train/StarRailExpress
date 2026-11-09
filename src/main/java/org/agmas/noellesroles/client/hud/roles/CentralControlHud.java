package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.central_control.CentralControlRole;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.CentralControlRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.SecurityCameraClientState;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 中控员独立 HUD（不使用默认的统一技能 HUD）。
 *
 * <p>
 * 分两种状态：
 * <ul>
 * <li><b>普通状态</b>：右下角列出三个技能的名字与状态（冷却 / 价格 / 已用完），并标出当前选中的技能；</li>
 * <li><b>监控状态</b>（正在看某台摄像头）：只画监控页面右下角那几条，说明这台摄像头装了什么模块、
 * 激光模块是否就绪 / 冷却剩余多少、以及「按技能键释放激光」的提示。</li>
 * </ul>
 * 冷却一律读统一技能系统的 {@code SREAbilityPlayerComponent#getSkillState}（客户端会自己递减，
 * 与其它职业 HUD 的做法一致）。
 */
public final class CentralControlHud {

    // 配色取自 docs/ui_style.md：金色强调、米色正文、土褐色次要
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int BLUE = 0xFF5EB7D8;

    /** 三个技能的固定展示顺序（技能 id 的后缀，也是翻译键的最后一段）。 */
    private static final String[] SKILL_KEYS = {
            "dynamic_module",
            "infrared_scanner",
            "laser_module"
    };

    private CentralControlHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CENTRAL_CONTROL_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()) {
                return;
            }
            if (!SREClient.isRole(ModRoles.CENTRAL_CONTROL)) {
                return;
            }
            if (SecurityCameraClientState.isInSecurityMode()) {
                // 监控画面里只画监控页面的那几条（保安原本的 HUD 负责退出提示）
                renderSecurityMonitorOverlay(context, client);
                return;
            }
            renderSkillPanel(context, client);
        });
    }

    // ==================== 普通状态：三个技能 ====================

    private static void renderSkillPanel(FakeGuiGraphics context, Minecraft client) {
        CentralControlRoleData data = RoleData.getNullable(CentralControlRoleData.class, client.player);
        Font font = client.font;
        int right = context.guiWidth() - 8;
        int y = context.guiHeight() - 20 - SKILL_KEYS.length * 22;

        Component toggle = Component.translatable("hud.noellesroles.central_control.toggle_skill",
                NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage());
        context.drawString(font, toggle, right - font.width(toggle), y - 12, MUTED);

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
        int selected = ability.getSelectedSkill();

        for (int i = 0; i < SKILL_KEYS.length; i++) {
            String key = SKILL_KEYS[i];
            boolean active = i == selected;

            Component name = Component.translatable("skill.noellesroles.central_control." + key);
            int nameX = right - font.width(name) - (active ? 8 : 0);
            context.drawString(font, name, nameX, y, active ? GOLD : TEXT);
            if (active) {
                context.fill(right - 5, y + 1, right - 2, y + 7, GOLD);
            }

            Component status = statusText(client, ability, data, key);
            context.drawString(font, status, right - font.width(status), y + 10,
                    statusColor(client, ability, data, key));
            y += 22;
        }
    }

    /** 某个技能当前的状态文案。 */
    private static Component statusText(Minecraft client, SREAbilityPlayerComponent ability,
            CentralControlRoleData data, String key) {
        int cooldown = findCooldown(ability, key);
        if (cooldown > 0) {
            return Component.translatable("hud.noellesroles.central_control.cooldown", (cooldown + 19) / 20);
        }
        switch (key) {
            case "infrared_scanner" -> {
                int price = CentralControlRole.INFRARED_SCANNER_PRICE;
                if (MoneyUtils.getBalance(client.player) < price) {
                    return Component.translatable("hud.noellesroles.central_control.need_money", price);
                }
                return Component.translatable("hud.noellesroles.central_control.ready_with_price", price);
            }
            case "laser_module" -> {
                if (data != null && data.laserModuleUsed) {
                    return Component.translatable("hud.noellesroles.central_control.used");
                }
                int price = CentralControlRole.LASER_MODULE_PRICE;
                if (MoneyUtils.getBalance(client.player) < price) {
                    return Component.translatable("hud.noellesroles.central_control.need_money", price);
                }
                return Component.translatable("hud.noellesroles.central_control.limited_with_price", price);
            }
            default -> {
                return Component.translatable("hud.noellesroles.central_control.ready");
            }
        }
    }

    private static int statusColor(Minecraft client, SREAbilityPlayerComponent ability,
            CentralControlRoleData data, String key) {
        if (findCooldown(ability, key) > 0) {
            return RED;
        }
        if ("laser_module".equals(key) && data != null && data.laserModuleUsed) {
            return MUTED;
        }
        int price = switch (key) {
            case "infrared_scanner" -> CentralControlRole.INFRARED_SCANNER_PRICE;
            case "laser_module" -> CentralControlRole.LASER_MODULE_PRICE;
            default -> 0;
        };
        if (price > 0 && MoneyUtils.getBalance(client.player) < price) {
            return MUTED;
        }
        return GREEN;
    }

    /** 按技能 id 后缀找剩余冷却（技能 id 统一是 {@code central_control_<key>}）。 */
    private static int findCooldown(SREAbilityPlayerComponent ability, String key) {
        for (RoleSkill.Definition definition : RoleSkill.getDefinitions(ModRoles.CENTRAL_CONTROL)) {
            if (definition.id().getPath().endsWith(key)) {
                return ability.getSkillState(definition.id()).cooldown;
            }
        }
        return 0;
    }

    // ==================== 监控状态：页面右下角 ====================

    private static void renderSecurityMonitorOverlay(FakeGuiGraphics context, Minecraft client) {
        BlockPos cameraPos = SecurityCameraClientState.getCurrentCameraPos();
        if (cameraPos == null || client.level == null) {
            return;
        }
        if (!(client.level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)) {
            return;
        }
        Font font = client.font;
        int right = context.guiWidth() - 10;
        // 保安原本的退出提示画在底部 36px 处，这里往上让开
        int y = context.guiHeight() - 60;

        if (camera.hasDynamicModule()) {
            Component line = Component.translatable("hud.noellesroles.central_control.module_installed.dynamic")
                    .withStyle(ChatFormatting.AQUA);
            context.drawString(font, line, right - font.width(line), y, BLUE);
            y -= 11;
        }
        if (camera.hasInfraredScanner()) {
            Component line = Component.translatable("hud.noellesroles.central_control.module_installed.infrared")
                    .withStyle(ChatFormatting.AQUA);
            context.drawString(font, line, right - font.width(line), y, BLUE);
            y -= 11;
        }
        if (!camera.hasLaserModule()) {
            return;
        }

        Component laserLine;
        int color;
        if (camera.isBroken()) {
            laserLine = Component.translatable("hud.noellesroles.central_control.laser.offline");
            color = MUTED;
        } else if (camera.getLaserCooldown() > 0) {
            laserLine = Component.translatable("hud.noellesroles.central_control.laser.cooldown_hud",
                    (camera.getLaserCooldown() + 19) / 20);
            color = RED;
        } else {
            laserLine = Component.translatable("hud.noellesroles.central_control.laser.loaded");
            color = GOLD;
        }
        context.drawString(font, laserLine, right - font.width(laserLine), y, color);
        y -= 11;

        if (!camera.isBroken() && camera.getLaserCooldown() <= 0) {
            Component hint = Component.translatable("hud.noellesroles.central_control.laser.press_key",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            context.drawString(font, hint, right - font.width(hint), y, TEXT);
        }
    }

    /**
     * 当前监控页面是否接管技能键。
     *
     * <p>
     * 必须同时满足：自己就是中控员、正在看监控、该摄像头装了激光模块。
     * 前两条很关键——否则别的职业（例如保安）在这台摄像头前按技能键时，
     * 技能键会被吞掉，发出一个服务端会直接丢弃的激光请求，等于把技能按没了。
     */
    public static boolean isMonitorHandlingAbilityKey(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        if (!SecurityCameraClientState.isInSecurityMode()) {
            return false;
        }
        if (!SREClient.isRole(ModRoles.CENTRAL_CONTROL)) {
            return false;
        }
        BlockPos cameraPos = SecurityCameraClientState.getCurrentCameraPos();
        if (cameraPos == null
                || !(client.level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)) {
            return false;
        }
        return camera.hasLaserModule() && !camera.isBroken();
    }
}

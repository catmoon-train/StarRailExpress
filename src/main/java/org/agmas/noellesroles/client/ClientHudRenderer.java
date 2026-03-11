package org.agmas.noellesroles.client;

import java.awt.Color;

import org.agmas.noellesroles.AttendantHandler;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.NoellesRolesAbilityPlayerComponent;
import org.agmas.noellesroles.component.RecorderPlayerComponent;
import org.agmas.noellesroles.component.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.component.ClockmakerPlayerComponent;
import org.agmas.noellesroles.component.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.commander.CommanderHudRender;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.BartenderPlayerComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;

public class ClientHudRenderer {

    public static void registerRenderersEvent() {
        CommanderHudRender.register();
        WayfarerHudRenderer.registerRendererEvent();
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 记录员
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.RECORDER)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = RecorderPlayerComponent.KEY.get(client.player);
            // hud.noellesroles.recorder.process
            Component text = Component
                    .translatable("hud.noellesroles.recorder.requirement",
                            abpc.requiredCorrectCount)
                    .withStyle(ChatFormatting.GOLD);
            Component text2 = Component
                    .translatable("hud.noellesroles.recorder.process",
                            abpc.getCorrectGuesses(), abpc.requiredCorrectCount)
                    .withStyle(ChatFormatting.YELLOW);
            guiGraphics.drawString(font, text2, xOffset - font.width(text2), yOffset - font.lineHeight - 4,
                    Color.WHITE.getRGB());
            guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight * 2 - 8,
                    Color.WHITE.getRGB());
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.CLOCKMAKER)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = ClockmakerPlayerComponent.KEY.get(client.player);
            Component text = Component
                    .translatable("hud.noellesroles.clockmaker.use",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GOLD);
            if (abpc.isUsingSkill) {
                text = Component.translatable("hud.noellesroles.clockmaker.already_using")
                        .withStyle(ChatFormatting.DARK_AQUA);
            }
            // 按下技能键可花费125金币，减少游戏时间45秒并使世界时间加快2000tick。
            guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                    Color.WHITE.getRGB());
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染清道夫的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(client.player, ModRoles.CLEANER)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.cleaner.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                var text = Component
                        .translatable("hud.cleaner.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染JOJO的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.JOJO)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.jojo.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                var THE_WORLD = Component.translatable("hud.noellesroles.jojo.the_world").withStyle(ChatFormatting.GOLD,
                        ChatFormatting.BOLD);
                var text = Component
                        .translatable("hud.jojo.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage(),
                                THE_WORLD)
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染SAKUYA的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.MAID_SAKUYA)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.maid_sakuya.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                var text = Component
                        .translatable("hud.maid_sakuya.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染红美铃的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.HOAN_MEIRIN)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = HoanMeirinPlayerComponent.KEY.get(client.player);
            {
                var text = Component
                        .translatable("hud.hoan_meirin.armor",
                                abpc.armor)
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(font, text, 10, yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            if (abpc.loneyTime > 5 * 20) {
                // 孤独值
                var text1 = Component
                        .translatable("hud.hoan_meirin.lonely_value",
                                Component.literal(String.format("%ds", (60 - abpc.loneyTime / 20)))
                                        .withStyle(ChatFormatting.RED))
                        .withStyle(ChatFormatting.YELLOW);
                guiGraphics.drawString(font, text1, xOffset - font.width(text1), yOffset - font.lineHeight * 3 - 12,
                        Color.WHITE.getRGB());

                var text2 = Component
                        .translatable("hud.hoan_meirin.lonely_tip")
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(font, text2, xOffset - font.width(text2), yOffset - font.lineHeight * 2 - 8,
                        Color.WHITE.getRGB());
            }
            if (client.player.hasEffect(MobEffects.LEVITATION)) {
                var text = Component
                        .translatable("hud.hoan_meirin.ready_stop",
                                NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.hoan_meirin.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                var text = Component
                        .translatable("hud.hoan_meirin.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.GREEN);

                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染风精灵的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.WIND_YAOSE)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.wind_yaose.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                var text = Component
                        .translatable("hud.wind_yaose.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染小镇做题家的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.EXAMPLER)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            var psc = PlayerShopComponent.KEY.get(client.player);
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.exampler.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            } else {
                if (psc.balance < 100) {
                    var text = Component
                            .translatable("hud.exampler.money")
                            .withStyle(ChatFormatting.YELLOW);
                    guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                            Color.WHITE.getRGB());
                } else {
                    var allneiJuanSkill = Component
                            .translatable("hud.exampler.all_neijuan",
                                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GOLD);
                    guiGraphics.drawString(font, allneiJuanSkill, xOffset - font.width(allneiJuanSkill),
                            yOffset - font.lineHeight * 3 - 12,
                            Color.WHITE.getRGB());
                    var text = Component
                            .translatable("hud.exampler.ready")
                            .withStyle(ChatFormatting.AQUA);
                    guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                            Color.WHITE.getRGB());
                }

            }
            var chargeText = Component
                    .translatable("hud.exampler.charges", abpc.charges)
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, chargeText, xOffset - font.width(chargeText),
                    yOffset - font.lineHeight * 2 - 8,
                    Color.WHITE.getRGB());
            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染算命大师的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.FORTUNETELLER)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            var abpc = NoellesRolesAbilityPlayerComponent.KEY.get(client.player);
            var fpc = FortunetellerPlayerComponent.KEY.get(client.player);
            if (!fpc.protectedPlayers.isEmpty()) {
                int dy = yOffset - font.lineHeight * 2 - 12;
                for (var po : fpc.protectedPlayers) {
                    var pl = client.level.getPlayerByUUID(po.player);
                    if (pl == null)
                        continue;
                    var text = Component
                            .translatable("hud.fortuneteller.protecting_line",
                                    Component.literal(pl.getDisplayName().getString()).withStyle(ChatFormatting.GREEN),
                                    Component.literal((po.time / 20) + "s").withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.GOLD);
                    guiGraphics.drawString(font, text, xOffset - font.width(text), dy,
                            Color.WHITE.getRGB());
                    dy = dy - 2 - font.lineHeight;
                }
                var text = Component
                        .translatable("hud.fortuneteller.protecting_above")
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
            if (abpc.cooldown > 0) {
                var text = Component
                        .translatable("hud.fortuneteller.cooldown", abpc.cooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
            } else {
                var text = Component
                        .translatable("hud.fortuneteller.ready",
                                NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.YELLOW);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
            }

            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染老人的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(client.player, ModRoles.OLDMAN)) {
                return;
            }
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘
            if (client.player.getVehicle() != null && client.player.getVehicle() instanceof WheelchairEntity) {
                var text = Component
                        .translatable("hud.oldman.get_back", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
            }

            return;
        });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            // 渲染酒保的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent != null && (SREClient.gameComponent.isRole(client.player, ModRoles.BARTENDER)
                    || SREClient.gameComponent.isRole(client.player, TMMRoles.LOOSE_END))) {
                var comc = BartenderPlayerComponent.KEY.maybeGet(client.player).orElse(null);
                if (comc == null)
                    return;
                if (comc.getArmor() <= 0)
                    return;
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();
                var font = client.font;
                int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
                int xOffset = screenWidth - 10; // 距离右边缘
                var text = Component.translatable("hud.bartender.has_armor", comc.getArmor())
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
                return;
            }

            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(client.player, ModRoles.ATTENDANT)) {
                var comc = NoellesRolesAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
                if (comc == null)
                    return;
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();
                var font = client.font;
                int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
                int xOffset = screenWidth - 10; // 距离右边缘
                var text = Component.literal("");
                if (comc.cooldown <= 0) {
                    text.append(Component.translatable("hud.noellesroles.attendant.available",
                            Component.keybind("key.noellesroles.ability"), AttendantHandler.area_distance)
                            .withStyle(ChatFormatting.GOLD));
                } else {
                    text.append(Component.translatable("hud.noellesroles.attendant.cooldown", (comc.cooldown / 20))
                            .withStyle(ChatFormatting.RED));
                }

                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
                return;
            }
        });

        // 小偷HUD
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(client.player, ModRoles.THIEF)) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘

            var thiefComponent = ThiefPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (thiefComponent == null)
                return;

            // 显示当前模式
            Component progress = Component.literal("");
            var shopC = PlayerShopComponent.KEY.get(client.player);
            progress = Component.translatable("message.thief.honor_cost", shopC.balance, thiefComponent.honorCost)
                    .withStyle(ChatFormatting.GOLD);
            Component modeText;
            if (thiefComponent.currentMode == ThiefPlayerComponent.MODE_STEAL_MONEY) {
                modeText = Component.translatable("hud.thief.mode.money").withStyle(ChatFormatting.GOLD);
            } else if (thiefComponent.currentMode == ThiefPlayerComponent.MODE_STEAL_ITEM) {
                modeText = Component.translatable("hud.thief.mode.item").withStyle(ChatFormatting.AQUA);
            } else {
                modeText = Component.translatable("hud.thief.mode.sell").withStyle(ChatFormatting.LIGHT_PURPLE);
            }

            // 显示冷却或就绪状态
            int dy = yOffset - font.lineHeight - 4;
            if (thiefComponent.cooldown > 0) {
                var cdText = Component.translatable("hud.thief.cooldown", thiefComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(font, cdText, xOffset - font.width(cdText), dy, Color.WHITE.getRGB());
                dy -= font.lineHeight;
            } else {
                var readyText = Component
                        .translatable("hud.thief.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), dy, Color.WHITE.getRGB());
                dy -= font.lineHeight;
            }

            // 显示模式信息
            var modeInfo = Component.translatable("hud.thief.current_mode").withStyle(ChatFormatting.WHITE);
            guiGraphics.drawString(font, modeInfo, xOffset - font.width(modeInfo) - font.width(modeText), dy,
                    Color.WHITE.getRGB());
            guiGraphics.drawString(font, modeText, xOffset - font.width(modeText), dy, Color.WHITE.getRGB());

            dy -= font.lineHeight + 8;

            guiGraphics.drawString(font, progress, xOffset - font.width(progress), dy, Color.WHITE.getRGB());
        });

        // 仇杀客HUD
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.BLOOD_FEUDIST)) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘

            BloodFeudistPlayerComponent bfComponent = ModComponents.BLOOD_FEUDIST.maybeGet(client.player).orElse(null);
            if (bfComponent == null)
                return;

            int dy = yOffset;

            // 显示误杀人数
            var killText = Component
                    .translatable("hud.blood_feudist.accidental_kills", bfComponent.getAccidentalKillCount())
                    .withStyle(ChatFormatting.RED);
            guiGraphics.drawString(font, killText, xOffset - font.width(killText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 2;

            // 显示速度状态
            if (bfComponent.hasSpeed1() || bfComponent.hasSpeed2()) {
                Component speedLabel = bfComponent.hasSpeed2() ? Component.translatable("hud.blood_feudist.speed2")
                        : Component.translatable("hud.blood_feudist.speed1");
                Component speedStatus = bfComponent.isSpeedEnabled()
                        ? Component.translatable("hud.blood_feudist.enabled").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("hud.blood_feudist.disabled").withStyle(ChatFormatting.GRAY);
                Component speedText = Component.literal("").append(speedLabel).append(speedStatus);
                guiGraphics.drawString(font, speedText, xOffset - font.width(speedText), dy, Color.WHITE.getRGB());
                dy -= font.lineHeight + 2;
            }

            // 显示急迫状态
            if (bfComponent.hasHaste2()) {
                Component hasteLabel = Component.translatable("hud.blood_feudist.haste2");
                Component hasteStatus = bfComponent.isHasteEnabled()
                        ? Component.translatable("hud.blood_feudist.enabled").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("hud.blood_feudist.disabled").withStyle(ChatFormatting.GRAY);
                Component hasteText = Component.literal("").append(hasteLabel).append(hasteStatus);
                guiGraphics.drawString(font, hasteText, xOffset - font.width(hasteText), dy, Color.WHITE.getRGB());
                dy -= font.lineHeight + 2;
            }

            // 显示技能提示
            var readyText = Component
                    .translatable("hud.blood_feudist.toggle_effects",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.YELLOW);
            guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), dy, Color.WHITE.getRGB());
        });

        // 会计HUD
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.ACCOUNTANT)) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘

            var accountantComponent = org.agmas.noellesroles.component.AccountantPlayerComponent.KEY
                    .maybeGet(client.player).orElse(null);
            if (accountantComponent == null)
                return;
            int dy = yOffset;

            // 显示当前模式
            Component modeText;
            if (accountantComponent
                    .getCurrentMode() == org.agmas.noellesroles.component.AccountantPlayerComponent.MODE_INCOME) {
                modeText = Component.translatable("hud.accountant.mode.income").withStyle(ChatFormatting.GOLD);
            } else {
                modeText = Component.translatable("hud.accountant.mode.expense").withStyle(ChatFormatting.AQUA);
            }

            var modeInfo = Component.translatable("hud.accountant.current_mode").withStyle(ChatFormatting.WHITE);
            guiGraphics.drawString(font, modeInfo, xOffset - font.width(modeInfo) - font.width(modeText), dy,
                    Color.WHITE.getRGB());
            guiGraphics.drawString(font, modeText, xOffset - font.width(modeText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示技能提示
            Component skillText = Component.translatable("hud.accountant.skill_cost", 175)
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, skillText, xOffset - font.width(skillText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示被动收入倒计时
            int remainingSeconds = accountantComponent.getPassiveIncomeRemainingSeconds();
            Component incomeText = Component.translatable("hud.accountant.passive_income", remainingSeconds)
                    .withStyle(ChatFormatting.YELLOW);
            guiGraphics.drawString(font, incomeText, xOffset - font.width(incomeText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示切换模式提示
            var toggleText = Component
                    .translatable("hud.accountant.toggle_mode",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY);
            guiGraphics.drawString(font, toggleText, xOffset - font.width(toggleText), dy, Color.WHITE.getRGB());
        });

        // 药剂师HUD
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.ALCHEMIST)) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
            int xOffset = screenWidth - 10; // 距离右边缘

            var alchemistComponent = org.agmas.noellesroles.component.AlchemistPlayerComponent.KEY
                    .maybeGet(client.player).orElse(null);
            if (alchemistComponent == null)
                return;

            int dy = yOffset;

            // 显示当前选择的药剂
            int currentPotionIndex = alchemistComponent.getCurrentPotionIndex();
            Component potionName = Component.translatable("potion.noellesroles." + getPotionKey(currentPotionIndex));
            Component potionLabel = Component.translatable("hud.alchemist.current_potion")
                    .withStyle(ChatFormatting.WHITE);
            guiGraphics.drawString(font, potionLabel, xOffset - font.width(potionLabel) - font.width(potionName), dy,
                    Color.WHITE.getRGB());
            guiGraphics.drawString(font, potionName, xOffset - font.width(potionName), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示调制花费
            int goldCost = getPotionCost(currentPotionIndex);
            Component costText = Component.translatable("hud.alchemist.craft_cost", goldCost,
                    org.agmas.noellesroles.component.AlchemistPlayerComponent.MATERIALS_TO_CRAFT)
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, costText, xOffset - font.width(costText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示当前药剂的调制次数
            int craftCount = alchemistComponent.getCurrentPotionCraftCount();
            int maxCraftCount = org.agmas.noellesroles.component.AlchemistPlayerComponent.MAX_CRAFT_COUNT;
            Component countText = Component.translatable("hud.alchemist.craft_count", craftCount, maxCraftCount)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            guiGraphics.drawString(font, countText, xOffset - font.width(countText), dy, Color.WHITE.getRGB());
            dy -= font.lineHeight + 4;

            // 显示蹲下获取素材倒计时
            if (client.player.isShiftKeyDown()) {
                int remainingSeconds = alchemistComponent.getMaterialGatherRemainingSeconds();
                Component gatherText = Component.translatable("hud.alchemist.gather_countdown", remainingSeconds)
                        .withStyle(ChatFormatting.YELLOW);
                guiGraphics.drawString(font, gatherText, xOffset - font.width(gatherText), dy, Color.WHITE.getRGB());
                dy -= font.lineHeight + 4;
            }

            // 显示切换药剂提示
            var toggleText = Component
                    .translatable("hud.alchemist.switch_potion",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY);
            guiGraphics.drawString(font, toggleText, xOffset - font.width(toggleText), dy, Color.WHITE.getRGB());
        });
    }

    /**
     * 获取药剂的key（用于翻译）
     */
    private static String getPotionKey(int potionIndex) {
        return switch (potionIndex) {
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ADRENALINE -> "adrenaline";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ANTIBIOTIC -> "antibiotic";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_HEDINGHONG -> "hedinghong";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_DOGSKIN_PLASTER -> "dogskin_plaster";
            default -> "unknown";
        };
    }

    /**
     * 获取药剂的调制金币花费
     */
    private static int getPotionCost(int potionIndex) {
        return switch (potionIndex) {
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ADRENALINE,
                    org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ANTIBIOTIC ->
                100;
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_HEDINGHONG -> 175;
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_DOGSKIN_PLASTER -> 150;
            default -> 0;
        };
    }

}

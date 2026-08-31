package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.THHumanVillageRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

public class THKotiyaSanaeRole extends TouhouRole {
    public static final int COST = 175;

    public THKotiyaSanaeRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shops = new ArrayList<>();
        shops.add(new ShopEntry(ModItems.REIMU_GOHEI.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        shops.add(new ShopEntry(ModItems.DANMUKU.getDefaultInstance(), 300, ShopEntry.Type.TOOL));
        return shops;
    }

    public static void registerEvents() {
        RoleSkill.register(THHumanVillageRoles.KOTIYA_SANAE,
                RoleSkill.skill(SRE.id("kotiya_sanae"), "skill.noellesroles.kotiya_sanae", (ctx) -> {
                    final var player = ctx.player();
                    if (!MoneyUtils.hasBalance(player, COST)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds_money", COST)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    MoneyUtils.addToBalance(player, -COST);
                    triggerRandomWeather(player);
                    return true;
                }).recordReplay().showOnHud(true).announceToSelf().cooldownSeconds(90).build());
    }

    private static void triggerRandomWeather(ServerPlayer player) {
        int type = player.getRandom().nextInt(0, 7);
        if (type == 0) {
            // 放晴 0：所有人获得暂时的无限体力和sans恢复，立刻开灯
            player.serverLevel().setWeatherParameters(20 * 60 * 10, 0, false, false);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(ModEffects.INFINITE_STAMINA, 20 * 10, 1, false, false, true));
                    p.addEffect(ModEffects.of(ModEffects.MOOD_REGENERATION, 20 * 10, 1, false, false, true));
                }
            }
            SREWorldBlackoutComponent.KEY.get(player.level()).stopBlackout();
        } else if (type == 1) {
            // 骤雨 1：所有人获得10s临时护盾
            player.serverLevel().setWeatherParameters(0, 20 * 30, true, true);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    SREArmorPlayerComponent.KEY.get(p).addTimedArmor(1, 10 * 20);
                }
            }
        } else if (type == 2) {
            // 狂风 2：所有人获得速度2 10s
            player.serverLevel().setWeatherParameters(0, 20 * 30, true, true);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 20 * 10, 1, false, false, true));
                }
            }
        } else if (type == 3) {
            // 沙尘 3：所有人失去透视 10s
            player.serverLevel().setWeatherParameters(20 * 120, 0, false, false);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(ModEffects.NO_INSTINCT, 20 * 10, 1, false, false, true));
                }
            }
        } else if (type == 4) {
            // 雷闪 4：所有人发光 10s
            player.serverLevel().setWeatherParameters(0, 20 * 30, true, true);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(MobEffects.GLOWING, 20 * 10, 1, false, false, true));
                }
            }
        } else if (type == 5) {
            // 雾霾 5：所有人无法使用物品

            player.serverLevel().setWeatherParameters(0, 20 * 30, true, false);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(ModEffects.USED_BANED, 20 * 10, 1, false, false, true));
                }
            }
        } else if (type == 6) {
            // 飞雪 6：所有人获得缓慢2
            player.serverLevel().setWeatherParameters(0, 20 * 30, true, false);
            for (final var p : player.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    p.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 20 * 10, 1, false, false, true));
                }
            }
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.kotiya_sanae.actionbar",
                Component.translatable("skill.noellesroles.kotiya_sanae.weather." + type)
                        .withStyle(ChatFormatting.GREEN))
                .withStyle(ChatFormatting.GOLD), true);
        SRENetworkMessageUtils.sendTitle(player,
                Component.translatable("message.noellesroles.kotiya_sanae.title",
                        Component.translatable("skill.noellesroles.kotiya_sanae.weather." + type)
                                .withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.AQUA));
        SRENetworkMessageUtils.sendSubtitle(player,
                Component.translatable("message.noellesroles.kotiya_sanae.subtitle"));
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("message.replay.noellesroles.kotiya_sanae",
                GameReplayUtils.getReplayPlayerDisplayText(player, true),
                Component.translatable("skill.noellesroles.kotiya_sanae.weather." + type)
                        .withStyle(ChatFormatting.GREEN)));
    }

}

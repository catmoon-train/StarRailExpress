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

package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.morph.MorphApiClient;
import io.wifi.starrailexpress.network.packet.MobRiotStateS2CPacket;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class MobRiotHudRenderer {
    private static boolean active;
    private static boolean day;
    private static int remainingSeconds;
    private static int tokens;
    private static int tokenGoal;
    private static boolean unlocked;

    private MobRiotHudRenderer() {
    }

    public static void update(MobRiotStateS2CPacket packet) {
        active = packet.active();
        day = packet.day();
        remainingSeconds = packet.remainingSeconds();
        tokens = packet.tokens();
        tokenGoal = packet.tokenGoal();
        unlocked = packet.unlocked();
    }

    public static void reset() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isNight() {
        return active && !day;
    }

    public static void registerClientEvents() {
        OnRenderRoleName.RENDER_PLAYER_NAME.register((player, target, context, tickCounter, font) -> {
            if (!shouldShowDisguiseName(player, target)) {
                return TrueFalseAndCustomResult.pass();
            }
            return TrueFalseAndCustomResult.custom(Component
                    .literal("????????" + "X".repeat(player.getRandom().nextInt(6)))
                    .withStyle(style -> style.applyFormats(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_PURPLE)));
        });
        OnRenderRoleName.RENDER_PLAYER_ROLE.register((player, target, context, tickCounter, font) ->
                shouldShowDisguiseName(player, target)
                        ? TrueFalseAndCustomResult.disallow()
                        : TrueFalseAndCustomResult.pass());
        OnRenderRoleName.RENDER_PLAYER_COHORT.register((player, target, context, tickCounter, font) ->
                shouldShowDisguiseName(player, target)
                        ? TrueFalseAndCustomResult.disallow()
                        : TrueFalseAndCustomResult.pass());
        OnRenderRoleName.RENDER_PLAYER_MODIFIER.register((player, target, context, tickCounter, font) ->
                shouldShowDisguiseName(player, target)
                        ? TrueFalseAndCustomResult.disallow()
                        : TrueFalseAndCustomResult.pass());
    }

    private static boolean shouldShowDisguiseName(Player viewer, Player target) {
        if (!isNight() || target == null) {
            return false;
        }
        if (viewer != null && !GameUtils.isPlayerAliveAndSurvival(viewer) && !SREClient.hasPenalty()) {
            return false;
        }
        return isDisguised(target);
    }

    private static boolean isDisguised(Player target) {
        if (target instanceof AbstractClientPlayer clientPlayer && MorphApiClient.isTextureMorph(clientPlayer)) {
            return true;
        }
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack stack = target.getInventory().getItem(i);
            if (stack.is(TMMItems.MOB_PSYCHO_DISGUISE)) {
                return true;
            }
        }
        return false;
    }

    public static void render(Font font, FakeGuiGraphics context) {
        if (!active) {
            return;
        }
        Component phase = Component.translatable(day ? "hud.sre.mob_riot.day" : "hud.sre.mob_riot.night",
                remainingSeconds);
        Component tokensLine = Component.translatable("hud.sre.mob_riot.tokens", tokens, tokenGoal);
        int x = context.guiWidth() / 2;
        int y = 26;
        context.drawCenteredString(font, phase, x, y, day ? 0xFFE8B86D : 0xFFC08CFF);
        context.drawCenteredString(font, tokensLine, x, y + 10, 0xFFFFE6A3);
        if (unlocked) {
            context.drawCenteredString(font, Component.translatable("hud.sre.mob_riot.unlocked"), x, y + 20, 0xFF7CFF9A);
        }
    }
}

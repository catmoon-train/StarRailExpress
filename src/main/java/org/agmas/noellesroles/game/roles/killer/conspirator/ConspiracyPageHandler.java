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

package org.agmas.noellesroles.game.roles.killer.conspirator;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.bouns.BounsRoles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 「阴谋书页」的非阴谋家使用路径。
 *
 * <p>书页不再限制职业：任何存活玩家拿到都能记录某位玩家的身份。阴谋家本体仍走
 * {@code ConspiratorRoleData}（带完整的目标列表 / HUD / 回放），其它职业由本类接管：</p>
 * <ul>
 * <li>猜中 → 目标进入 10 秒倒计时，结束时心脏麻痹死亡；</li>
 * <li>猜中奖励 100 金币<b>只给杀手阵营</b>（{@link RoleTeam#isKiller}，不含杀手方中立）；</li>
 * <li>猜错 3 次 → 使用者因「阴谋反噬」死亡（与阴谋家规则一致）。</li>
 * </ul>
 */
public final class ConspiracyPageHandler {

    private static final int DEATH_COUNTDOWN = 10 * 20;
    private static final int MAX_WRONG_GUESSES = 3;
    private static final int REWARD = 100;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ConspiracyPageHandler() {
    }

    private static final class State {
        final Map<UUID, ResourceLocation> guesses = new HashMap<>();
        final Map<UUID, Integer> countdowns = new HashMap<>();
        int wrongGuessCount;
    }

    /** 注册倒计时推进（在服务端初始化时调用一次）。 */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ConspiracyPageHandler::tick);
    }

    /**
     * 非阴谋家使用书页时的猜测。
     *
     * @return 是否猜中
     */
    public static boolean makeGuess(Player player, UUID targetUuid, ResourceLocation roleId) {
        if (!(player instanceof ServerPlayer user) || targetUuid == null || roleId == null) {
            return false;
        }
        Player target = player.level().getPlayerByUUID(targetUuid);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null) {
            return false;
        }
        SRERole actualRole = game.getRole(target);
        if (actualRole == null) {
            return false;
        }
        String targetName = target.getName().getString();

        // 大肥鱼免疫：既不算猜中，也不进入倒计时
        if (BounsRoles.FAT_FISH_ID.equals(actualRole.identifier())) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.conspirator.immune", targetName)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        State state = STATES.computeIfAbsent(user.getUUID(), id -> new State());
        state.guesses.put(targetUuid, roleId);

        if (actualRole.identifier().equals(roleId)) {
            state.countdowns.put(targetUuid, DEATH_COUNTDOWN);
            // 猜中奖励只给杀手阵营（不含杀手方中立）
            SRERole userRole = game.getRole(user);
            if (userRole != null && RoleTeam.isKiller(userRole)) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(user);
                if (shop != null) {
                    shop.setBalance(REWARD + shop.balance);
                }
            }
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.conspirator.correct", targetName)
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    true);
            if (target instanceof ServerPlayer targetServer) {
                targetServer.displayClientMessage(
                        Component.translatable("message.noellesroles.conspirator.cursed")
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
            }
            return true;
        }

        state.wrongGuessCount++;
        if (state.wrongGuessCount >= MAX_WRONG_GUESSES) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.conspirator.too_many_wrong")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    true);
            GameUtils.killPlayer(user, true, null, Noellesroles.id("conspiracy_backfire"));
            state.guesses.clear();
            state.countdowns.clear();
            state.wrongGuessCount = 0;
            return false;
        }
        user.displayClientMessage(
                Component.translatable("message.noellesroles.conspirator.wrong_with_count", targetName,
                        MAX_WRONG_GUESSES - state.wrongGuessCount)
                        .withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    private static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            ServerPlayer user = server.getPlayerList().getPlayer(entry.getKey());
            if (user == null || !GameUtils.isPlayerAliveAndSurvival(user)) {
                iterator.remove();
                continue;
            }
            State state = entry.getValue();
            state.countdowns.entrySet().removeIf(countdown -> {
                ServerPlayer target = server.getPlayerList().getPlayer(countdown.getKey());
                if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                    return true;
                }
                int remaining = countdown.getValue() - 1;
                countdown.setValue(remaining);
                if (remaining % 200 == 0 && remaining > 0) {
                    target.displayClientMessage(
                            Component.translatable("message.noellesroles.conspirator.countdown", remaining / 20)
                                    .withStyle(ChatFormatting.DARK_PURPLE),
                            true);
                }
                if (remaining <= 0) {
                    // 心脏麻痹死因（隐藏真实原因），凶手记为使用者
                    GameUtils.killPlayer(target, true, user, Noellesroles.id("heart_attack"));
                    return true;
                }
                return false;
            });
            if (state.countdowns.isEmpty() && state.guesses.isEmpty()) {
                iterator.remove();
            }
        }
    }
}

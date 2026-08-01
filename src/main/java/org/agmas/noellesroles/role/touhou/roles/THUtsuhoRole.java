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

package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.api.TouhouRole;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public class THUtsuhoRole extends TouhouRole {
    public static final HashMap<UUID, UtsuhoNeedDrinkInfo> NEED_DRINK_TIME = new HashMap<>();

    private static record UtsuhoNeedDrinkInfo(long time, ServerPlayer killer) {
    }

    public static final int SKILL_RANGE = 4;
    public static final int MAX_PLAYER_COUNT = 6;
    public static final int DRINK_THRESHOLD = 10 * 20;

    public THUtsuhoRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void resetVariables() {
        NEED_DRINK_TIME.clear();
    }

    @Override
    public void serverTick(ServerPlayer player) {
        final var level = player.serverLevel();

        ArrayList<UUID> needclear = new ArrayList<>();
        ArrayList<ServerPlayer> victims = new ArrayList<>();
        final long timenow = level.getGameTime();
        for (final var entry : NEED_DRINK_TIME.entrySet()) {

            UUID puid = entry.getKey();
            ServerPlayer p = findServerPlayerByUuid(level, puid);
            if (p == null) {
                needclear.add(puid);
                continue;
            }

            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                needclear.add(puid);
                return;
            }
            UtsuhoNeedDrinkInfo info = entry.getValue();
            long boomTime = info.time();
            if (timenow >= boomTime) {
                victims.add(p);
                continue;
            }

            if (p.isInWater()) {
                p.removeEffect(MobEffects.GLOWING);
                needclear.add(puid);
            }
            if (timenow % 30 == 0) {
                p.playNotifySound(NRSounds.C4_BEEP, SoundSource.MASTER, 1f, 1f);
            }
        }
        for (var p : needclear) {
            NEED_DRINK_TIME.remove(p);
        }

        for (var p : victims) {
            var info = NEED_DRINK_TIME.get(p.getUUID());
            GameUtils.killPlayer(p, true, info.killer(), GameConstants.DeathReasons.RADIATION);
            NEED_DRINK_TIME.remove(p.getUUID());
        }
    }

    private ServerPlayer findServerPlayerByUuid(ServerLevel level, UUID puid) {
        for (int i = 0; i < level.players().size(); ++i) {
            ServerPlayer player = level.players().get(i);
            if (puid.equals(player.getUUID())) {
                return player;
            }
        }
        return null;
    }

    public static boolean skillHandler(RoleSkillContext context) {
        final var player = context.player();
        final var level = player.serverLevel();
        ArrayList<ServerPlayer> victims = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (victims.size() >= MAX_PLAYER_COUNT)
                break;
            if (p.getUUID().equals(player.getUUID()))
                continue;
            if (RoleUtils.isPlayerTheJob(p, THMiscRoles.REIUJI_UTSUHO))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (p.distanceToSqr(player) <= SKILL_RANGE * SKILL_RANGE) {
                victims.add(p);
            }
        }
        final long timenow = level.getGameTime();
        player.addEffect(ModEffects.of(MobEffects.GLOWING, DRINK_THRESHOLD, 1, true, true, true));
        for (var p : victims) {
            p.addEffect(ModEffects.of(MobEffects.GLOWING, DRINK_THRESHOLD, 1, true, true, true));
            NEED_DRINK_TIME.put(p.getUUID(), new UtsuhoNeedDrinkInfo(timenow + DRINK_THRESHOLD, player));
            p.playNotifySound(NRSounds.C4_BEEP, SoundSource.MASTER, 1f, 1f);
            SRENetworkMessageUtils.sendBroadcast(p,
                    Component.translatable("skill.noellesroles.utsuho.victim", DRINK_THRESHOLD / 20));
            SRENetworkMessageUtils.sendCODSubtitleToPlayer(p,
                    Component.translatable("skill.noellesroles.utsuho.victim_warning.title"),
                    Component.translatable("skill.noellesroles.utsuho.victim_warning.subtitle", DRINK_THRESHOLD / 20),
                    100,
                    Color.RED.getRGB(), false, SubtitleS2CPayload.POS_CENTER);
        }
        player.displayClientMessage(Component.translatable("skill.noellesroles.utsuho.triggered",
                Component.translatable("skill.noellesroles.utsuho").withStyle(ChatFormatting.GOLD), victims.size(),
                DRINK_THRESHOLD / 20).withStyle(ChatFormatting.AQUA), true);
        player.playNotifySound(NRSounds.C4_BEEP, SoundSource.MASTER, 1f, 1f);
        return true;
    }

    public static void playerDrink(Player player) {
        if (player == null)
            return;
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        sp.removeEffect(MobEffects.GLOWING);
        NEED_DRINK_TIME.remove(sp.getUUID());
    }
}

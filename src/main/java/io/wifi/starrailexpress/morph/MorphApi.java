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

package io.wifi.starrailexpress.morph;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.morph.ClientMorphCache;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 统一变形 API。
 * <p>
 * 服务端写入外观后会同步到所有客户端。玩家变形为另一名玩家时，皮肤、帽子、名牌与身份玩偶
 * 一律跟随「显示皮肤拥有者」，避免用赞助玩偶 / 名牌识破伪装。
 * <p>
 * 贴图变形没有可复制的真实玩家，帽子 / 名牌前缀 / 玩偶按隐藏处理（与固定职业皮肤一致）。
 *
 * <pre>{@code
 * MorphApi.morphToPlayer(player, target.getUUID());
 * MorphApi.morphToRandomPlayer(player);
 * MorphApi.morphToTexture(player, SRE.id("textures/entity/disguise/disguise_skin_1.png"), false);
 * MorphApi.clearMorph(player);
 * }</pre>
 */
public final class MorphApi {

    private MorphApi() {
    }

    public static MorphAppearance getAppearance(Player player) {
        if (player == null) {
            return MorphAppearance.NONE;
        }
        if (player.level() != null && player.level().isClientSide) {
            return ClientMorphCache.get(player.getUUID());
        }
        return MorphManager.get(player.getUUID());
    }

    public static MorphAppearance getAppearance(UUID uuid) {
        if (uuid == null) {
            return MorphAppearance.NONE;
        }
        return MorphManager.get(uuid);
    }

    public static boolean isMorphed(Player player) {
        return !getAppearance(player).isNone();
    }

    /**
     * 变形成指定玩家：复制其皮肤，并绑定其帽子 / 名牌 / 身份玩偶。
     *
     * @param durationTicks 持续 tick；{@code <=0} 直到 {@link #clearMorph} 或玩家重置
     */
    public static boolean morphToPlayer(ServerPlayer player, UUID targetUuid, int durationTicks) {
        if (player == null || targetUuid == null) {
            return false;
        }
        if (player.getUUID().equals(targetUuid)) {
            return clearMorph(player);
        }
        return MorphManager.set(player, MorphAppearance.ofPlayer(targetUuid), expireAt(durationTicks));
    }

    public static boolean morphToPlayer(ServerPlayer player, UUID targetUuid) {
        return morphToPlayer(player, targetUuid, 0);
    }

    public static boolean morphToPlayer(ServerPlayer player, Player target) {
        return target == null ? false : morphToPlayer(player, target.getUUID(), 0);
    }

    public static boolean morphToPlayer(ServerPlayer player, Player target, int durationTicks) {
        return target == null ? false : morphToPlayer(player, target.getUUID(), durationTicks);
    }

    /**
     * 随机变形成一名存活玩家（默认排除自己与旁观/创造）。
     */
    public static boolean morphToRandomPlayer(ServerPlayer player, int durationTicks) {
        return morphToRandomPlayer(player, MorphApi::defaultRandomCandidate, durationTicks);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player) {
        return morphToRandomPlayer(player, 0);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player, Predicate<ServerPlayer> filter) {
        return morphToRandomPlayer(player, filter, 0);
    }

    public static boolean morphToRandomPlayer(ServerPlayer player, Predicate<ServerPlayer> filter,
            int durationTicks) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        Predicate<ServerPlayer> predicate = filter == null ? MorphApi::defaultRandomCandidate : filter;
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (predicate.test(other)) {
                candidates.add(other);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ServerPlayer target = candidates.get(player.getRandom().nextInt(candidates.size()));
        return morphToPlayer(player, target.getUUID(), durationTicks);
    }

    /**
     * 使用指定贴图变形（wide/slim）。没有真实玩家可复制，外观附属物隐藏。
     */
    public static boolean morphToTexture(ServerPlayer player, ResourceLocation texture, boolean slim,
            int durationTicks) {
        if (player == null || texture == null) {
            return false;
        }
        return MorphManager.set(player, MorphAppearance.ofTexture(texture, slim), expireAt(durationTicks));
    }

    public static boolean morphToTexture(ServerPlayer player, ResourceLocation texture, boolean slim) {
        return morphToTexture(player, texture, slim, 0);
    }

    public static boolean clearMorph(ServerPlayer player) {
        return MorphManager.clear(player, false);
    }

    private static boolean defaultRandomCandidate(ServerPlayer player) {
        return GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static long expireAt(int durationTicks) {
        if (durationTicks <= 0) {
            return 0;
        }
        return SRE.getTicksFromGameStart() + durationTicks;
    }

}

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

package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 无我 / 无妄的「待复活尸体」守卫。
 * <p>
 * 两人任何一方存活、另一方死亡时，死者还可以被存活方用「还魂」复活，
 * 因此该尸体必须被标记为待复活尸体且无法被交互（复用尸体已有的
 * {@link PlayerBodyEntity#isNonInteractable()} 机制）。
 * 当搭档也死亡（组合彻底无法复活）时自动解除标记，避免尸体被永久锁死。
 * <p>
 * 只跟踪由本类亲自标记过的尸体，不会误清除除颤器 / 蜂后 / 万圣节等
 * 其它复活逻辑加上的不可交互标记。
 */
public final class AnatmanAsatyaCorpseGuard {

    /** 被本类标记为待复活的尸体主人 UUID；尸体消失时会自动移除，无需按局清理。 */
    private static final Set<UUID> GUARDED_OWNERS = new HashSet<>();

    private AnatmanAsatyaCorpseGuard() {
    }

    /**
     * 尸体生成时调用：死者是无我/无妄之一且搭档仍存活时，把尸体标记为待复活、不可交互。
     */
    public static void onBodySpawned(PlayerBodyEntity body, Player victim) {
        if (body == null || victim == null || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (game == null || !game.isRunning()) {
            return;
        }
        SRERole partnerRole = partnerRoleOf(victim, game);
        if (partnerRole == null || !isPartnerAlive(level, game, partnerRole)) {
            return;
        }
        body.setNonInteractable(true);
        GUARDED_OWNERS.add(victim.getUUID());
    }

    /**
     * 组合中有人死亡后调用：搭档也不在了就解除本类加的待复活标记。
     * 复活路径（{@code GameUtils#revivePlayerToItsRoom}）本身就会清掉尸体标记，这里负责回收跟踪。
     */
    public static void refresh(ServerLevel level) {
        if (GUARDED_OWNERS.isEmpty()) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (game == null) {
            return;
        }
        boolean anatmanAlive = isPartnerAlive(level, game, ModRoles.ANATMAN);
        boolean asatyaAlive = isPartnerAlive(level, game, ModRoles.ASATYA);
        boolean running = game.isRunning();

        GUARDED_OWNERS.removeIf(ownerUuid -> {
            PlayerBodyEntity body = findBody(level, ownerUuid);
            if (body == null) {
                return true;
            }
            boolean ownerWasAnatman = ModRoles.ANATMAN.identifier()
                    .equals(PlayerBodyEntityComponent.KEY.get(body).playerRole);
            boolean partnerAlive = ownerWasAnatman ? asatyaAlive : anatmanAlive;
            if (running && partnerAlive) {
                return false;
            }
            // 若其它复活逻辑（除颤器 / 循声者）也等着这具尸体，保留标记不解除
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null && DefibrillatorComponent.KEY.get(owner).isDead) {
                return true;
            }
            body.setNonInteractable(false);
            return true;
        });
    }

    /**
     * 交互入口的通用拦截：尸体处于待复活状态时提示并拒绝交互。
     *
     * @return true 表示本次交互应当被取消
     */
    public static boolean denyInteraction(PlayerBodyEntity body, @Nullable Player actor) {
        if (body == null || !body.isNonInteractable()) {
            return false;
        }
        if (actor instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.corpse.pending_revive")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        return true;
    }

    /** 返回该玩家在无我/无妄组合中的搭档职业；不是组合成员时返回 null。 */
    private static SRERole partnerRoleOf(Player victim, SREGameWorldComponent game) {
        if (game.isRole(victim, ModRoles.ANATMAN)) {
            return ModRoles.ASATYA;
        }
        if (game.isRole(victim, ModRoles.ASATYA)) {
            return ModRoles.ANATMAN;
        }
        return null;
    }

    private static boolean isPartnerAlive(ServerLevel level, SREGameWorldComponent game, SRERole partnerRole) {
        for (Player p : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p) && game.isRole(p, partnerRole)) {
                return true;
            }
        }
        return false;
    }

    private static PlayerBodyEntity findBody(ServerLevel level, UUID ownerUuid) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof PlayerBodyEntity body && ownerUuid.equals(body.getPlayerUuid())) {
                return body;
            }
        }
        return null;
    }
}

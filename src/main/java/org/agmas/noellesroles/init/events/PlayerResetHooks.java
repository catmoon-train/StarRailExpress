/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.component.TemporaryEffectPlayerComponent;
import org.agmas.noellesroles.content.entity.MudTrapEntity;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.packet.PlayerResetS2CPacket;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.ArrayList;
import java.util.List;

/** 原玩家重置注入，改由 GameUtilityEvents 在相同的入口和末尾调用。 */
final class PlayerResetHooks {
    private PlayerResetHooks() {
    }

    static void afterReset(ServerPlayer player) {
        clearAllComponents(player);
        if (ModComponents.DEFIBRILLATOR.get(player) != null) {
            ModComponents.DEFIBRILLATOR.get(player).clear();
        }
        player.getInventory().offhand.set(0, ItemStack.EMPTY);
        ServerPlayNetworking.send(player, new PlayerResetS2CPacket());
        SREItemUtils.clearItem(player, s -> true);
    }

    static void beforeInitialize(ServerLevel world) {
        world.players().forEach(player -> ServerPlayNetworking.send(player, new PlayerResetS2CPacket()));
    }

    private static void clearAllComponents(ServerPlayer player) {
        RoleUtils.removeAllPlayerAttributes(player);
        RoleUtils.removeAllEffects(player);
        player.setLastHurtByMob(null);
        player.setLastHurtMob(null);
        player.setLastHurtByPlayer(null);
        TemporaryEffectPlayerComponent.KEY.get(player).init();
        SplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).sync();
        PlayerVolumeComponent.KEY.get(player).clear();
        InControlCCA.KEY.get(player).clear();
        DeathPenaltyComponent deathPenalty = ModComponents.DEATH_PENALTY.get(player);
        deathPenalty.clear();
        SREAbilityPlayerComponent abilityComp = ModComponents.ABILITY.get(player);
        abilityComp.clear();
        // RoleData 不需要重置。
        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
        puppeteerComp.clear();
        clearMudTraps(player);
        clearTripwireTraps(player);
    }

    private static void clearMudTraps(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (world == null) return;
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof MudTrapEntity mud && mud.getOwnerUuid().isPresent()
                    && mud.getOwnerUuid().get().equals(player.getUUID())) {
                toRemove.add(mud);
            }
        }
        for (Entity entity : toRemove) entity.discard();
    }

    private static void clearTripwireTraps(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (world == null) return;
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof TripwireTrapEntity trap && trap.getOwnerUuid().isPresent()
                    && trap.getOwnerUuid().get().equals(player.getUUID())) {
                toRemove.add(trap);
            }
        }
        for (Entity entity : toRemove) entity.discard();
    }
}

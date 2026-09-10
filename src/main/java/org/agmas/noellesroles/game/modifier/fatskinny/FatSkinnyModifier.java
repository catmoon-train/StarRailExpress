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

package org.agmas.noellesroles.game.modifier.fatskinny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

/**
 * 胖子推动周围玩家；瘦子被周围玩家挤压移动。
 *
 * <p>不改方块碰撞箱，避免胖子卡门。只在玩家之间施加水平速度。
 */
public final class FatSkinnyModifier {
    private FatSkinnyModifier() {
    }

    public static void init() {
        ModifierAssigned.EVENT.register((player, modifier) -> {
            WorldModifierComponent cca = WorldModifierComponent.KEY.get(player.level());
            if (modifier.equals(NRModifiers.FAT) && cca.isModifier(player, NRModifiers.SKINNY)) {
                RoleUtils.removeModifier(player, NRModifiers.SKINNY);
            } else if (modifier.equals(NRModifiers.SKINNY) && cca.isModifier(player, NRModifiers.FAT)) {
                RoleUtils.removeModifier(player, NRModifiers.FAT);
            }
        });
    }

    public static void serverTickFat(ServerPlayer player) {
        if (!canAffectPlayers(player)) {
            return;
        }
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        double radius = FatSkinnyPushLogic.FAT_PUSH_RADIUS;
        AABB search = player.getBoundingBox().inflate(radius, 0.25D, radius);
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, search,
                target -> target != player && canBeMoved(target))) {
            FatSkinnyPushLogic.Vec2 push = FatSkinnyPushLogic.fatPush(
                    player.getX(), player.getZ(), other.getX(), other.getZ(),
                    modifiers.isModifier(other, NRModifiers.SKINNY),
                    modifiers.isModifier(other, NRModifiers.FAT));
            applyHorizontal(other, push);
        }
    }

    public static void serverTickSkinny(ServerPlayer player) {
        if (!canAffectPlayers(player) || !canBeMoved(player)) {
            return;
        }
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        double radius = FatSkinnyPushLogic.SKINNY_SQUEEZE_RADIUS;
        AABB search = player.getBoundingBox().inflate(radius, 0.25D, radius);
        double accX = 0.0D;
        double accZ = 0.0D;
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, search,
                neighbor -> neighbor != player && canSqueezeSkinny(neighbor, modifiers))) {
            FatSkinnyPushLogic.Vec2 squeeze = FatSkinnyPushLogic.skinnySqueeze(
                    player.getX(), player.getZ(), other.getX(), other.getZ());
            accX += squeeze.x();
            accZ += squeeze.z();
        }
        applyHorizontal(player, new FatSkinnyPushLogic.Vec2(accX, accZ));
    }

    private static boolean canAffectPlayers(ServerPlayer player) {
        if (SRE.isLobby) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return false;
        }
        return GameUtils.isPlayerAliveAndSurvival(player) && !isFrozen(player);
    }

    private static boolean canBeMoved(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player) || isFrozen(player)) {
            return false;
        }
        if (player.isPassenger()) {
            return false;
        }
        if (player.hasEffect(ModEffects.NO_COLLIDE)) {
            return false;
        }
        return !WorldModifierComponent.KEY.get(player.level()).isModifier(player, SEModifiers.FEATHER);
    }

    /**
     * 瘦子只被「会占空间」的玩家挤压：胖子已在自己的 tick 里推开瘦子，瘦子之间互不挤压。
     */
    private static boolean canSqueezeSkinny(ServerPlayer other, WorldModifierComponent modifiers) {
        if (!canBeMoved(other)) {
            return false;
        }
        if (modifiers.isModifier(other, NRModifiers.FAT)) {
            return false;
        }
        return !modifiers.isModifier(other, NRModifiers.SKINNY);
    }

    private static boolean isFrozen(Player player) {
        return player.hasEffect(ModEffects.TIME_STOP) || player.hasEffect(ModEffects.TAROT_ASSEMBLY);
    }

    private static void applyHorizontal(ServerPlayer player, FatSkinnyPushLogic.Vec2 push) {
        if (push.isZero()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x + push.x(), motion.y, motion.z + push.z());
        player.hurtMarked = true;
    }
}

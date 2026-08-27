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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.packet.PlayerResetS2CPacket;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家重置 Mixin
 * 
 * 在游戏结束时（GameUtils.resetPlayer 被调用）清除所有自定义组件的状态
 * 这确保了下一局游戏开始时玩家不会有残留的状态
 */
@Mixin(GameUtils.class)
public abstract class PlayerResetMixin {

    /**
     * 在 resetPlayer 方法尾部注入，清除所有自定义组件状态
     */
    @Inject(method = "resetPlayer", at = @At("TAIL"))
    private static void clearAllComponentsOnReset(ServerPlayer player, CallbackInfo ci) {
        // 清除跟踪者组件状态

        clearAllComponents(player);
        if (ModComponents.DEFIBRILLATOR.get(player) != null) {
            ModComponents.DEFIBRILLATOR.get(player).clear();
        }
        player.getInventory().offhand.set(0, ItemStack.EMPTY);
        ServerPlayNetworking.send(player, new PlayerResetS2CPacket());
        SREItemUtils.clearItem(player, (s) -> true);
    }

    /**
     * 在 initializeGame 方法头部注入，清除自定义笔记
     */
    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void clearAllComponentsOnReset(ServerLevel serverWorld, CallbackInfo ci) {
        // 清除客户端自定义笔记状态

        serverWorld.players().forEach((pl) -> {
            // clearAllComponents(pl);
            ServerPlayNetworking.send(pl, new PlayerResetS2CPacket());
        });
    }

    private static void clearAllComponents(ServerPlayer player) {
        RoleUtils.removeAllPlayerAttributes(player);
        RoleUtils. removeAllEffects(player);
        player.setLastHurtByMob(null);
        player.setLastHurtByPlayer(null);
        TemporaryEffectPlayerComponent.KEY.get(player).init();
        SplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).sync();
        (PlayerVolumeComponent.KEY.get(player)).clear();

        InControlCCA inControlCCA = InControlCCA.KEY.get(player);
        inControlCCA.clear();
        // 清除惩罚组件状态
        DeathPenaltyComponent deathPenalty = ModComponents.DEATH_PENALTY.get(player);
        deathPenalty.clear();

        // 清除其他自定义组件状态
        SREAbilityPlayerComponent abilityComp = ModComponents.ABILITY.get(player);
        abilityComp.clear();

        // Noellesroles.LOGGER.info("resetPlayer");

        AyayayaPlayerComponent postmanComp = ModComponents.AYAYAYA.get(player);
        postmanComp.clear();

        // 清除傀儡师组件状态
        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
        puppeteerComp.clear();

        // 清除蛋糕师组件状态（移除烟熏炉和已放置的蛋糕，防止残留到下一局）
        CakeMakerComponent.KEY.get(player).clear();
        // 删除modifier
        // WorldModifierComponent worldModifierComponent =
        // WorldModifierComponent.KEY.get(player.level());
        // worldModifierComponent.modifiers.clear();
        // worldModifierComponent.sync();
        // 清除该玩家放置的所有泥沼陷阱实体
        clearMudTraps(player);
        // 清除该玩家放置的所有绊线陷阱实体
        clearTripwireTraps(player);
    }

    /**
     * 清除指定玩家放置的所有泥沼陷阱实体
     */
    private static void clearMudTraps(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (world == null)
            return;

        // 收集需要移除的实体（避免在遍历时修改集合）
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof MudTrapEntity mud) {
                // 检查是否是该玩家放置的
                if (mud.getOwnerUuid().isPresent() &&
                        mud.getOwnerUuid().get().equals(player.getUUID())) {
                    toRemove.add(mud);
                }
            }
        }

        // 移除所有标记的实体
        for (Entity entity : toRemove) {
            entity.discard();
        }
    }

    /**
     * 清除指定玩家放置的所有绊索陷阱实体
     */
    private static void clearTripwireTraps(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (world == null)
            return;

        // 收集需要移除的实体（避免在遍历时修改集合）
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof TripwireTrapEntity trap) {
                // 检查是否是该玩家放置的
                if (trap.getOwnerUuid().isPresent() &&
                        trap.getOwnerUuid().get().equals(player.getUUID())) {
                    toRemove.add(trap);
                }
            }
        }

        // 移除所有标记的实体
        for (Entity entity : toRemove) {
            entity.discard();
        }
    }
}
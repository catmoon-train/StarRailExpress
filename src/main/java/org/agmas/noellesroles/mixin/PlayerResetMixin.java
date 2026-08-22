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

import org.agmas.noellesroles.role_data.neutral.AmonRoleData;

import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.agmas.noellesroles.role_data.innocence.GhostRoleData;
import io.wifi.starrailexpress.api.data.RoleData;
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
import org.agmas.noellesroles.role_data.innocence.AthleteRoleData;
import org.agmas.noellesroles.role_data.innocence.AvengerRoleData;
import org.agmas.noellesroles.role_data.innocence.AwesomeRoleData;
import org.agmas.noellesroles.role_data.innocence.AyayayaRoleData;
import org.agmas.noellesroles.role_data.innocence.BoxerRoleData;
import org.agmas.noellesroles.role_data.innocence.BroadcasterRoleData;
import org.agmas.noellesroles.role_data.innocence.CakeMakerRoleData;
import org.agmas.noellesroles.role_data.innocence.AgentRoleData;
import org.agmas.noellesroles.role_data.innocence.FortunetellerRoleData;
import org.agmas.noellesroles.role_data.innocence.RecallerRoleData;
import org.agmas.noellesroles.role_data.innocence.VoodooRoleData;
import org.agmas.noellesroles.role_data.killer.BloodFeudistRoleData;
import org.agmas.noellesroles.role_data.killer.ConspiratorRoleData;
import org.agmas.noellesroles.role_data.killer.ExecutionerRoleData;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;
import org.agmas.noellesroles.role_data.killer.SkincrawlerRoleData;
import org.agmas.noellesroles.role_data.killer.StalkerRoleData;
import org.agmas.noellesroles.role_data.killer.TrapperRoleData;
import org.agmas.noellesroles.role_data.neutral.AdmirerRoleData;
import org.agmas.noellesroles.role_data.neutral.MonokumaRoleData;
import org.agmas.noellesroles.role_data.neutral.PuppeteerRoleData;
import org.agmas.noellesroles.role_data.neutral.RecorderRoleData;
import org.agmas.noellesroles.role_data.neutral.SlipperyGhostRoleData;
import org.agmas.noellesroles.role_data.neutral.VultureRoleData;
import org.agmas.noellesroles.role_data.neutral.WayfarerRoleData;
import org.agmas.noellesroles.packet.PlayerResetS2CPacket;
import org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket;
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
        player.setLastHurtMob(null);
        TemporaryEffectPlayerComponent.KEY.get(player).init();
        BloodFeudistRoleData bloodFeudist = RoleData.getNullable(BloodFeudistRoleData.class, player);
        if (RoleData.isAttached(bloodFeudist)) bloodFeudist.clear();
        SplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).sync();
        MonokumaRoleData monokuma = RoleData.getNullable(MonokumaRoleData.class, player);
        if (RoleData.isAttached(monokuma)) monokuma.clear();
        (PlayerVolumeComponent.KEY.get(player)).clear();
        WayfarerRoleData wayfarer = RoleData.getNullable(WayfarerRoleData.class, player);
        if (RoleData.isAttached(wayfarer)) wayfarer.clear();

        MorphlingRoleData morphling = RoleData.getNullable(MorphlingRoleData.class, player);
        if (RoleData.isAttached(morphling)) morphling.init();
        VoodooRoleData voodoo = RoleData.getNullable(VoodooRoleData.class, player);
        if (RoleData.isAttached(voodoo)) voodoo.init();
        RecallerRoleData recaller = RoleData.getNullable(RecallerRoleData.class, player);
        if (RoleData.isAttached(recaller)) recaller.init();
        VultureRoleData vulture = RoleData.getNullable(VultureRoleData.class, player);
        if (RoleData.isAttached(vulture)) vulture.init();
        ExecutionerRoleData executioner = RoleData.getNullable(ExecutionerRoleData.class, player);
        if (RoleData.isAttached(executioner)) executioner.init();

        FortunetellerRoleData fortuneteller = RoleData.getNullable(FortunetellerRoleData.class, player);
        if (RoleData.isAttached(fortuneteller)) fortuneteller.init();

        AwesomeRoleData awesomeComp = RoleData.getNullable(AwesomeRoleData.class, player);
        if (RoleData.isAttached(awesomeComp)) awesomeComp.init();

        StalkerRoleData stalkerComp = RoleData.getNullable(StalkerRoleData.class, player);
        if (RoleData.isAttached(stalkerComp)) stalkerComp.clearAll();
        InControlCCA inControlCCA = InControlCCA.KEY.get(player);
        inControlCCA.clear();
        MagicianRoleData magician = RoleData.getNullable(MagicianRoleData.class, player);
        if (RoleData.isAttached(magician)) magician.clear();
        ManipulatorRoleData manipulatorComp = RoleData.getNullable(ManipulatorRoleData.class, player);
        if (RoleData.isAttached(manipulatorComp)) manipulatorComp.clear();
        // 清除惩罚组件状态
        DeathPenaltyComponent deathPenalty = ModComponents.DEATH_PENALTY.get(player);
        deathPenalty.clear();

        // 清除慕恋者组件状态
        AdmirerRoleData admirerComp = RoleData.getNullable(AdmirerRoleData.class, player);
        if (RoleData.isAttached(admirerComp)) admirerComp.clear();

        // 清除其他自定义组件状态
        SREAbilityPlayerComponent abilityComp = ModComponents.ABILITY.get(player);
        abilityComp.clear();

        AvengerRoleData avengerComp = RoleData.getNullable(AvengerRoleData.class, player);
        if (RoleData.isAttached(avengerComp)) avengerComp.clear();

        ConspiratorRoleData conspiratorComp = RoleData.getNullable(ConspiratorRoleData.class, player);
        if (RoleData.isAttached(conspiratorComp)) conspiratorComp.clear();

        InsaneKillerRoleData insaneKillerComp = RoleData.getNullable(InsaneKillerRoleData.class, player);
        if (RoleData.isAttached(insaneKillerComp)) insaneKillerComp.clear();

        SlipperyGhostRoleData slipperyGhostComp = RoleData.getNullable(SlipperyGhostRoleData.class, player);
        if (RoleData.isAttached(slipperyGhostComp)) slipperyGhostComp.clear();

        BroadcasterRoleData broadcasterComp = RoleData.getNullable(BroadcasterRoleData.class, player);
        if (RoleData.isAttached(broadcasterComp)) broadcasterComp.clear();

        AyayayaRoleData postmanComp = RoleData.getNullable(AyayayaRoleData.class, player);
        if (RoleData.isAttached(postmanComp)) postmanComp.clear();

        AgentRoleData detectiveComp = RoleData.getNullable(AgentRoleData.class, player);
        if (RoleData.isAttached(detectiveComp)) detectiveComp.clear();

        BoxerRoleData boxerComp = RoleData.getNullable(BoxerRoleData.class, player);
        if (RoleData.isAttached(boxerComp)) boxerComp.clear();

        AthleteRoleData athleteComp = RoleData.getNullable(AthleteRoleData.class, player);
        if (RoleData.isAttached(athleteComp)) athleteComp.clear();

        TrapperRoleData trapperComp = RoleData.getNullable(TrapperRoleData.class, player);
        if (RoleData.isAttached(trapperComp)) trapperComp.clearAll();

        SkincrawlerRoleData skincrawlerComp = RoleData.getNullable(org.agmas.noellesroles.role_data.killer.SkincrawlerRoleData.class, player);
        if (RoleData.isAttached(skincrawlerComp)) {
            if (skincrawlerComp.stolenSkin != null && player.getServer() != null) {
                for (ServerPlayer sp : player.getServer().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(sp, new SkincrawlerSkinS2CPacket(player.getUUID(), null));
                }
            }
            skincrawlerComp.clear();
        }

        AmonRoleData amon = RoleData.getNullable(org.agmas.noellesroles.role_data.neutral.AmonRoleData.class, player);
        if (RoleData.isAttached(amon)) amon.clear();

        PuppeteerRoleData puppeteerComp = RoleData.getNullable(PuppeteerRoleData.class, player);
        if (RoleData.isAttached(puppeteerComp)) puppeteerComp.clear();

        RecorderRoleData recorderComp = RoleData.getNullable(RecorderRoleData.class, player);
        if (RoleData.isAttached(recorderComp)) recorderComp.clear();

        CakeMakerRoleData cakeMaker = RoleData.getNullable(CakeMakerRoleData.class, player);
        if (RoleData.isAttached(cakeMaker)) cakeMaker.clear();
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
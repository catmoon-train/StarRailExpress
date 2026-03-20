package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TMMItemUtils;

import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.entity.CalamityMarkEntity;
import org.agmas.noellesroles.packet.PlayerResetS2CPacket;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.InControlCCA;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.roles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.roles.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

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
        TMMItemUtils.clearItem(player, (s) -> true);
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
        RoleUtils.RemoveAllPlayerAttributes(player);
        RoleUtils.RemoveAllEffects(player);
        player.setLastHurtMob(null);
        TemporaryEffectPlayerComponent.KEY.get(player).init();
        BloodFeudistPlayerComponent.KEY.get(player).clear();
        SplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).clear();
        SkinSplitPersonalityComponent.KEY.get(player).sync();
        (PlayerVolumeComponent.KEY.get(player)).clear();
        (WayfarerPlayerComponent.KEY.get(player)).clear();
        (HoanMeirinPlayerComponent.KEY.get(player)).clear();

        ((MorphlingPlayerComponent) MorphlingPlayerComponent.KEY.get(player)).init();
        ((VoodooPlayerComponent) VoodooPlayerComponent.KEY.get(player)).init();
        (RecallerPlayerComponent.KEY.get(player)).init();
        (VulturePlayerComponent.KEY.get(player)).init();
        (ExecutionerPlayerComponent.KEY.get(player)).init();

        FortunetellerPlayerComponent.KEY.get(player).init();

        AwesomePlayerComponent awesomeComp = ModComponents.AWESOME.get(player);
        awesomeComp.init();

        StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(player);
        stalkerComp.clearAll();
        InControlCCA inControlCCA = InControlCCA.KEY.get(player);
        inControlCCA.clear();
        ModComponents.MAGICIAN.get(player).clear();
        ManipulatorPlayerComponent manipulatorComp = ManipulatorPlayerComponent.KEY.get(player);
        manipulatorComp.clear();
        // 清除惩罚组件状态
        DeathPenaltyComponent deathPenalty = ModComponents.DEATH_PENALTY.get(player);
        deathPenalty.clear();

        // 清除慕恋者组件状态
        AdmirerPlayerComponent admirerComp = ModComponents.ADMIRER.get(player);
        admirerComp.clear();

        // 清除其他自定义组件状态
        SREAbilityPlayerComponent abilityComp = ModComponents.ABILITY.get(player);
        abilityComp.clear();

        AvengerPlayerComponent avengerComp = ModComponents.AVENGER.get(player);
        avengerComp.clear();

        ConspiratorPlayerComponent conspiratorComp = ModComponents.CONSPIRATOR.get(player);
        conspiratorComp.clear();

        // Noellesroles.LOGGER.info("resetPlayer");
        InsaneKillerPlayerComponent insaneKillerComp = ModComponents.INSANE_KILLER.get(player);
        insaneKillerComp.clear();

        SlipperyGhostPlayerComponent slipperyGhostComp = ModComponents.SLIPPERY_GHOST.get(player);
        slipperyGhostComp.clear();

        BroadcasterPlayerComponent broadcasterComp = ModComponents.BROADCASTER.get(player);
        broadcasterComp.clear();

        PostmanPlayerComponent postmanComp = ModComponents.POSTMAN.get(player);
        postmanComp.clear();

        DetectivePlayerComponent detectiveComp = ModComponents.DETECTIVE.get(player);
        detectiveComp.clear();

        BoxerPlayerComponent boxerComp = ModComponents.BOXER.get(player);
        boxerComp.clear();

        AthletePlayerComponent athleteComp = ModComponents.ATHLETE.get(player);
        athleteComp.clear();

        // 清除设陷者组件状态
        TrapperPlayerComponent trapperComp = ModComponents.TRAPPER.get(player);
        trapperComp.clearAll();

        // 清除傀儡师组件状态
        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
        puppeteerComp.clear();

        // 清除记录员组件状态
        RecorderPlayerComponent recorderComp = ModComponents.RECORDER.get(player);
        recorderComp.clear();
        // 删除modifier
        // WorldModifierComponent worldModifierComponent =
        // WorldModifierComponent.KEY.get(player.level());
        // worldModifierComponent.modifiers.clear();
        // worldModifierComponent.sync();
        // 清除该玩家放置的所有灾厄印记实体
        clearCalamityMarks(player);
    }

    /**
     * 清除指定玩家放置的所有灾厄印记实体
     */
    private static void clearCalamityMarks(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (world == null)
            return;

        // 收集需要移除的实体（避免在遍历时修改集合）
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof CalamityMarkEntity mark) {
                // 检查是否是该玩家放置的
                if (mark.getOwnerUuid().isPresent() &&
                        mark.getOwnerUuid().get().equals(player.getUUID())) {
                    toRemove.add(mark);
                }
            }
        }

        // 移除所有标记的实体
        for (Entity entity : toRemove) {
            entity.discard();
        }
    }
}
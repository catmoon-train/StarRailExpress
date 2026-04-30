package pro.fazeclan.river.stupid_express.role.amnesiac;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;

import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class RoleSelectionHandler {

    private static void clearAllKnives(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(TMMItems.KNIFE)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    public static void init() {
        UseEntityCallback.EVENT.register(((player, level, interactionHand, entity, entityHitResult) -> {
            if (!(player instanceof ServerPlayer interacting)) {
                return InteractionResult.PASS;
            }
            if (!interacting.gameMode.isSurvival()) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, SERoles.AMNESIAC)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity victim)) {
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available"), true);
                return InteractionResult.PASS;
            }
            var roleRes = PlayerBodyEntityComponent.KEY.get(victim).playerRole;
            if (roleRes == null) {
                return InteractionResult.PASS;
            }
            SRERole role = TMMRoles.ROLES.get(roleRes);
            if (role == null) {
                return InteractionResult.PASS;
            }
            if (!role.canBeRandomed()) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_not_support")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (role.identifier().equals(ModRoles.MA_CHEN_XU.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_not_support")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (role.identifier().equals(TMMRoles.LOOSE_END.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_not_support")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (role.identifier().equals(SERoles.INITIATE.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_not_support")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (role.identifier().equals(SERoles.AMNESIAC.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_not_support")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            // 清除物品栏中的所有刀
            clearAllKnives(interacting);

            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(interacting);
            StupidRoleUtils.changeRole(interacting, role);

            // 播放全场音效
            interacting.level().playSound(null, interacting.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 2.0F, 1.0F);

            playerShopComponent.setBalance(200);
            StupidRoleUtils.sendWelcomeAnnouncement(interacting);

            return InteractionResult.CONSUME;
        }));
    }

}
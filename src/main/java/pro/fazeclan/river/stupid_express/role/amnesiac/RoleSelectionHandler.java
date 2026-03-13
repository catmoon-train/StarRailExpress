package pro.fazeclan.river.stupid_express.role.amnesiac;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
            StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, SERoles.AMNESIAC)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity victim)) {
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                 player.displayClientMessage(Component.translatable("message.stupid_express.generic.skill_not_available"), true);
                return InteractionResult.PASS;
            }
            Role role = gameWorldComponent.getRole(victim.getPlayerUuid());
            if (role.identifier().equals(SERoles.INITIATE.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_initiate")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (role.identifier().equals(SERoles.AMNESIAC.identifier())) {
                player.displayClientMessage(
                        Component.translatable("msg.amnesiac.change_role.failed_same").withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            // 清除物品栏中的所有刀
            clearAllKnives(interacting);

            StarPlayerShopComponent playerShopComponent = StarPlayerShopComponent.KEY.get(interacting);
            StupidRoleUtils.changeRole(interacting, role);

            playerShopComponent.setBalance(200);
            StupidRoleUtils.sendWelcomeAnnouncement(interacting);

            return InteractionResult.CONSUME;
        }));
    }

}
package pro.fazeclan.river.stupid_express.role.necromancer;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.component.StarAbilityPlayerComponent;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static io.wifi.starrailexpress.compat.TrainVoicePlugin.*;

public class RevivalSelectionHandler {
    public static void removeVoice(@NotNull UUID player) {
        if (SERVER_API == null) {
            return;
        }
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            connection.setGroup(null);
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
            if (!gameWorldComponent.isRole(player, SERoles.NECROMANCER)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available").withStyle(ChatFormatting.RED), true);
                return InteractionResult.PASS;
            }
            var serverLevel = (ServerLevel) level;

            // check if the selected body can be revived
            var revived = (ServerPlayer) serverLevel.getPlayerByUUID(body.getPlayerUuid());
            if (revived == null) {
                return InteractionResult.PASS;
            }
            var nc = NecromancerComponent.KEY.get(serverLevel);
            if (nc.getAvailableRevives() < 1) {
                return InteractionResult.PASS;
            }

            // activate cooldown
            StarAbilityPlayerComponent cooldown = StarAbilityPlayerComponent.KEY.get(player);
            if (cooldown.hasCooldown()) {
                return InteractionResult.PASS;
            }
            cooldown.setCooldown(3 * 60 * 20);
            nc.decreaseAvailableRevives();
            nc.sync();

            // get random killer role
            var roles = new ArrayList<Role>();
            roles.add(TMMRoles.KILLER);
            Collections.shuffle(roles);

            // revive player and give them the role
            var selectedRole = roles.getFirst();

            serverLevel.players().forEach(
                    a -> {
                        a.playNotifySound(SoundEvents.TOTEM_USE, revived.getSoundSource(), 1.2f, 1.5f);
                        a.displayClientMessage(Component.translatable("hud.stupid_express.necromancer.revived_player")
                                .append(Harpymodloader.getRoleName(selectedRole)), true);
                    });
            revived.getInventory().clearContent();
            revived.teleportTo(body.getX(), body.getY(), body.getZ());
            revived.setGameMode(GameType.ADVENTURE);
            removeVoice(revived.getUUID());
            body.remove(Entity.RemovalReason.DISCARDED); // like it never existed

            StupidRoleUtils.changeRole(revived, selectedRole);
            SRE.REPLAY_MANAGER.recordPlayerRevival(revived.getUUID(), selectedRole);
            StarPlayerShopComponent playerShopComponent = StarPlayerShopComponent.KEY.get(revived);
            playerShopComponent.setBalance(200);

            StupidRoleUtils.sendWelcomeAnnouncement(revived);

            return InteractionResult.CONSUME;
        }));
    }

}

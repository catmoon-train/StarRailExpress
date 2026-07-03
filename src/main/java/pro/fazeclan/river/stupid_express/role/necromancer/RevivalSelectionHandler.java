package pro.fazeclan.river.stupid_express.role.necromancer;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.funny.SREEvilWarGameMode;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static io.wifi.starrailexpress.compat.TrainVoicePlugin.SERVER_API;

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
            if (!GameUtils.isPlayerAliveAndSurvival(interacting)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, BounsRoles.CAT_NECROMANCER)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 检查是否是葬仪伪造的尸体，不能复活伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.necromancer.cannot_revive_fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            var serverLevel = (ServerLevel) level;

            // check if the selected body can be revived
            var revived = (ServerPlayer) serverLevel.getPlayerByUUID(body.getPlayerUuid());
            if (revived == null) {
                return InteractionResult.PASS;
            }

            if (!revived.isSpectator()) {
                return InteractionResult.PASS;
            }
            var nc = NecromancerComponent.KEY.get(serverLevel);
            if (nc.getAvailableRevives() < 1) {
                return InteractionResult.PASS;
            }

            // activate cooldown
            SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(player);
            if (cooldown.hasCooldown()) {
                return InteractionResult.PASS;
            }
            // 1分半钟cd
            cooldown.setCooldown(120 * 20);
            nc.decreaseAvailableRevives();
            nc.sync();

            // get random killer role
            var roles = new ArrayList<SRERole>();
            roles.add(BounsRoles.CAT_KILLER);
            Collections.shuffle(roles);

            // revive player and give them the role
            var selectedRole = roles.getFirst();

            serverLevel.players().forEach(
                    a -> {
                        a.playNotifySound(SoundEvents.TOTEM_USE, revived.getSoundSource(), 1.2f, 1.5f);
                        a.displayClientMessage(Component.translatable("hud.stupid_express.necromancer.revived_player",
                                RoleUtils.getRoleOrModifierNameWithColor(BounsRoles.CAT_NECROMANCER),
                                RoleUtils.getRoleOrModifierNameWithColor(selectedRole))
                                .withStyle(ChatFormatting.DARK_RED), true);
                    });
            revived.getInventory().clearContent();
            revived.teleportTo(body.getX(), body.getY(), body.getZ());
            revived.setGameMode(GameType.ADVENTURE);
            removeVoice(revived.getUUID());
            body.remove(Entity.RemovalReason.DISCARDED); // like it never existed

            StupidRoleUtils.changeRole(revived, selectedRole);
            SRE.REPLAY_MANAGER.recordPlayerRevival(revived.getUUID(), selectedRole);
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(revived);
            playerShopComponent.setBalance(200);

            StupidRoleUtils.sendWelcomeAnnouncement(revived);
            var psychoCCA = SREPlayerPsychoComponent.KEY.get(revived);
            psychoCCA.startPsycho();
            // 使用默认时长 * 1.1
            psychoCCA.setPsychoTicks((int) ((double) GameConstants.getPsychoTimer() * 1.1));

            return InteractionResult.CONSUME;
        }));

        UseEntityCallback.EVENT.register(((player, level, interactionHand, entity, entityHitResult) -> {

            if (!(player instanceof ServerPlayer interacting)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(interacting)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, SERoles.NECROMANCER)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 检查是否是葬仪伪造的尸体，不能复活伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.necromancer.cannot_revive_fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            var serverLevel = (ServerLevel) level;

            // check if the selected body can be revived
            var revived = (ServerPlayer) serverLevel.getPlayerByUUID(body.getPlayerUuid());
            if (revived == null) {
                return InteractionResult.PASS;
            }

            if (!revived.isSpectator()) {
                return InteractionResult.PASS;
            }
            var nc = NecromancerComponent.KEY.get(serverLevel);
            if (nc.getAvailableRevives() < 1) {
                return InteractionResult.PASS;
            }

            // activate cooldown
            SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(player);
            if (cooldown.hasCooldown()) {
                return InteractionResult.PASS;
            }
            cooldown.setCooldown(3 * 60 * 20);
            nc.decreaseAvailableRevives();
            nc.sync();

            // get random killer role
            var roles = new ArrayList<SRERole>();
            // 邪恶战争模式可以复活随机杀手
            if (gameWorldComponent.gameMode instanceof SREEvilWarGameMode evilWarGameMode) {
                RoleAssignmentPool evilWarKillerPool = evilWarGameMode.createEvilWarRolePool();
                SRERole role = evilWarKillerPool.selectRole();
                if (role != null)
                    roles.add(role);
            }
            if (roles.isEmpty())
                roles.add(TMMRoles.KILLER);
            Collections.shuffle(roles);

            // revive player and give them the role
            var selectedRole = roles.getFirst();

            serverLevel.players().forEach(
                    a -> {
                        a.playNotifySound(SoundEvents.TOTEM_USE, revived.getSoundSource(), 1.2f, 1.5f);
                        a.displayClientMessage(Component.translatable("hud.stupid_express.necromancer.revived_player",
                                RoleUtils.getRoleOrModifierNameWithColor(SERoles.NECROMANCER),
                                RoleUtils.getRoleOrModifierNameWithColor(selectedRole))
                                .withStyle(ChatFormatting.DARK_RED), true);
                    });
            revived.getInventory().clearContent();
            revived.teleportTo(body.getX(), body.getY(), body.getZ());
            revived.setGameMode(GameType.ADVENTURE);
            removeVoice(revived.getUUID());
            body.remove(Entity.RemovalReason.DISCARDED); // like it never existed

            StupidRoleUtils.changeRole(revived, selectedRole);
            SRE.REPLAY_MANAGER.recordPlayerRevival(revived.getUUID(), selectedRole);
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(revived);
            playerShopComponent.setBalance(200);

            // 邪恶战争模式时复活会给予专属物品
            if (gameWorldComponent.gameMode instanceof SREEvilWarGameMode) {
                SREEvilWarGameMode.addEvilWarItemsByRole(revived, selectedRole);
            }

            StupidRoleUtils.sendWelcomeAnnouncement(revived);

            return InteractionResult.CONSUME;
        }));
    }

}

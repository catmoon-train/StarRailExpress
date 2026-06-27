package org.agmas.noellesroles.game.roles.killer.undead_lord;

import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 亡灵之主事件注册：
 * <ul>
 *   <li>右键尸体发动【亡者复苏】——将尸体转化为无意识亡灵（45 秒冷却，最多同时 3 个）。</li>
 *   <li>角色分配时初始化组件状态。</li>
 * </ul>
 */
public class UndeadLordHandler {

    public static void init() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role.equals(ModRoles.UNDEAD_LORD)) {
                UndeadLordPlayerComponent.KEY.maybeGet(player).ifPresent(UndeadLordPlayerComponent::init);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (!gameWorldComponent.isRole(serverPlayer, ModRoles.UNDEAD_LORD)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 不能复活葬仪伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            UndeadLordPlayerComponent comp = UndeadLordPlayerComponent.KEY.maybeGet(serverPlayer).orElse(null);
            if (comp == null) {
                return InteractionResult.PASS;
            }

            // 数量上限（基于开局人数动态计算，最多 4 个）
            if (!comp.canRaiseFromCorpse()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.max_reached",
                                comp.maxActiveUndead()).withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            // 冷却
            SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(serverPlayer);
            if (cooldown.hasCooldown()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.cooldown",
                                cooldown.getCooldown() / 20).withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            boolean ok = comp.spawnUndeadAt(serverLevel, body.position(), body.getPlayerUuid(),
                    org.agmas.noellesroles.content.entity.UndeadEntity.DEFAULT_LIFETIME);
            if (!ok) {
                return InteractionResult.PASS;
            }

            cooldown.setCooldown(NoellesRolesConfig.HANDLER.instance().undeadLordReviveCooldownSeconds * 20);
            body.remove(Entity.RemovalReason.DISCARDED);
            serverLevel.playSound(null, serverPlayer.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.HOSTILE, 1.0f, 0.6f);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.undead_lord.raised")
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    true);
            return InteractionResult.CONSUME;
        });
    }
}

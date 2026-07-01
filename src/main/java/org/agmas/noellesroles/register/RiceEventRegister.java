package org.agmas.noellesroles.register;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.dio.DIOPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * Rice's Role Rhapsody 事件注册，
 * 从 {@link org.agmas.noellesroles.RicesRoleRhapsody} 中按类别剥离归一化而来。
 *
 * <p>注册傀儡师尸体收集事件，使用 Fabric API 的 UseEntityCallback 代替 Mixin。
 */
public class RiceEventRegister {

    public static void registerPuppeteerBodyCollect() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // 只在服务端处理
            if (world.isClientSide())
                return net.minecraft.world.InteractionResult.PASS;

            // 检查玩家是否存活
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return net.minecraft.world.InteractionResult.PASS;

            // 检查玩家是否是傀儡师
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isRole(player, ModRoles.CANDLE_BEARER)) {
                ItemStack held = player.getItemInHand(hand);
                if (held.is(Items.CANDLE)) {
                    var candleBearer = org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent.KEY.get(player);
                    if (entity instanceof Player targetPlayer) {
                        if (candleBearer.candleLivingPlayer(targetPlayer)) {
                            return net.minecraft.world.InteractionResult.SUCCESS;
                        }
                        return net.minecraft.world.InteractionResult.PASS;
                    }
                    if (entity instanceof PlayerBodyEntity targetBody) {
                        if (candleBearer.candleCorpse(targetBody)) {
                            return net.minecraft.world.InteractionResult.SUCCESS;
                        }
                        return net.minecraft.world.InteractionResult.PASS;
                    }
                }
            }

            // DIO/傀儡师逻辑只处理尸体实体
            if (!(entity instanceof PlayerBodyEntity body))
                return net.minecraft.world.InteractionResult.PASS;

            if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body))
                return net.minecraft.world.InteractionResult.PASS;

            if (gameWorld.isRole(player, ModRoles.DIO)) {
                DIOPlayerComponent dioPlayerComponent = DIOPlayerComponent.KEY.get(player);
                boolean success = dioPlayerComponent.feedOnCorpse(body);
                if (success) {
                    dioPlayerComponent.sync();
                    if (dioPlayerComponent.isFinalCarnivalActive) {
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        dioPlayerComponent.extendTempLife();
                    }
                }
            }
            if (!gameWorld.isRole(player, ModRoles.PUPPETEER))
                return net.minecraft.world.InteractionResult.PASS;

            // 获取傀儡师组件
            PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);

            // 检查是否可以回收（阶段一且不在冷却中）
            if (!puppeteerComp.canCollectBody())
                return net.minecraft.world.InteractionResult.PASS;

            // 获取尸体对应的玩家UUID
            java.util.UUID bodyOwnerUuid = body.getPlayerUuid();

            // 获取游戏总人数
            int totalPlayers = 1;
            if (world instanceof net.minecraft.server.level.ServerLevel serverWorld) {
                totalPlayers = serverWorld.players().size();
            }

            // 回收尸体
            puppeteerComp.collectBody(bodyOwnerUuid, totalPlayers);

            // 让尸体消失
            body.discard();

            return net.minecraft.world.InteractionResult.SUCCESS;
        });
    }
}

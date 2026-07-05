package org.agmas.noellesroles.handler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.ShouldGiveKillerBalance;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.content.item.BowenBadgeItem;
import org.agmas.noellesroles.content.item.RopeItem;
import org.agmas.noellesroles.role.touhou.MountainRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class TouhouHandlers {
    public static void register() {
        registerSkills();
        registerEvents();
    }

    public static void registerEvents() {
        ShouldGiveKillerBalance.EVENT.register((victim, killer, deathReason) -> {
            if (RoleUtils.isPlayerTheJob(killer, THMiscRoles.KOMACHI))
                return TrueFalseResult.FALSE;
            return TrueFalseResult.PASS;
        });
        // 天子&小町
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            // 小町
            // 你杀死的玩家将给予其所花费金额的100%与其当前金币的50%给你。一次获取上限为500。
            if (killer != null && gameWorldComponent.isRole(killer, THMiscRoles.KOMACHI)) {
                var vshop = SREPlayerShopComponent.KEY.get(player);
                int buyCosts = vshop.getTotalCostAndClear();
                int totaladd = buyCosts + vshop.balance / 2;
                if (totaladd > 500)
                    totaladd = 500;
                SREPlayerShopComponent.KEY.get(killer).addToBalance(totaladd);
            }
            // 天子
            for (var p : player.level().players()) {

                if (p.getUUID() != player.getUUID() && (killer == null || p.getUUID() != killer.getUUID())) {
                    if (gameWorldComponent.isRole(p, THMiscRoles.KOMACHI)) {
                        // 每个玩家死后将给予其所花费金额的10%给你。一次获取上限为300。
                        var vshop = SREPlayerShopComponent.KEY.get(player);
                        int buyCosts = vshop.getTotalCostAndClear();

                        int totaladd = (int) ((float) buyCosts * 0.1);
                        if (totaladd > 300)
                            totaladd = 300;
                        SREPlayerShopComponent.KEY.get(p).addToBalance(totaladd);
                    } else if (gameWorldComponent.isRole(p, THMiscRoles.TENSHI)) {
                        if (p.getCooldowns().isOnCooldown(Items.BARRIER)) {
                            continue;
                        } else {
                            p.getCooldowns().addCooldown(Items.BARRIER, 30 * 20);
                            if (p instanceof ServerPlayer sp) {
                                SRENetworkMessageUtils.sendCODSubtitleToPlayerTop(sp,
                                        Component.translatable("message.tenshi.killer_killed.title")
                                                .withStyle(ChatFormatting.RED),
                                        Component.translatable("message.tenshi.killer_killed.subtitle", 30), 100);
                            }
                        }
                    }
                }
            }

        });
    }

    public static void registerSkills() {
        RoleSkill.register(THMiscRoles.KOMACHI_ID,
                RoleSkill.skill(SRE.id("komachi_pull"), "skill.noellesroles.komachi_pull", context -> {
                    Player player = context.player();
                    var target = RopeItem.findTargetedPlayerInView(player.level(), player, 20);
                    if (target == null) {
                        return false;
                    }
                    // 身前2格
                    RopeItem.pullPlayer(player, target, 2);
                    return true;
                }).cooldownSeconds(90).showOnHud(true).shifted(true).build(),
                RoleSkill.skill(SRE.id("komachi_rush"), "skill.noellesroles.komachi_rush", context -> {
                    Player player = context.player();
                    BowenBadgeItem.fowardAndKnockbackPlayerNearby(player.level(), player, 2.5f);
                    return true;
                }).cooldownSeconds(60).showOnHud(true).shifted(false).build());
        RoleSkill.register(MountainRoles.NITORI, RoleSkill.skill(SRE.id("nitori_exchange"),
                "skill.noellesroles.nitori_exchange",
                context -> {
                    if (context.target() == null) {
                        return false;
                    }

                    var target = context.player().level().getPlayerByUUID(context.target());
                    if (target == null) {
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.no_target"), true);
                        return false;
                    }
                    ItemStack it = context.player().getMainHandItem();
                    if (it == null || it.isEmpty()) {
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.noitem"), true);
                        return false;
                    }
                    var targetShop = SREPlayerShopComponent.KEY.get(target);
                    var selfShop = SREPlayerShopComponent.KEY.get(context.player());
                    if (targetShop.balance < 200) {

                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.nomoney"), true);
                        return false;
                    }

                    if (RoleUtils.insertStackInFreeSlot(target, it.copy())) {
                        targetShop.addToBalance(-200);
                        selfShop.addToBalance(200);
                        context.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.success", it.getDisplayName()), true);
                        return true;
                    }
                    return false;
                }).cooldownSeconds(30).build());
    }
}

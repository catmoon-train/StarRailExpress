package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.economy.MoneyFlow;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import java.util.ArrayList;
import java.util.List;

/** 法器效果与权威冷却；物品转交只改变施法者，不刷新技能或金币。 */
public final class GhostRelicService {
    private GhostRelicService() {}

    public static boolean use(ServerPlayer player, ItemStack stack, boolean breakRealm) {
        var game = SREGameWorldComponent.KEY.get(player.level());
        if (!SackGhostItems.eligibleRelicCarrier(player) || !SackGhostItems.validRound(player, stack)
                || !GameUtils.isPlayerAliveAndSurvival(player) || game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE
                || !game.isSkillAvailable || SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()
                || player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.USED_BANED)) return false;
        var state = SackGhostItems.relicState(player, stack);
        if (state == null) return false;
        long now = SackGhostItems.now(player.serverLevel());
        long ready = breakRealm ? state.breakReadyAt : state.wardReadyAt;
        if (ready > now) return fail(player, "message.noellesroles.sack.cooldown", (ready - now + 19) / 20);
        boolean used = breakRealm ? breakRealm(player) : ward(player);
        if (!used) return false;
        if (breakRealm) state.breakReadyAt = now + SackGhostRules.RELIC_BREAK_COOLDOWN;
        else state.wardReadyAt = now + SackGhostRules.RELIC_WARD_COOLDOWN;
        SackGhostItems.updateRelicTag(player, stack, state);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean ward(ServerPlayer player) {
        List<MaChenXuRoleData> ghosts = ghosts(player);
        boolean any = false;
        for (ServerPlayer target : player.serverLevel().players()) {
            if (player.distanceToSqr(target) > 100 || !GameUtils.isPlayerAliveAndSurvival(target)
                    || !SackGhostItems.eligibleRelicCarrier(target)) continue;
            var mood = SREPlayerMoodComponent.KEY.get(target);
            boolean useful = mood.getMood() < mood.getMoodMax() - 0.0001F
                    || target.hasEffect(ModEffects.GHOST_CONDEMNED) || target.hasEffect(ModEffects.OTHERWORLD_AURA)
                    || ghosts.stream().anyMatch(data -> data.isEnemy(target));
            if (!useful) continue;
            mood.addMood(0.2F);
            for (var data : ghosts) data.cleanse(target);
            SackGhostItems.grantWard(target);
            target.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65F, 0.8F);
            any = true;
        }
        if (!any) return fail(player, "message.noellesroles.relic.no_ward_target");
        player.displayClientMessage(Component.translatable("message.noellesroles.relic.ward_used"), true);
        return true;
    }

    private static List<MaChenXuRoleData> ghosts(ServerPlayer player) {
        var result = new ArrayList<MaChenXuRoleData>();
        for (ServerPlayer owner : player.serverLevel().players()) {
            var data = SackGhostItems.data(owner);
            if (data != null && data.isActiveMaChenXu() && GameUtils.isPlayerAliveAndSurvival(owner)) result.add(data);
        }
        return result;
    }

    private static boolean breakRealm(ServerPlayer player) {
        var targets = new ArrayList<MaChenXuRoleData>();
        for (ServerPlayer owner : player.serverLevel().players()) {
            var data = SackGhostItems.data(owner);
            if (data != null && data.otherworldActive && player.distanceToSqr(owner) <= 400) targets.add(data);
        }
        if (targets.isEmpty()) return fail(player, "message.noellesroles.relic.no_realm");
        if (!MoneyUtils.hasBalance(player, SackGhostRules.RELIC_BREAK_COST)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(player, SackGhostRules.RELIC_BREAK_COST);
            return false;
        }
        // 同一个服务端线程中先确定目标再统一结算；多目标只扣一次。
        if (!MoneyUtils.cost(player, SackGhostRules.RELIC_BREAK_COST)) return false;
        MoneyFlow.recordExpense(player, MoneyFlow.ExpenseType.SKILL, SackGhostRules.RELIC_BREAK_COST,
                Component.translatable("item.noellesroles.ghost_ward_relic"));
        for (var target : targets) target.endOtherworld(false);
        player.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.9F);
        player.displayClientMessage(Component.translatable("message.noellesroles.relic.realm_closed", SackGhostRules.RELIC_BREAK_COST), true);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.trickster.close_shadow",
                io.wifi.starrailexpress.api.replay.GameReplayUtils.getReplayPlayerDisplayText(player, true)));
        return true;
    }

    private static boolean fail(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return false;
    }
}

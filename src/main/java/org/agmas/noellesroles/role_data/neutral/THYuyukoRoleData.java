package org.agmas.noellesroles.role_data.neutral;

import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.roles.THYuyukoRole;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public class THYuyukoRoleData extends SimpleRoleData {
    public static final int AFK_THRESHOLD = 10 * 20;
    public int ateCount = 0;
    public int winnerNeedCount = 0;
    public int instinctLeft = 0;

    public THYuyukoRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putInt("count", ateCount);
        tag.putInt("need", winnerNeedCount);
        tag.putInt("instinct", instinctLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        ateCount = getIntTag(tag, "count", 0);
        winnerNeedCount = getIntTag(tag, "need", 0);
        instinctLeft = getIntTag(tag, "instinct", 0);
    }

    public void calcWinnerCount() {
        long c = player.level().players().stream().filter(GameUtils::isPlayerAliveAndSurvival).count();
        if (c >= 24) {
            winnerNeedCount = ((int) (c / 3f) + 2);
        } else {
            winnerNeedCount = ((int) (c / 1.5f) + 2);
        }
        sync();
    }

    public boolean tryEat(Player t) {
        if (t == null || !(t instanceof ServerPlayer target)) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target))
            return false;
        if (player.getVehicle() != null || player.isSleeping()) {
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.yuyuko.player.failed", target.getName(),
                            Component.translatable("message.noellesroles.yuyuko.player.failed.no_afk",
                                    AFK_THRESHOLD / 20))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (SREPlayerAFKComponent.KEY.get(target).getAFKTime() < AFK_THRESHOLD) {
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.yuyuko.player.failed", target.getName(),
                            Component.translatable("message.noellesroles.yuyuko.player.failed.no_afk",
                                    AFK_THRESHOLD / 20))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        player.displayClientMessage(Component.translatable("message.noellesroles.yuyuko.success", t.getName()),
                true);
        GameUtils.killPlayer(t, false, player, GameConstants.DeathReasons.YUYUKO_EATEN);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.noellesroles.yuyuko.eat",
                GameReplayUtils.getReplayPlayerDisplayText(player, true),
                GameReplayUtils.getReplayPlayerDisplayText(target, true)));
        ateCount++;
        endEat();
        return true;
    }

    public boolean tryEat(PlayerBodyEntity body) {
        if (body == null) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (body.isRemoved()) {
            return false;
        }
        ateCount++;
        player.displayClientMessage(Component.translatable("message.noellesroles.yuyuko.success", body.getName()),
                true);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.noellesroles.yuyuko.eat",
                GameReplayUtils.getReplayPlayerDisplayText(player, true), body.getName()));
        body.discard();
        endEat();
        return true;
    }

    public void endEat() {

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.MASTER, 0.9F, 0.65F);

        if (ateCount >= winnerNeedCount) {
            if (player.level() instanceof ServerLevel sl)
                RoleUtils.customWinnerWin(sl, "saigyouji_yuyuko", (THMiscRoles.YUYUKO.color()));
            return;
        }
        rewardInstinct(THYuyukoRole.INSTINCT_REWARD_TIME_PLAYER);
    }

    public void rewardInstinct(int ticks) {
        instinctLeft += ticks;
        sync();
    }

    @Override
    public void clientTick() {
        if (instinctLeft > 0) {
            instinctLeft--;
        }
    }

    @Override
    public void serverTick() {
        if (instinctLeft > 0) {
            instinctLeft--;
            if (instinctLeft <= 0) {
                instinctLeft = 0;
                sync();
            }
        }
    }
}

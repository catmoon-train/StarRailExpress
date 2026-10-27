package org.agmas.noellesroles.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.init.ModEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 惧意：所有活着的人都会看见别人的皮肤和名牌在乱，并且每个人都在看自己。
 */
@Environment(EnvType.CLIENT)
public final class DreadlordDreadClient {
    private static int shuffledAt = -1;

    private DreadlordDreadClient() {
    }

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            LocalPlayer self = Minecraft.getInstance().player;
            if (self == null || player == null || player == self || !self.hasEffect(ModEffects.DREADLORD_DREAD)) {
                return PlayerSkinResult.SKIP;
            }
            refreshShuffle(self.tickCount);
            UUID shuffled = NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
            if (shuffled == null) {
                return PlayerSkinResult.SKIP;
            }
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(shuffled);
            if (info == null || info.getSkin() == null) {
                return PlayerSkinResult.SKIP;
            }
            return PlayerSkinResult.playerSkin(info.getSkin());
        });
        OnRenderRoleName.RENDER_PLAYER_NAME.register((self, target, context, delta, font) -> {
            if (self == null || target == null || !self.hasEffect(ModEffects.DREADLORD_DREAD)) {
                return TrueFalseAndCustomResult.pass();
            }
            return TrueFalseAndCustomResult.custom(Component.literal("????")
                    .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_PURPLE));
        });
    }

    public static void lookAtSelf(LocalPlayer self) {
        if (self == null || self.level() == null || !self.hasEffect(ModEffects.DREADLORD_DREAD)) {
            return;
        }
        for (Player other : self.level().players()) {
            if (!(other instanceof AbstractClientPlayer clientPlayer) || clientPlayer == self
                    || clientPlayer.isSpectator()) {
                continue;
            }
            clientPlayer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    self.getEyePosition());
        }
    }

    private static void refreshShuffle(int tick) {
        if (shuffledAt >= 0 && tick - shuffledAt < 8) {
            return;
        }
        shuffledAt = tick;
        List<UUID> keys = new ArrayList<>(ClientSkinCache.PLAYER_ENTRIES_CACHE.keySet());
        if (keys.size() < 2) {
            return;
        }
        List<UUID> shuffled = new ArrayList<>(keys);
        Collections.shuffle(shuffled);
        for (int i = 0; i < keys.size(); i++) {
            NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.put(keys.get(i), shuffled.get(i));
        }
    }
}

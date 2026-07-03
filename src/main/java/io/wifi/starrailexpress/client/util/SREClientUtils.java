package io.wifi.starrailexpress.client.util;

import com.mojang.blaze3d.vertex.BufferUploader;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.minecraft.SharedConstants;
import net.minecraft.client.InputType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SREClientUtils {
    public static UUID getPlayerUidByName(String name) {
        if (name == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(name);
        if (s == null)
            return null;
        return s.getProfile().getId();
    }

    public static String getPlayerNameByUid(UUID uid) {
        if (uid == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return null;
        return s.getProfile().getName();
    }

    public static PlayerInfo getPlayerInfoByUid(UUID uid) {
        if (uid == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return null;
        return s;
    }

    public static List<UUID> getAllPlayersUUID(Level level) {
        if (level.isClientSide) {
            List<UUID> result = new ArrayList<UUID>();
            for (PlayerInfo op : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                result.add(op.getProfile().getId());
            }
            return result;
        }
        return level.players().stream().map((p) -> p.getUUID()).toList();
    }

    public static boolean isPlayerAlive(UUID uid) {
        if (uid == null)
            return false;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return false;
        // 下面相当于 gamemode == SURVIVAL || gamemode == ADVENTURE;
        return s.getGameMode().isSurvival();
    }

    public static PlayerInfo getPlayerInfo(AbstractClientPlayer localPlayer) {
        PlayerInfo playerInfo = localPlayer.getPlayerInfo();
        return playerInfo;
    }

    /**
     * 获取玩家真实皮肤。请注意，此获取到的皮肤信息将不会经过事件处理。建议直接通过 {@code AbstractClientPlayer.getSkin()}
     * 获取处理后的皮肤。
     * <br/>
     * 但如果您在 {@link OnGettingPlayerSkin} 事件中想要获取皮肤，请使用此方法，否则将会出现递归调用从而导致崩溃！
     */
    public static PlayerSkin getPlayerOriginalSkin(AbstractClientPlayer localPlayer) {
        var playerInfo = getPlayerInfo(localPlayer);
        return playerInfo == null ? DefaultPlayerSkin.get(localPlayer.getUUID()) : playerInfo.getSkin();
    }

    public static void setScreenIgnoreMixins(Minecraft minecraft, Screen screen) {
        Minecraft client = minecraft;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            SRE.LOGGER.error("setScreen called from non-game thread");
        }

        if (client.screen != null) {
            client.screen.removed();
        } else {
            client.setLastInputType(InputType.NONE);
        }
        {
            if (screen == null && client.level == null) {
                screen = new TitleScreen();
            } else if (screen == null && client.player.isDeadOrDying()) {
                if (client.player.shouldShowDeathScreen()) {
                    screen = new DeathScreen(null, client.level.getLevelData().isHardcore());
                } else {
                    client.player.respawn();
                }
            }

            client.screen = screen;
            if (client.screen != null) {
                client.screen.added();
            }

            BufferUploader.reset();
            if (screen != null) {
                client.mouseHandler.releaseMouse();
                KeyMapping.releaseAll();
                screen.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                client.noRender = false;
            } else {
                client.getSoundManager().resume();
                client.mouseHandler.grabMouse();
            }

            client.updateTitle();
        }
    }
}

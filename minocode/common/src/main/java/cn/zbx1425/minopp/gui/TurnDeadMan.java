package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.platform.ClientPlatform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class TurnDeadMan {

    public static double deadManElapsedTicks;

    private static final double ALARM_DELAY = 8 * 20;

    private static final SoundInstance alarmSound = new SimpleSoundInstance(
            Mino.id("game.turn_notice_continuous"),
            SoundSource.BLOCKS, 1, 1, SoundInstance.createUnseededRandom(),
            true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true
    );

    public static void pedal() {
        deadManElapsedTicks = Math.min(0, deadManElapsedTicks);
    }

    public static void tick(CardGame game, DeltaTracker deltaTracker) {
        deadManElapsedTicks += deltaTracker.getGameTimeDeltaPartialTick(false);
        LocalPlayer player = Minecraft.getInstance().player;
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        CardPlayer currentPlayer = game.players.get(game.currentPlayerIndex);
        boolean myTurn = cardPlayer.equals(currentPlayer);
        if (!myTurn) deadManElapsedTicks = 0;
        playAlarmSound();
    }

    public static void setOutsideGame() {
        MinoClient.globalFovModifier = 1;
        deadManElapsedTicks = 0;
        playAlarmSound();
    }

    public static boolean isAlarmActive() {
        return deadManElapsedTicks > ALARM_DELAY;
    }

    private static void playAlarmSound() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (isAlarmActive() && !Minecraft.getInstance().isPaused()) {
            if (!soundManager.isActive(alarmSound)) soundManager.play(alarmSound);
        } else {
            if (soundManager.isActive(alarmSound)) soundManager.stop(alarmSound);
        }
    }
}

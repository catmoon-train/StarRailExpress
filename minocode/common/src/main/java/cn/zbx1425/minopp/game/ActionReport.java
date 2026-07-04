package cn.zbx1425.minopp.game;

import cn.zbx1425.minopp.effect.EffectEvent;
import cn.zbx1425.minopp.effect.SoundEffectEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ActionReport {

    private CardGame game;
    private CardPlayer initiator;

    public ActionMessage state;
    public List<ActionMessage> messages = new ArrayList<>();
    public List<EffectEvent> effects = new ArrayList<>();

    public boolean shouldDestroyGame = false;
    public boolean isFail = false;

    private ActionReport(CardGame game, CardPlayer player) {
        this.initiator = player;
        this.game = game;
    }

    public static ActionReport builder(CardGame game, CardPlayer player) {
        return new ActionReport(game, player);
    }

    public static ActionReport builder(CardGame game) {
        return builder(game, null);
    }

    public static ActionReport builder(CardPlayer player) {
        return builder(null, player);
    }

    public static ActionReport builder() {
        return builder(null, null);
    }

    public ActionReport state(Component message) {
        this.state = new ActionMessage(ActionMessage.Type.STATE, message);
        return this;
    }

    public ActionReport fail(Component message) {
        this.isFail = true;
        this.messages.add(new ActionMessage(ActionMessage.Type.FAIL, message));
        return this;
    }

    public ActionReport messageAll(Component message) {
        this.messages.add(new ActionMessage(ActionMessage.Type.MESSAGE_ALL, message));
        return this;
    }

    public ActionReport combineWith(ActionReport other) {
        if (other == null) return this;
        if (this.state == null) this.state = other.state;
        this.messages.addAll(other.messages);
        this.shouldDestroyGame |= other.shouldDestroyGame;
        this.effects.addAll(other.effects);
        return this;
    }

    private static final int SOUND_RANGE = 16;

    public ActionReport sound(ResourceLocation sound, int timeOffset, CardPlayer target) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.of(target.uuid), SoundEvent.createFixedRangeEvent(sound, SOUND_RANGE)));
        return this;
    }

    public ActionReport sound(ResourceLocation sound, int timeOffset) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.empty(), SoundEvent.createFixedRangeEvent(sound, SOUND_RANGE)));
        return this;
    }

    public ActionReport effect(EffectEvent event) {
        effects.add(event);
        return this;
    }

    public ActionReport played() {
        return state(Component.translatable("game.minopp.play.played",
                initiator.name, game.topCard.getDisplayName()));
    }

    public ActionReport cut() {
        return state(Component.translatable("game.minopp.play.cut",
                initiator.name, game.topCard.getDisplayName()));
    }

    public ActionReport drew(int drawCount) {
        return state(Component.translatable("game.minopp.play.drew",
                initiator.name, drawCount));
    }

    public ActionReport playedNoCard(boolean drawn) {
        if (drawn) {
            return state(Component.translatable("game.minopp.play.played_no_drawn_card", initiator.name));
        } else {
            return state(Component.translatable("game.minopp.play.played_no_card", initiator.name));
        }
    }

    public ActionReport gameDestroyed() {
        return state(Component.translatable("game.minopp.play.game_destroyed", initiator.name));
    }

    public ActionReport gameStarted() {
        return state(Component.translatable("game.minopp.play.game_started", initiator.name));
    }

    public ActionReport gameWon() {
        shouldDestroyGame = true;
        MutableComponent result = Component.translatable("game.minopp.play.game_won", initiator.name);
        for (CardPlayer player : game.players) {
            if (!player.equals(initiator)) {
                result = result.append("\n")
                        .append(Component.translatable("game.minopp.play.game_nearly_won", player.name, player.hand.size()));
            }
        }
        return state(result);
    }

    public ActionReport panic(Component message) {
        shouldDestroyGame = true;
        return state(message);
    }

    public static final ActionReport NO_GAME = ActionReport.builder().state(Component.translatable("game.minopp.play.no_game"));
}

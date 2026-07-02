package io.wifi.starrailexpress.event.client;

import java.util.Optional;

import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class OnRenderRoleName {
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_NAME = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });
    public static final Event<RenderWithPuppeteerTargetInterface> RENDER_PUPPETEER = EventFactory.createArrayBacked(
            RenderWithPuppeteerTargetInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderWithPuppeteerTargetInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_COHORT = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_ROLE = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });
    public static final Event<RenderPlayerExtraInterface> RENDER_EXTRA = EventFactory.createArrayBacked(
            RenderPlayerExtraInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerExtraInterface listener : listeners) {
                    listener.renderExtra(p, p1, c, t, r);
                }
            });
    public static final Event<RenderWithNoteTargetInterface> RENDER_NOTE = EventFactory.createArrayBacked(
            RenderWithNoteTargetInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderWithNoteTargetInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.equals(TrueFalseResult.PASS)) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });
    public static final Event<RenderWithPlayerTargetInterface> RENDER_PLAYER = EventFactory
            .createArrayBacked(
                    RenderWithPlayerTargetInterface.class,
                    listeners -> (p, p1, c, t, r) -> {
                        for (RenderWithPlayerTargetInterface listener : listeners) {
                            var result = listener.allowRender(p, p1, c, t, r);
                            if (result != null && !result.equals(TrueFalseResult.PASS)) {
                                return result;
                            }
                        }
                        return TrueFalseResult.PASS;
                    });
    public static final Event<RenderHeadInterface> RENDER_ALL = EventFactory.createArrayBacked(
            RenderHeadInterface.class,
            listeners -> (p, c, t, r) -> {
                for (RenderHeadInterface listener : listeners) {
                    var result = listener.allowRender(p, c, t, r);
                    if (result != null && !result.equals(TrueFalseResult.PASS)) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });
    public static final Event<PlayerRangeInterface> RENDER_RANGE = EventFactory.createArrayBacked(
            PlayerRangeInterface.class,
            listeners -> (player, original) -> {
                for (PlayerRangeInterface listener : listeners) {
                    var result = listener.getPlayerRange(player, original);
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
                return Optional.of(original);
            });

    public interface PlayerRangeInterface {
        Optional<Float> getPlayerRange(Player player, float original);
    }

    public interface RenderHeadInterface {
        TrueFalseResult allowRender(Player player, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    public interface RenderWithPlayerTargetInterface {
        TrueFalseResult allowRender(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    public interface RenderWithPuppeteerTargetInterface {
        TrueFalseResult allowRender(Player player, PuppeteerBodyEntity target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }
    public interface RenderWithNoteTargetInterface {
        TrueFalseResult allowRender(Player player, NoteEntity targetNote, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    public interface RenderPlayerNameInterface {
        TrueFalseAndCustomResult<Component> allowRender(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    public interface RenderPlayerExtraInterface {
        void renderExtra(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

}

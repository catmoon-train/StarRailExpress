package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端侧：根据小游戏ID创建对应的Screen实例
 */
public class MinigameScreenFactory {

    @FunctionalInterface
    public interface ScreenConstructor {
        Screen create(BlockPos pos, Runnable onSuccess);
    }

    private static final Map<String, ScreenConstructor> FACTORY = new LinkedHashMap<>();

    static {
        FACTORY.put("lockpick", LockpickMinigameScreen::new);
        FACTORY.put("key_making", KeyMakingMinigameScreen::new);
        FACTORY.put("math", MathMinigameScreen::new);
        FACTORY.put("cooking", CookingMinigameScreen::new);
        FACTORY.put("reactor_temperature", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.REACTOR_TEMPERATURE));
        FACTORY.put("box_sort", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.BOX_SORT));
        FACTORY.put("wire_connect", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.WIRE_CONNECT));
        FACTORY.put("hold_button", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.HOLD_BUTTON));
        FACTORY.put("clean_debris", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.CLEAN_DEBRIS));
        FACTORY.put("shooting", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SHOOTING));
        FACTORY.put("wire_tuning", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.WIRE_TUNING));
        FACTORY.put("signal_calibration", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SIGNAL_CALIBRATION));
        FACTORY.put("shape_match", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SHAPE_MATCH));
        FACTORY.put("sequence_buttons", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SEQUENCE_BUTTONS));
        FACTORY.put("clean_stains", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.CLEAN_STAINS));
        FACTORY.put("pull_lever", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.PULL_LEVER));
        FACTORY.put("trash_recycle", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.TRASH_RECYCLE));
        FACTORY.put("item_checklist", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.ITEM_CHECKLIST));
        FACTORY.put("swipe_card", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SWIPE_CARD));
        FACTORY.put("play_music", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.PLAY_MUSIC));
        FACTORY.put("rhythm", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.RHYTHM));
        FACTORY.put("light_candles", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.LIGHT_CANDLES));
        FACTORY.put("whack_mole", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.WHACK_MOLE));
        FACTORY.put("puzzle", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.PUZZLE));
        FACTORY.put("storage", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.STORAGE));
        FACTORY.put("slice_food", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SLICE_FOOD));
        FACTORY.put("three_cards", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.THREE_CARDS));
        FACTORY.put("break_jar", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.BREAK_JAR));
        FACTORY.put("zone_calibration", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.ZONE_CALIBRATION));
        FACTORY.put("water_valve", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.WATER_VALVE));
        FACTORY.put("typing", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.TYPING));
        FACTORY.put("pipe_bird", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.PIPE_BIRD));
        FACTORY.put("fruit_ninja", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.FRUIT_NINJA));
        FACTORY.put("mouse_whack", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.MOUSE_WHACK));
        FACTORY.put("brick_breaker", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.BRICK_BREAKER));
        FACTORY.put("make_change", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.MAKE_CHANGE));
        FACTORY.put("snake", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SNAKE));
        FACTORY.put("minesweeper", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.MINESWEEPER));
        FACTORY.put("simon_says", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.SIMON_SAYS));
        FACTORY.put("game_2048", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.GAME_2048));
        FACTORY.put("catch_eggs", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.CATCH_EGGS));
        FACTORY.put("color_sort", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.COLOR_SORT));
        FACTORY.put("guess_number", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.GUESS_NUMBER));
        FACTORY.put("reaction_test", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.REACTION_TEST));
        FACTORY.put("link_match", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.LINK_MATCH));
        FACTORY.put("tetris", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.TETRIS));
        FACTORY.put("memory_match", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.MEMORY_MATCH));
        FACTORY.put("pipe_connect", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.PIPE_CONNECT));
        FACTORY.put("lights_out", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.LIGHTS_OUT));
        FACTORY.put("game_24", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.GAME_24));
        FACTORY.put("maze", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.MAZE));
        FACTORY.put("balance_scale", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.BALANCE_SCALE));
        FACTORY.put("klotski", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.KLOTSKI));
        FACTORY.put("gold_miner", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.GOLD_MINER));
        FACTORY.put("one_stroke", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.ONE_STROKE));
        FACTORY.put("claw_machine", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.CLAW_MACHINE));
        FACTORY.put("balloon_sniper", (pos, onSuccess) -> new SimpleQuestMinigameScreen(pos, onSuccess,
                SimpleQuestMinigameScreen.Mode.BALLOON_SNIPER));
        FACTORY.put("extinguish_fire", (pos, onSuccess) -> new PhysicalQuestMinigameScreen(pos, onSuccess,
                PhysicalQuestMinigameScreen.Kind.EXTINGUISH));
        FACTORY.put("pachinko", (pos, onSuccess) -> new PhysicalQuestMinigameScreen(pos, onSuccess,
                PhysicalQuestMinigameScreen.Kind.PACHINKO));
        FACTORY.put("mix_drink", (pos, onSuccess) -> new PhysicalQuestMinigameScreen(pos, onSuccess,
                PhysicalQuestMinigameScreen.Kind.MIX_DRINK));
        FACTORY.put("balloon_pump", (pos, onSuccess) -> new PhysicalQuestMinigameScreen(pos, onSuccess,
                PhysicalQuestMinigameScreen.Kind.BALLOON_PUMP));
        FACTORY.put("throw_ball", (pos, onSuccess) -> new PhysicalQuestMinigameScreen(pos, onSuccess,
                PhysicalQuestMinigameScreen.Kind.THROW_BALL));
    }

    public static Screen create(String minigameId, BlockPos pos, Runnable onSuccess) {
        ScreenConstructor ctor = FACTORY.get(minigameId);
        return ctor != null ? ctor.create(pos, onSuccess) : null;
    }
}

package pro.fazeclan.river.stupid_express;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;
import io.wifi.ConfigCompact.ConfigClassHandler;

@Config(name = "stupid_express")
public class StupidExpressConfig implements ConfigData {
    public static ConfigClassHandler<StupidExpressConfig> HANDLER = new ConfigClassHandler<>(
            StupidExpressConfig.class);

    public static StupidExpressConfig getInstance() {
        return HANDLER.instance();
    }

    @CollapsibleObject
    public RolesSection rolesSection = new RolesSection();

    public static class RolesSection {
        @CollapsibleObject
        public NecromancerSection necromancerSection = new NecromancerSection();

        public static class NecromancerSection {
            public boolean necromancerHasShop = false;
        }
        @CollapsibleObject
        public ArsonistSection arsonistSection = new ArsonistSection();

        public static class ArsonistSection {
            public boolean arsonistKeepsGameGoing = true;
        }

        @CollapsibleObject
        public InitiateSection initiateSection = new InitiateSection();

        public static class InitiateSection {

            public enum InitiateFallbackOptions {
                AMNESIAC,
                KILLER,
                NEUTRAL;
                // @Override
                // public @NotNull String prefix() {
                // return "stupid_express.config.initiate_fallback_options";
                // }
            }

            public InitiateFallbackOptions initiateFallbackRole = InitiateFallbackOptions.AMNESIAC;
        }
        @CollapsibleObject
        public AmnesiacSection amnesiacSection = new AmnesiacSection();

        public static class AmnesiacSection {
            public boolean bodiesGlowToAmnesiac = true;
            public boolean amnesiacGlowsDifferently = false;
        }

    }

    @CollapsibleObject
    public ModifiersSection modifiersSection = new ModifiersSection();

    public static class ModifiersSection {
        @CollapsibleObject
        public LoversSection loversSection = new LoversSection();

        public static class LoversSection {
            public boolean loversKnowImmediately = true;
            public boolean loversWinWithKillers = false;
            public boolean loversWinWithCivilians = true;
        }
    }

}

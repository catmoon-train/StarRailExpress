package pro.fazeclan.river.stupid_express;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;

@Config(name = "stupid_express")
public class StupidExpressConfig implements ConfigData {
    public static ConfigClassHandler<StupidExpressConfig> HANDLER = new ConfigClassHandler<>(
            StupidExpressConfig.class);

    public static StupidExpressConfig getInstance() {
        return HANDLER.instance();
    }

    @CollapsibleObject
    @ConfigSync(shouldSync = true)
    public RolesSection rolesSection = new RolesSection();

    public static class RolesSection {
        @CollapsibleObject
        public ArsonistSection arsonistSection = new ArsonistSection();

        public static class ArsonistSection {
            public boolean arsonistKeepsGameGoing = true;
        }

        @CollapsibleObject
        public AmnesiacSection amnesiacSection = new AmnesiacSection();

        public static class AmnesiacSection {
            public boolean amnesiacGlowsDifferently = false;
        }
    }

    @CollapsibleObject
    @ConfigSync(shouldSync = true)
    public ModifiersSection modifiersSection = new ModifiersSection();

    public static class ModifiersSection {
        @CollapsibleObject
        public LoversSection loversSection = new LoversSection();

        public static class LoversSection {
            public boolean loversKnowImmediately = true;
        }
    }
}

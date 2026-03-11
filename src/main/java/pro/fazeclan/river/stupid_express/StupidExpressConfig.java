package pro.fazeclan.river.stupid_express;

//import me.fzzyhmstrs.fzzy_config.api.FileType;
//import me.fzzyhmstrs.fzzy_config.api.SaveType;
//import me.fzzyhmstrs.fzzy_config.config.Config;
//import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
//import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;
//import org.jetbrains.annotations.NotNull;


public class StupidExpressConfig
      //  extends Config
{

    public static StupidExpressConfig getInstance() {
        return new StupidExpressConfig();
    }

    public RolesSection rolesSection = new RolesSection();
    public static class RolesSection  {

        public NecromancerSection necromancerSection = new NecromancerSection();
        public static class NecromancerSection  {
            public boolean necromancerHasShop = false;
        }

        public ArsonistSection arsonistSection = new ArsonistSection();
        public static class ArsonistSection {
            public boolean arsonistKeepsGameGoing = false;
        }

        public InitiateSection initiateSection = new InitiateSection();
        public static class InitiateSection {

            public enum InitiateFallbackOptions  {
                AMNESIAC,
                KILLER,
                NEUTRAL;

//                @Override
//                public @NotNull String prefix() {
//                    return "stupid_express.config.initiate_fallback_options";
//                }
            }

            public InitiateFallbackOptions initiateFallbackRole = InitiateFallbackOptions.AMNESIAC;
        }

        public AmnesiacSection amnesiacSection = new AmnesiacSection();
        public static class AmnesiacSection  {
            public boolean bodiesGlowToAmnesiac = true;
            public boolean amnesiacGlowsDifferently = false;
        }

    }

    public ModifiersSection modifiersSection = new ModifiersSection();
    public static class ModifiersSection  {

        public LoversSection loversSection = new LoversSection();
        public static class LoversSection  {
            public boolean loversKnowImmediately = true;
            public boolean loversWinWithKillers = false;
            public boolean loversWinWithCivilians = true;
        }

    }


}

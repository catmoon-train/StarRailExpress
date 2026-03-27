package pro.fazeclan.river.stupid_express;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;

import java.util.ArrayList;
import java.util.List;

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

        @CollapsibleObject
        public RoleUnlockSection roleUnlockSection = new RoleUnlockSection();

        public static class RoleUnlockSection {
            /** 是否启用职业解锁系统（关闭后所有职业直接可用） */
            public boolean enableRoleUnlockSystem = false;
            /** 是否在读取存档时自动解锁下方基础职业列表 */
            public boolean unlockBasicRolesAtStart = true;
            /** 默认解锁的基础职业 ID 列表（namespace:path） */
            public List<String> basicDefaultUnlockedRoles = new ArrayList<>(List.of(
                    "noellesroles:baka",
                    "noellesroles:jester",
                    "noellesroles:conductor",
                    "noellesroles:doctor",
                    "noellesroles:locksmith",
                    "noellesroles:pachuri"
            ));
        }

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

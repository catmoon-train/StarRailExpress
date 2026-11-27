/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.ConfigCompact.ui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.noellesroles.utils.RoleUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class RoleManageConfigUI {

    public static HashMap<String, Boolean> RoleEnableStatus = new HashMap<>();
    public static HashMap<String, Boolean> ModifierEnableStatus = new HashMap<>();

    // ── 人数快照（设置页显示用）────────────────────────────────────────
    // 服务端在回角色 / 修饰符开关快照时一起带上：forced* = 强制值（-1 = 用自动公式），
    // auto* = 服务端按当前人数算出的自动值。设置页据此显示「自动 (N)」还是具体数字。
    /** 强制杀手人数（-1 = 自动）。 */
    public static int forcedKillerCount = -1;
    /** 强制义警人数（-1 = 自动）。 */
    public static int forcedVigilanteCount = -1;
    /** 强制中立人数（-1 = 自动）。 */
    public static int forcedNeutralCount = -1;
    /** 按当前人数算出的自动杀手人数。 */
    public static int autoKillerCount;
    /** 按当前人数算出的自动义警人数。 */
    public static int autoVigilanteCount;
    /** 按当前人数算出的自动中立人数。 */
    public static int autoNeutralCount;

    /** 收到服务端快照时更新人数（{@code RoleEnableInfoPacket}）。 */
    public static void setRoleCounts(int killer, int vigilante, int neutral,
            int autoKiller, int autoVigilante, int autoNeutral) {
        forcedKillerCount = killer;
        forcedVigilanteCount = vigilante;
        forcedNeutralCount = neutral;
        autoKillerCount = autoKiller;
        autoVigilanteCount = autoVigilante;
        autoNeutralCount = autoNeutral;
    }

    // ── 本局覆盖（设置页可改，直接影响下一局）────────────────────────────
    /** 本局时长覆盖（分钟）；{@code -1} = 跟随模式默认。 */
    public static int roundMinutes = -1;
    /** 本局初始金币覆盖；{@code -1} = 跟随配置，{@code 0} = 真的不发钱。 */
    public static int roundStartingMoney = -1;
    /** 配置里的初始金币（「自动」时显示它）。 */
    public static int defaultStartingMoney;

    /** 收到服务端快照时更新本局覆盖。 */
    public static void setRoundOverrides(int minutes, int startingMoney, int defaultMoney) {
        roundMinutes = minutes;
        roundStartingMoney = startingMoney;
        defaultStartingMoney = defaultMoney;
    }

    public static class RoleAndModifierSyncInfo {
        public HashMap<String, Boolean> roleInfo;
        public HashMap<String, Boolean> modifierInfo;
        /**
         * 服务端强制人数（-1 = 用自动公式）。
         *
         * <p>
         * 字段带初始值是有意的：老服务端发来的 JSON 里没有这些字段时，Gson 会走无参构造，
         * 于是 forced* 保持 -1（= 自动）而不是 0（= 一人都不要），不会把界面显示成错的数字。
         */
        public int forcedKillerCount = -1;
        public int forcedVigilanteCount = -1;
        public int forcedNeutralCount = -1;
        /** 服务端按当前人数算出的自动值。 */
        public int autoKillerCount;
        public int autoVigilanteCount;
        public int autoNeutralCount;

        /** 本局时长覆盖（分钟）；-1 = 跟随模式默认。 */
        public int roundMinutes = -1;
        /** 本局初始金币覆盖；-1 = 跟随配置，0 = 真的不发钱。 */
        public int roundStartingMoney = -1;
        /** 配置里的初始金币（设置页显示「自动 (N)」用）。 */
        public int defaultStartingMoney;

        public RoleAndModifierSyncInfo() {
            this(new HashMap<>(), new HashMap<>());
        }

        public RoleAndModifierSyncInfo(HashMap<ResourceLocation, Boolean> roleInfos,
                HashMap<ResourceLocation, Boolean> modifierInfos) {
            this.roleInfo = new HashMap<>();
            this.modifierInfo = new HashMap<>();
            for (var r : roleInfos.entrySet()) {
                this.roleInfo.put(r.getKey().toString(), r.getValue());
            }
            for (var r : modifierInfos.entrySet()) {
                this.modifierInfo.put(r.getKey().toString(), r.getValue());
            }
        }
    }

    public static void setRoleInfo(HashMap<String, Boolean> packetInfo) {
        RoleEnableStatus.clear();
        RoleEnableStatus.putAll(packetInfo);
    }

    public static void setModifierInfo(HashMap<String, Boolean> packetInfo) {
        ModifierEnableStatus.clear();
        ModifierEnableStatus.putAll(packetInfo);
    }

    public static Screen getScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.starrailexpress.role_config"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory roleCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.role"));
        ConfigCategory modifierCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.modifier"));
        if (Minecraft.getInstance().player == null) {
            RoleEnableStatus.clear();
            ModifierEnableStatus.clear();
        }
        if (RoleEnableStatus.isEmpty()) {
            RoleEnableStatus.clear();
            for (var info : TMMRoles.ROLES.keySet()) {
                if (HarpyModLoaderConfig.HANDLER.instance().getDisabled().contains(info.toString())) {
                    RoleEnableStatus.put(info.toString(), false);
                } else {
                    RoleEnableStatus.put(info.toString(), true);
                }
            }
        }
        if (ModifierEnableStatus.isEmpty()) {
            ModifierEnableStatus.clear();
            for (var info : HMLModifiers.MODIFIERS) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(info.identifier().toString())) {
                    ModifierEnableStatus.put(info.identifier().toString(), false);
                } else {
                    ModifierEnableStatus.put(info.identifier().toString(), true);
                }
            }
        }
        ArrayList<Entry<String, Boolean>> entrySets = new ArrayList<>(RoleEnableStatus.entrySet());
        sortRoles(entrySets);
        for (var info : entrySets) {
            var roleId = info.getKey();
            roleCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.role_enable_option",
                                            RoleUtils.getTeamName(ResourceLocation.tryParse(roleId)),
                                            RoleUtils.getRoleName(ResourceLocation.tryParse(roleId)), roleId),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString(), RoleUtils.getRoleDescription(roleId)))
                            .setSaveConsumer(newValue -> RoleEnableStatus.put(roleId, newValue))
                            .build());
        }
        for (var info : ModifierEnableStatus.entrySet()) {
            var roleId = info.getKey();
            modifierCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.modifier_enable_option",
                                            RoleUtils.getModifierName(ResourceLocation.tryParse(roleId)), roleId),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString(), RoleUtils.getModifierDescription(roleId)))
                            .setSaveConsumer(newValue -> ModifierEnableStatus.put(roleId, newValue))
                            .build());
        }

        builder.setSavingRunnable(() -> {
            HarpyModLoaderConfig.HANDLER.instance().disabled.clear();
            for (Entry<String, Boolean> entry : RoleEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabled.add(entry.getKey());
                }
            }
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.clear();
            for (Entry<String, Boolean> entry : ModifierEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.add(entry.getKey());
                }
            }
            HarpyModLoaderConfig.HANDLER.save();

            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null
                    && !Minecraft.getInstance().isLocalServer()) {
                if (Minecraft.getInstance().player.hasPermissions(2)) {
                    String roleCommandPrefix = "setEnabledRole";
                    String modifierCommandPrefix = "setEnabledModifier";
                    {
                        Minecraft.getInstance().player.connection.sendCommand(roleCommandPrefix + " enableAll");
                        Minecraft.getInstance().player.connection.sendCommand(modifierCommandPrefix + " enableAll");
                    }
                    for (var role : HarpyModLoaderConfig.HANDLER.instance().disabledModifiers) {
                        Minecraft.getInstance().player.connection
                                .sendCommand(modifierCommandPrefix + " " + role + " false");
                    }
                    for (var role : HarpyModLoaderConfig.HANDLER.instance().getDisabled()) {
                        Minecraft.getInstance().player.connection
                                .sendCommand(roleCommandPrefix + " " + role + " false");
                    }
                }
            }
        });
        return builder.build();
    }

    private static void sortRoles(ArrayList<Entry<String, Boolean>> clone) {
        Collator collator = Collator.getInstance();
        boolean killerFirst = false;
        clone.sort((ea, eb) -> {
            SRERole a = TMMRoles.ROLES.get(ResourceLocation.parse(ea.getKey()));
            SRERole b = TMMRoles.ROLES.get(ResourceLocation.parse(eb.getKey()));
            int rt_a = RoleUtils.getRoleType(a);
            int rt_b = RoleUtils.getRoleType(b);
            if (a != null && b != null) {
                if (rt_a > rt_b)
                    return killerFirst ? -1 : 1;
                if (rt_a < rt_b)
                    return killerFirst ? 1 : -1;
                if (a.identifier().getNamespace().equals(b.identifier().getNamespace())) {
                    String r_a = RoleUtils.getRoleName(a).getString();
                    String r_b = RoleUtils.getRoleName(b).getString();
                    return collator.compare(r_a, r_b);
                } else {
                    String nameSpaceA = a.identifier().getNamespace();
                    String nameSpaceB = b.identifier().getNamespace();
                    return collator.compare(nameSpaceA, nameSpaceB);
                }
            } else {
                return 0;
            }
        });
    }

    public static void startConfigUI() {
        Screen screen = getScreen(Minecraft.getInstance().screen);
        Minecraft.getInstance().setScreen(screen);
    }
}

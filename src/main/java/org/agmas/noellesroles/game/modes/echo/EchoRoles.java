package org.agmas.noellesroles.game.modes.echo;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

import java.util.EnumMap;
import java.util.Map;

/**
 * 专属身份显式分配，永远不进入普通模式随机职业池。
 *
 * <p>命名空间用回声模式自己的 {@value #NAMESPACE}，不再借用 Noellesroles；
 * path 沿用 {@code "echo_"} 前缀原则，职业 ID 形如 {@code echo:echo_carrier} /
 * {@code echo:echo_interceptor} / {@code echo:echo_broker}。显示名/目标/简介的
 * 翻译键只取 path（{@code announcement.star.role.<path>}、{@code announcement.star.goals.<path>}、
 * {@code info.screen.roleid.<path>}），{@code TMMRoles} 的查重也按 path，需保持全局唯一。
 */
public final class EchoRoles {
    /** 回声模式自己的命名空间。 */
    public static final String NAMESPACE = "echo";

    /**
     * 回响托运站模式标签。所有回声身份统一挂上，介绍页「回响托运站」筛选与旗标筛选都按它归类。
     */
    public static final String ECHO_MODE_FLAG = "echo_freight";

    private static final Map<EchoFaction, SRERole> ROLES = new EnumMap<>(EchoFaction.class);

    private EchoRoles() { }

    public static void register() {
        if (!ROLES.isEmpty()) return;
        for (EchoFaction faction : EchoFaction.values()) {
            String suffix = switch (faction) {
                case CARRIER -> "carrier";
                case INTERCEPTOR -> "interceptor";
                case BROKER -> "broker";
            };
            int color = switch (faction) {
                case CARRIER -> 0xFF44BB66;
                case INTERCEPTOR -> 0xFFCC2233;
                case BROKER -> 0xFFCCAA22;
            };
            SRERole role = new NormalRole(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "echo_" + suffix), color,
                    faction == EchoFaction.CARRIER, faction == EchoFaction.INTERCEPTOR,
                    SRERole.MoodType.NONE, -1, true) {
                @Override public boolean canBeRandomed() { return false; }
                @Override public java.util.List<io.wifi.starrailexpress.util.ShopEntry> getShopEntries() { return java.util.List.of(); }
                @Override public net.minecraft.world.InteractionResult onDropItem(net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack stack) {
                    return org.agmas.noellesroles.game.modes.echo.content.EchoContent.tool(stack)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS;
                }
            };
            role.setRoleData(EchoRoleData::new).setOtherModeRole(true).setDefaultMax(0)
                    .setDefaultEnableChance(0).setCanBeRandomedByOtherRoles(false)
                    .setCanSeeCoin(false).setCanAutoAddMoney(false).setCanAutoAddMiniGameToken(false)
                    .setPassiveIncome(false).setInitialCoinCount(0).setKillExtraCoinAwards(0)
                    .setCanUseInstinctAndNightVision(false).setCanPickUpRevolver(false);
            // 统一挂上回响托运站模式标签，介绍页按此自动归类
            role.getFlags().add(ECHO_MODE_FLAG);
            ROLES.put(faction, TMMRoles.registerRole(role));
        }
    }

    public static SRERole get(EchoFaction faction) {
        SRERole role = ROLES.get(faction);
        if (role == null) throw new IllegalStateException("回声身份尚未注册");
        return role;
    }
}

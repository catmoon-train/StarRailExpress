package org.agmas.noellesroles.game.halloween.pumpkin;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;

import java.util.List;

/** 普通可选池中的独立中立职业；不使用彩蛋池，也不额外替换已分配职业。 */
public final class PumpkinSpiritRole extends CustomWinnerRole {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("noellesroles", "pumpkin_spirit");
    private static SRERole registered;

    private PumpkinSpiritRole() {
        super(ID, 0xEF8B24, RoleType.INDEPENDENT_WIN_NEUTRAL, MoodType.NONE, Integer.MAX_VALUE, true);
        setRoleData(PumpkinSpiritRoleData::new);
        setDefaultMax(1);
        setDefaultEnableChance(10000);
        setCanBeRandomedByOtherRoles(false);
        setCanSpawnInMap((map, settings) -> settings != null && settings.halloweenEventEnabled);
        setPassiveIncome(false);
    }

    /** 必须早于职业刷新配置加载调用；可重复调用但不重复注册。 */
    public static SRERole register() {
        if (registered == null) registered = TMMRoles.registerRole(new PumpkinSpiritRole());
        return registered;
    }

    @Override public boolean canBeRandomed(Level world) {
        return world != null && HalloweenRuntime.isMapEnabled(world)
                && SREGameWorldComponent.KEY.get(world).gameMode == io.wifi.starrailexpress.api.SREGameModes.MURDER
                && super.canBeRandomed(world);
    }

    @Override public int getRoundMaxCount(ServerLevel level, SREGameWorldComponent game,
            List<ServerPlayer> players, String map, AreasSettings settings) {
        if (!canBeRandomed(level)) return 0;
        int configured = super.getRoundMaxCount(level, game, players, map, settings);
        return configured < 0 ? 1 : Math.min(1, configured);
    }

    @Override public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(HalloweenItems.CANDY.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
    }

    @Override public InteractionResult onDropItem(Player player, ItemStack stack) {
        if (stack.is(HalloweenItems.CANDY)) {
            // ServerDropManager: SUCCESS 允许丢弃；PASS 会继续走丢弃白名单，不保证放行。
            return InteractionResult.SUCCESS;
        }
        return super.onDropItem(player, stack);
    }

    @Override public GameUtils.WinStatus checkWin(ServerPlayer player, GameUtils.WinStatus original) {
        if (PumpkinSpiritHandler.checkVictory(player)) return GameUtils.WinStatus.CUSTOM;
        return GameUtils.WinStatus.NOT_MODIFY;
    }

    @Override public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus status) {
        return PumpkinSpiritHandler.wasWinner(player);
    }
}

package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 愚者角色 - 好人阵营
 *
 * 核心机制：
 * 1. 尊名纸条：玩家祷告后获得"塔罗会成员"标签
 * 2. 塔罗会：G键召开，所有成员可加入讨论和投票
 * 3. 处刑者手枪：只能对异端玩家造成伤害
 * 4. 死亡后：塔罗会仍可召开，冷却变为6分钟
 */
public class FoolRole extends NormalRole {

    public FoolRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime,
            boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>(super.getDefaultItems());
        // 开局自带处刑者手枪
        items.add(new ItemStack(ModItems.EXECUTIONER_GUN));
        return items;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();
        // 尊名纸条：50金币
        entries.add(new ShopEntry(new ItemStack(ModItems.HONORED_NOTE), 50, ShopEntry.Type.TOOL));
        // 灵性斗篷：200金币
        entries.add(new ShopEntry(new ItemStack(ModItems.SPIRIT_CLOAK), 200, ShopEntry.Type.TOOL));
        return entries;
    }

    @Override
    public boolean onUseGun(Player player) {
        // 允许愚者使用处刑者手枪
        return true;
    }

    @Override
    public boolean onGunHit(Player killer, Player victim) {
        if (!(killer instanceof ServerPlayer serverKiller)) return false;

        // 使用处刑者手枪的特殊逻辑
        if (killer.getMainHandItem().getItem() instanceof ExecutionerGunItem) {
            return ExecutionerGunItem.handleServerShoot(serverKiller, victim);
        }

        // 普通左轮（死亡后掉落的）正常处理
        return true;
    }

    @Override
    public boolean onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        if (victim instanceof ServerPlayer serverPlayer) {
            FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(serverPlayer);

            // 死亡后掉落处刑者手枪（变为一次性手枪）
            // 处刑者手枪在死亡时由物品掉落机制自然处理
            // 普通玩家捡起后当作OnceRevolver使用

            // 冷却变为6分钟
            // （在TarotAssemblyManager.startAssembly中已根据存活状态判断）
        }

        return super.onDeath(victim, spawnBody, killer, deathReason);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        TarotAssemblyManager.serverTick(player, gameComponent);
        super.serverTick(player);
    }
}

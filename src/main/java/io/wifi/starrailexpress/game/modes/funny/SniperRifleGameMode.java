package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.item.DerringerItem;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 狙击模式
 * <p>
 *     模式特性：所有人获得一把狙击枪和上百发子弹
 *     地图锁定为：天空村庄
 * </p>
 */
public class SniperRifleGameMode extends WTLooseEndsGameMode {
    public SniperRifleGameMode(ResourceLocation identifier) {
        super(identifier);
    }
    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(TMMItems.SNIPER_RIFLE::getDefaultInstance);
        looseEndsItems.add(TMMItems.SCOPE::getDefaultInstance);
        looseEndsItems.add(()->{
            ItemStack bullet = new ItemStack(TMMItems.MAGNUM_BULLET);
            bullet.setCount(999);
            return bullet;
        });
        looseEndsItems.removeIf(item -> item.get().getItem() instanceof KnifeItem);
        looseEndsItems.removeIf(item -> item.get().getItem() instanceof DerringerItem);
    }
    @Override
    protected void initCoolDownItems(List<ServerPlayer> players) {
        super.initCoolDownItems(players);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(TMMItems.SNIPER_RIFLE, cooldown);
        }
    }
}

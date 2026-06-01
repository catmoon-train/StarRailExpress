package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.DerringerItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 狙击模式
 * <p>
 * 模式特性：所有人获得一把狙击枪和上百发子弹
 * 地图不锁定
 * </p>
 */
public class SRESniperRifleGameMode extends WTLooseEndsGameMode {
    public SRESniperRifleGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(TMMItems.SNIPER_RIFLE::getDefaultInstance);
        looseEndsItems.add(TMMItems.SCOPE::getDefaultInstance);
        looseEndsItems.add(() -> {
            ItemStack bullet = new ItemStack(TMMItems.MAGNUM_BULLET);
            bullet.setCount(999);
            return bullet;
        });
        looseEndsItems.removeIf(item -> item.get().getItem() instanceof KnifeItem);
        looseEndsItems.removeIf(item -> item.get().getItem() instanceof DerringerItem);
    }

    @Override
    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        super.initCoolDownItems(players, gameWorldComponent);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(TMMItems.SNIPER_RIFLE, cooldown);
        }
    }
}

package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnDeathWithBody;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 尸体搜刮：60s 模式玩家死亡时，把其背包可用物资（含背包/武器等，跳过占位屏障）
 * 装进尸体箱——存活玩家右键尸体即可搜刮（查看权限由
 * {@link net.exmo.sre.sixtyseconds.SixtySecondsGameMode#canSeeBodyContent()} 放开，
 * 开箱走 {@code PlayerBodyEntity.interactAt} 原有尸体箱界面，一次一人防抢）。
 * 不改 io.wifi：挂 {@link OnDeathWithBody} 事件，在尸体生成后覆写其箱内容。
 */
public final class SixtySecondsCorpseLoot {

    private SixtySecondsCorpseLoot() {
    }

    public static void register() {
        OnDeathWithBody.EVENT.register((victim, killer, deathReason, body) -> {
            if (body == null || !SixtySecondsMod.isActive(victim.level())) {
                return;
            }
            fillCorpse(body, victim.getInventory());
        });
    }

    /** 用死者背包重建尸体箱内容（覆盖默认拷贝的 0-13 槽映射，收全部可用物资）。 */
    private static void fillCorpse(PlayerBodyEntity body, net.minecraft.world.entity.player.Inventory inventory) {
        SimpleContainer corpse = body.getComponent().getCorpseInventory();
        corpse.clearContent();
        int slot = 0;
        for (int i = 0; i < inventory.getContainerSize() && slot < corpse.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.is(Items.BARRIER)) {
                continue; // 跳过空槽与两排背包限制的占位屏障
            }
            corpse.setItem(slot++, stack.copy());
        }
        body.getComponent().sync();
    }
}

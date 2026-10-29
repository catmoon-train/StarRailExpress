package org.agmas.noellesroles.game.command_block.item;

import net.minecraft.world.item.Item;

/** 旧错误核心仅保留物品 ID 兼容存档；实体傀儡不再向玩家发放自爆物品。 */
public class ErrorCoreItem extends Item {
    public ErrorCoreItem(Properties properties) {
        super(properties);
    }
}

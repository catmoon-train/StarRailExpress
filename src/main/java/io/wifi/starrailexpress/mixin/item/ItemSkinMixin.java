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

package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 让原版物品（弓、弩）像 {@link SkinableItem} 一样拥有皮肤：
 * <ul>
 * <li>服务端在物品栏 tick 时把装备的皮肤写进 {@code SKIN} 组件，随物品栈同步；</li>
 * <li>物品提示里追加「皮肤：xxx」。</li>
 * </ul>
 * {@link SkinableItem} 自己已实现这两件事（且覆写了这两个方法、不调用 super），
 * 这里额外做一次类型判断，避免重复处理。
 */
@Mixin(Item.class)
public abstract class ItemSkinMixin {

    /**
     * 原版弓/弩没有可覆写的子类逻辑，在这里补上 {@code SKIN} 组件盖章。
     * 只在服务端写入，客户端以服务端同步过来的组件为准。
     */
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void sre$stampSkinComponent(ItemStack stack, Level level, Entity entity, int slot, boolean selected,
            CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            ItemSkinManager.applyEquippedSkin(player, stack);
        }
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void sre$appendSkinTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag, CallbackInfo ci) {
        if ((Object) this instanceof SkinableItem) {
            return;
        }
        Player player = context instanceof Player p ? p : null;
        ItemSkinManager.appendSkinTooltip(stack, tooltip, player);
    }
}

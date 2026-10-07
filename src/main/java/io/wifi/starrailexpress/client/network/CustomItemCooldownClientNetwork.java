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

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import io.wifi.starrailexpress.network.CustomItemCooldownS2CPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;

/**
 * 自定义列车物品冷却包的客户端接收（客户端专属类）。
 *
 * <p>包本体只放 record + ID + CODEC，receiver 与客户端类引用全部放在这里
 * （见 {@code docs/角色开发指南.md} §9 的三条网络铁律）。
 *
 * <p>收到包后把冷却条目写进<b>原版</b> {@link ItemCooldowns} 的那张表，键是这把自定义物品
 * 专属的冷却键。之后倒计时推进、过期清理、物品栏覆盖层全部交给原版，客户端不再单独存一份状态。
 */
@Environment(EnvType.CLIENT)
public class CustomItemCooldownClientNetwork {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomItemCooldownS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null) {
                        applyTo(client.player.getCooldowns(), payload);
                    }
                }));
    }

    /** 把包里这些自定义物品的冷却同步到本地玩家的原版冷却表。 */
    private static void applyTo(ItemCooldowns cooldowns, CustomItemCooldownS2CPayload payload) {
        for (String id : payload.itemIds()) {
            Item key = CustomItemCooldownKeys.keyOf(id);
            if (key == null) {
                continue;
            }
            if (payload.ticks() > 0) {
                // 直接写原版那张表，不走 addCooldown，避免客户端再触发一次原版同步钩子。
                // CooldownInstance 的第二个参数是「冷却结束时的 tickCount」，
                // 原版 addCooldown 传的就是 tickCount + ticks，不能直接传 ticks。
                cooldowns.cooldowns.put(key, new ItemCooldowns.CooldownInstance(cooldowns.tickCount,
                        cooldowns.tickCount + payload.ticks()));
            } else {
                cooldowns.cooldowns.remove(key);
            }
        }
    }
}
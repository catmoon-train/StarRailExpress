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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 自定义列车物品的冷却同步（服务端 → 客户端）。
 *
 * <p>
 * 原版的 {@code ClientboundCooldownPacket} 只能按 {@code ResourceLocation} 传物品，
 * 而自定义物品的冷却键是「未注册的 {@link Item} 实例」（见 {@link CustomItemCooldownKeys}），
 * 所以这里自带一个按<b>自定义物品 id 字符串</b>同步的包。
 *
 * <p>
 * 客户端拿到 id 后解析出同一个冷却键，直接写进<b>原版</b> {@link ItemCooldowns} 的那张表，
 * 之后冷却的推进、过期清理、物品栏覆盖层全部由原版负责，模组不再另存一份冷却状态。
 *
 * @param itemIds 受影响的自定义物品 id 列表
 * @param ticks   冷却时长（tick）；{@code <= 0} 表示「立刻解除这些物品的冷却」
 */
public record CustomItemCooldownS2CPayload(List<String> itemIds, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CustomItemCooldownS2CPayload> ID = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "custom_item_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, CustomItemCooldownS2CPayload> CODEC = StreamCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.itemIds.size());
                for (String id : payload.itemIds) {
                    buf.writeUtf(id, 256);
                }
                buf.writeVarInt(payload.ticks);
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readUtf(256));
                }
                return new CustomItemCooldownS2CPayload(List.copyOf(ids), buf.readVarInt());
            });

    /** 单件物品进入冷却。 */
    public static CustomItemCooldownS2CPayload of(String itemId, int ticks) {
        return new CustomItemCooldownS2CPayload(List.of(itemId), ticks);
    }

    public static CustomItemCooldownS2CPayload of(Collection<String> itemIds, int ticks) {
        return new CustomItemCooldownS2CPayload(List.copyOf(itemIds), ticks);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 把这些冷却同步到本地玩家的原版冷却表（客户端调用）。 */
    public void applyTo(ItemCooldowns cooldowns) {
        if (itemIds.isEmpty() || cooldowns == null) {
            return;
        }
        for (String id : itemIds) {
            Item key = CustomItemCooldownKeys.keyOf(id);
            if (key == null) {
                continue;
            }
            if (ticks > 0) {
                // 直接写原版那张表：不走 addCooldown，避免客户端再触发一次同步钩子。
                // CooldownInstance 的第二个参数是「冷却结束时的 tickCount」，
                // 原版 addCooldown 传的就是 tickCount + ticks，不能直接传 ticks。
                cooldowns.cooldowns.put(key,
                        new ItemCooldowns.CooldownInstance(cooldowns.tickCount, cooldowns.tickCount + ticks));
            } else {
                cooldowns.cooldowns.remove(key);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.client().execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                payload.applyTo(client.player.getCooldowns());
            }
        }));
    }
}
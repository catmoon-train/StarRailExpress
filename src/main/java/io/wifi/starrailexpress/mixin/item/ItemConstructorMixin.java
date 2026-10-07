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

import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「自定义列车物品的专属冷却键」能在运行期被造出来。
 *
 * <p>
 * <b>问题</b>：{@link Item} 的构造函数第一件事就是
 * {@code BuiltInRegistries.ITEM.createIntrusiveHolder(this)}，把自己登记进物品注册表的
 * intrusive holder 表。Fabric 的 registry sync 会在 mod 初始化<b>之后</b>把
 * {@code BuiltInRegistries.ITEM} 换成 {@code SyncedRegistry} 包装，它不支持 intrusive holder，
 * 于是运行期任何 {@code new Item(...)} 都会抛
 * {@code IllegalStateException: This registry can't create intrusive holders}。
 * （mod 初始化阶段能 new，是因为那时 {@code BuiltInRegistries.ITEM} 还是原版
 * {@code MappedRegistry}。）
 *
 * <p><b>做法</b>：只在 {@link CustomItemCooldownKeys#isCreatingCooldownKey()} 为 true
 * （也就是正在造冷却键）时返回 {@code null}，跳过这一步。这些键：
 * <ul>
 * <li>只当 {@code ItemCooldowns} 那个 {@code Map} 的键用；</li>
 * <li>永远不会进物品栏、不会被序列化、拿不到默认实例；</li>
 * <li>用不到 {@code builtInRegistryHolder} 这个字段（它只在 {@code ItemStack}
 * 与注册表相关路径上被读，我们的键不走那些路径）。</li>
 * </ul>
 *
 * <p><b>影响范围</b>：开关默认 false，所以除自定义物品冷却键之外，
 * <b>所有</b>物品（包括其它模组在运行期 new 出来的物品）走的都是原版那条
 * {@code createIntrusiveHolder}，行为与改动前完全一致。
 */
@Mixin(Item.class)
public class ItemConstructorMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/world/item/Item$Properties;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/DefaultedRegistry;createIntrusiveHolder(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;"))
    @SuppressWarnings("unchecked")
    private Holder.Reference<Item> sre$skipIntrusiveHolderForCooldownKey(DefaultedRegistry<Object> registry, Object item) {
        if (CustomItemCooldownKeys.isCreatingCooldownKey()) {
            return null;
        }
        return (Holder.Reference<Item>) (Holder.Reference<?>) registry.createIntrusiveHolder(item);
    }
}
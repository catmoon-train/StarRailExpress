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

package io.wifi.starrailexpress.disguise;

import io.wifi.starrailexpress.morph.MorphApi;
import io.wifi.starrailexpress.morph.MorphAppearance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaState;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.role_data.innocence.LeatherPigRoleData;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;
import org.agmas.noellesroles.role_data.neutral.PhantomSpiritRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 跨系统的「这名玩家现在是否处于伪装状态」查询，服务端与客户端都能用。
 * <p>
 * 汇总的伪装来源与客户端 HUD（{@code io.wifi.starrailexpress.client.disguise.DisguiseStatusResolver}）
 * 一致，两边要一起改：
 * <ol>
 * <li>实体伪装（{@link EntityDisguise}）</li>
 * <li>职业形态：皮革噶的（猪）、{@code RABBIT_SHAPE} 修饰符（兔）、番茄头、幻灵（悦灵）、熊猫</li>
 * <li>皮肤变形（{@link MorphApi}）</li>
 * </ol>
 * 全部判定都走 common 侧的 RoleData / 组件 / 管理器，因此命令、服务端逻辑可以直接调用
 * （客户端渲染那条路径另有按 tick 打戳的缓存，见 {@code RoleDisguiseResolver}）。
 */
public final class DisguiseQuery {

    private DisguiseQuery() {
    }

    /** 是否处于任何形式的伪装状态。 */
    public static boolean isDisguised(Player player) {
        if (player == null) {
            return false;
        }
        if (EntityDisguise.isDisguised(player)) {
            return true;
        }
        if (LeatherPigRoleData.isDisguised(player)
                || TomatoHeadRoleData.isTomatoForm(player)
                || PhantomSpiritRoleData.isDisguised(player)
                || PandaState.isPanda(player)
                || RoleUtils.isPlayerTheModifier(player, NRModifiers.RABBIT_SHAPE)) {
            return true;
        }
        return !MorphApi.getAppearance(player).isNone();
    }

    /**
     * 是否正伪装成指定实体类型。
     * <p>
     * 只针对**实体伪装**这一个来源——职业形态（猪 / 兔 / ……）与皮肤变形都没有「实体类型」这个量，
     * 要判断「是否处于任意伪装」用 {@link #isDisguised(Player)}。
     */
    public static boolean isDisguisedAs(@Nullable Player player, @Nullable EntityType<?> type) {
        if (player == null || type == null) {
            return false;
        }
        EntityDisguiseState state = EntityDisguise.get(player);
        // 实体类型是注册表单例，直接比引用。
        return !state.isNone() && state.type() == type;
    }

    /**
     * 是否处于「带指定 NBT」的伪装：给定 NBT 是伪装外观 NBT 的**子集**即算命中，
     * 与 {@code /execute if entity @e[nbt={...}]} 同语义——所以可以只写关心的那几个键，
     * 不必把整份 NBT 抄全。
     * <p>
     * 例：伪装 NBT 是 {@code {Color:3b, Sheared:1b}}，下面两个都命中：
     * <pre>
     * DisguiseQuery.isDisguisedWithNbt(player, {Color:3b})
     * DisguiseQuery.isDisguisedWithNbt(player, {Color:3b, Sheared:1b})
     * </pre>
     * 传空标签 {@code {}} 等价于「处于实体伪装」；目标没有伪装、或伪装不带任何 NBT 时，
     * 只有空标签才命中。
     * <p>
     * 同样只针对实体伪装——NBT 是它的概念。指令侧的等价物是原版 {@code if data} 的路径判断
     * （{@code /execute if data sre:disguise <player> <path>}，见 {@code DisguiseDataProvider}），
     * 那是「路径是否存在」而不是子集匹配，别混用。
     */
    public static boolean isDisguisedWithNbt(@Nullable Player player, @Nullable CompoundTag nbt) {
        if (player == null || nbt == null) {
            return false;
        }
        EntityDisguiseState state = EntityDisguise.get(player);
        if (state.isNone()) {
            return false;
        }
        CompoundTag actual = state.nbt();
        if (actual == null) {
            // 没有外观 NBT（等价于空标签），只有空测试标签才匹配。
            return nbt.isEmpty();
        }
        // 参数顺序：(测试标签, 实际标签, 允许部分匹配)。
        return NbtUtils.compareNbt(nbt, actual, true);
    }

    /**
     * 是否处于变形状态（{@code MorphApi}，任意形态）。
     * <p>
     * 与 {@link #isDisguised(Player)} 的区别：那个是「所有伪装来源」的总和（含职业形态与实体伪装），
     * 这个是**只看变形**。
     */
    public static boolean isMorphed(@Nullable Player player) {
        return player != null && !MorphApi.getAppearance(player).isNone();
    }

    /** 是否正变形为指定玩家（复制其皮肤 / 帽子 / 名牌 / 玩偶）。 */
    public static boolean isMorphedAsPlayer(@Nullable Player player, @Nullable Player target) {
        if (player == null || target == null) {
            return false;
        }
        MorphAppearance appearance = MorphApi.getAppearance(player);
        return appearance.isPlayer() && target.getUUID().equals(appearance.targetPlayer());
    }

    /** 是否正变形成指定贴图（忽略 slim / wide 的区别）。 */
    public static boolean isMorphedAsTexture(@Nullable Player player, @Nullable ResourceLocation texture) {
        if (player == null || texture == null) {
            return false;
        }
        MorphAppearance appearance = MorphApi.getAppearance(player);
        return appearance.isTexture() && texture.equals(appearance.texture());
    }
}

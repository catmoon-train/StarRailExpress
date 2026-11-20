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

package io.wifi.starrailexpress.client.ponder;

import com.mojang.authlib.GameProfile;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.element.ElementLinkImpl;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 在思索场景里创建「职业演示用玩家模型」。
 *
 * <p>Ponderer 自带的 {@code create_entity} 只能创建使用观看者皮肤的玩家模型，
 * 无法指定狂暴皮肤，也不会画本能透视边框。这里把两件事合并到同一个场景元素上：
 *
 * <ul>
 *   <li>{@link Builder#skin(ResourceLocation)} 指定皮肤贴图（例如小丑狂暴的
 *       {@code starrailexpress:textures/entity/custom_psycho/jester.png}）；</li>
 *   <li>{@link Builder#outline(int)} 指定本能透视边框颜色，颜色语义与游戏内
 *       {@code InstinctManager} 返回的值一致。</li>
 * </ul>
 *
 * <p>两者都不设置时，元素退化为普通玩家模型，可直接替换 JSON 的
 * {@code create_entity}/{@code minecraft:player}。
 */
public final class PonderPlayerPreset {

    /** 表示「不画本能透视边框」的哨兵值。 */
    public static final int NO_OUTLINE = Integer.MIN_VALUE;

    private PonderPlayerPreset() {
    }

    /** 开始配置一个玩家模型；{@code location} 与 JSON {@code pos} 使用同一坐标系。 */
    public static Builder at(Vec3 location) {
        return new Builder(location);
    }

    /** 开始配置一个玩家模型，坐标直接写成 {@code [x, y, z]}。 */
    public static Builder at(double x, double y, double z) {
        return new Builder(new Vec3(x, y, z));
    }

    public static final class Builder {
        private final Vec3 location;
        private float bodyYaw = 180.0F;
        private float headYaw = 180.0F;
        private float pitch = 0.0F;
        @Nullable
        private GameProfile profile;
        @Nullable
        private ResourceLocation skin;
        @Nullable
        private ItemStack heldItem;
        private int outlineColor = NO_OUTLINE;
        private float outlineLineWidth = 1.0F;
        private float outlineExpand = 0.12F;

        private Builder(Vec3 location) {
            this.location = location;
        }

        /** 模型整体朝向；{@code bodyYaw} 与 {@code headYaw} 相同时为正面站立。 */
        public Builder facing(float yaw) {
            this.bodyYaw = yaw;
            this.headYaw = yaw;
            return this;
        }

        public Builder facing(float bodyYaw, float headYaw, float pitch) {
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.pitch = pitch;
            return this;
        }

        /** 使用指定身份（皮肤来源）；缺省为观看者本人。 */
        public Builder profile(@Nullable GameProfile profile) {
            this.profile = profile;
            return this;
        }

        /** 强制使用指定皮肤贴图，优先于身份皮肤。 */
        public Builder skin(@Nullable ResourceLocation skin) {
            this.skin = skin;
            return this;
        }

        /** 让模型右手持有指定物品，用于演示换武器 / 狂暴形态。 */
        public Builder heldItem(@Nullable ItemStack heldItem) {
            this.heldItem = heldItem;
            return this;
        }

        /** 本能透视边框颜色；传 {@link #NO_OUTLINE} 表示不画边框。 */
        public Builder outline(int argb) {
            this.outlineColor = argb;
            return this;
        }

        /** 边框线宽（GUI 像素）；默认 1.0。 */
        public Builder outlineLineWidth(float lineWidth) {
            this.outlineLineWidth = Math.max(0.1F, lineWidth);
            return this;
        }

        /** 边框相对模型碰撞箱的外扩量（格）；默认 0.12，用于包住整个模型。 */
        public Builder outlineExpand(float expand) {
            this.outlineExpand = Math.max(0.0F, expand);
            return this;
        }

        /** 把玩家模型加入场景，返回可用于后续淡出/移动的链接。 */
        public ElementLink<PonderPlayerElement> addTo(SceneBuilder scene) {
            PonderPlayerElement element = new PonderPlayerElement(location)
                    .profile(profile)
                    .skin(skin)
                    .heldItem(heldItem)
                    .outline(outlineColor, outlineLineWidth, outlineExpand)
                    .facing(bodyYaw, headYaw, pitch);
            ElementLink<PonderPlayerElement> link = new ElementLinkImpl<>(PonderPlayerElement.class);
            scene.addInstruction(ps -> {
                element.bindOutliner(ps.getOutliner());
                ps.addElement(element);
                ps.linkElement(element, link);
                element.setVisible(true);
                element.forceApplyFade(1.0F);
            });
            return link;
        }
    }

    /** 淡出并移除玩家模型；{@code fadeOutTicks} 为 0 时立即移除。 */
    public static void remove(SceneBuilder scene, ElementLink<PonderPlayerElement> link, int fadeOutTicks) {
        if (fadeOutTicks <= 0) {
            scene.addInstruction(ps -> {
                PonderPlayerElement element = ps.resolve(link);
                if (element != null) {
                    element.setVisible(false);
                }
            });
            return;
        }
        scene.addInstruction(new FadeOutOfSceneInstruction<>(fadeOutTicks, Direction.DOWN, link));
    }
}

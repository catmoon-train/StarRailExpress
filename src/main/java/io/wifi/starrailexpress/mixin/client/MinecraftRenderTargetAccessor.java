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

package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 允许在「监控台小窗」离屏渲染的极短窗口内临时替换主渲染目标。
 *
 * <p>原版字段是 {@code private final}，{@link Mutable} 让生成的 setter 可以写入它；
 * setter 合入 {@link Minecraft} 类内部，因此不属于外部类对 final 字段的非法写入。
 * 每次替换后都会立刻还原，主画面与 GUI 阶段看到的仍是原来的 RenderTarget。
 */
@Mixin(Minecraft.class)
public interface MinecraftRenderTargetAccessor {

    @Accessor("mainRenderTarget")
    @Mutable
    void sre$setMainRenderTarget(RenderTarget target);
}

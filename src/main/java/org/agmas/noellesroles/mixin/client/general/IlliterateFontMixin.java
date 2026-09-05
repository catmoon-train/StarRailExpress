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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.IlliterateTextClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 每个字形都会进 {@code accept}，热路径必须极轻：
 * 用 {@code @ModifyVariable} 只改码点，避免 WrapMethod/Inject 的额外调用与 CallbackInfo。
 * 未乱码时只读一个静态布尔后立刻返回。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public class IlliterateFontMixin {
    @ModifyVariable(
            method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private int noe$scrambleIlliterateText(int codePoint, int index, Style style) {
        if (!IlliterateTextClientHandle.scramble) {
            return codePoint;
        }
        return IlliterateTextClientHandle.scrambleCodePoint(index, codePoint);
    }
}

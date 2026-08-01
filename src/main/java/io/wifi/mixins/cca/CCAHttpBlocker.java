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

package io.wifi.mixins.cca;

import com.google.gson.JsonElement;
import dev.upcraft.datasync.web.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.util.function.UnaryOperator;

// 未经告知的信息收集是可耻的行为
@Mixin(value = HttpUtil.class, remap = false)
public class CCAHttpBlocker {

    /** 阻止 POST（不需要响应体）*/
    @Inject(method = "postJsonRequest", at = @At("HEAD"), cancellable = true)
    private static void blockPostJsonRequest(
            URI uri, JsonElement json,
            UnaryOperator<java.net.http.HttpRequest.Builder> extraProperties,  // 实际类型见下
            CallbackInfo ci) {
        ci.cancel();
    }

    /** 阻止 POST（需要响应体），返回空 JsonObject */
    @Inject(method = "postJson", at = @At("HEAD"), cancellable = true)
    private static void blockPostJson(URI uri, JsonElement json,
            CallbackInfoReturnable<JsonElement> cir) {
        cir.setReturnValue(new com.google.gson.JsonObject());
    }

    /** 阻止 GET/通用请求，返回 null（原方法本身可返回 null）*/
    @Inject(method = "makeJsonRequest", at = @At("HEAD"), cancellable = true)
    private static void blockMakeJsonRequest(
            java.net.http.HttpRequest.Builder requestBuilder,
            CallbackInfoReturnable<JsonElement> cir) {
        cir.setReturnValue(null);
    }
}
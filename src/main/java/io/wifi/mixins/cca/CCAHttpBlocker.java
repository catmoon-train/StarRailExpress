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
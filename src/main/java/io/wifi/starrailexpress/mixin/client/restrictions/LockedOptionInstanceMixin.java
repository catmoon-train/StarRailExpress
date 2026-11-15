package io.wifi.starrailexpress.mixin.client.restrictions;

import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.client.ClientOptionPolicy;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** 设置文件、原版界面、Sodium 绑定与运行读取共用同一约束，不能只禁用按钮。 */
@Mixin(OptionInstance.class)
public abstract class LockedOptionInstanceMixin<T> {
    @Shadow T value;
    @Unique private T sre$lockedValue;
    @Unique private boolean sre$isGamma;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/client/OptionInstance$TooltipSupplier;Lnet/minecraft/client/OptionInstance$CaptionBasedToString;Lnet/minecraft/client/OptionInstance$ValueSet;Lcom/mojang/serialization/Codec;Ljava/lang/Object;Ljava/util/function/Consumer;)V",
            at = @At("TAIL"))
    private void sre$identifyLockedOption(String key, OptionInstance.TooltipSupplier<T> tooltip,
                                          OptionInstance.CaptionBasedToString<T> caption,
                                          @Coerce Object values, Codec<T> codec,
                                          T initial, Consumer<T> onChange, CallbackInfo ci) {
        sre$isGamma = "options.gamma".equals(key);
        sre$lockedValue = (T) ClientOptionPolicy.lockedValue(key, false);
        if (sre$lockedValue != null) {
            value = sre$lockedValue;
        }
    }

    @ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true)
    private T sre$rejectLockedValueChanges(T proposed) {
        T locked = sre$currentLockedValue();
        return locked != null ? locked : proposed;
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void sre$enforceLockedRead(CallbackInfoReturnable<T> cir) {
        T locked = sre$currentLockedValue();
        if (locked != null) {
            value = locked;
            cir.setReturnValue(locked);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private T sre$currentLockedValue() {
        return sre$isGamma ? (T) ClientOptionPolicy.lockedValue("options.gamma") : sre$lockedValue;
    }

    @Inject(method = "createButton(Lnet/minecraft/client/Options;IIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/AbstractWidget;",
            at = @At("RETURN"))
    private void sre$disableLockedControl(CallbackInfoReturnable<AbstractWidget> cir) {
        if (sre$lockedValue != null) {
            cir.getReturnValue().active = false;
        }
    }
}

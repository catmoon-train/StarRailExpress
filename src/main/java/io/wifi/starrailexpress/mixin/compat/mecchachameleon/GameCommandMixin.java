package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.command.GameCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /meccha} 是玩家自行开房、开局、改配置的唯一入口。房间由 SRE 的变色龙模式
 * 全权创建，因此这条命令永远不注册——其余时刻也就无从使用变色龙自带的玩法。
 */
@Mixin(value = GameCommand.class, remap = false)
public abstract class GameCommandMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void sre$disableChameleonCommand(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        ci.cancel();
    }
}

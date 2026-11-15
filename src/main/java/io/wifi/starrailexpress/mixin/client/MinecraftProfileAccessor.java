package io.wifi.starrailexpress.mixin.client;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** 非阻塞读取登录账号档案，避免 Minecraft.getGameProfile() 内部的 join。 */
@Mixin(Minecraft.class)
public interface MinecraftProfileAccessor {
    @Accessor("profileFuture")
    CompletableFuture<ProfileResult> sre$getProfileFuture();
}

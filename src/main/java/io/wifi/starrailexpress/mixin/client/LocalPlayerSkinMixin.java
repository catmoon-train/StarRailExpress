package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.util.LocalPlayerSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 仅修复本人默认皮肤回退，不干预其他玩家、主播模式或实体层的职业换肤。 */
@Mixin(PlayerInfo.class)
public abstract class LocalPlayerSkinMixin {
    @Unique private static final LocalPlayerSkinCache sre$localSkin = new LocalPlayerSkinCache();

    @Shadow public abstract GameProfile getProfile();

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin sre$restoreOwnSkin(PlayerSkin original) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.getUUID().equals(getProfile().getId())
                || SREClientConfig.instance().enableRandomSkinForStreaming) {
            return original;
        }
        return sre$localSkin.resolve(client.getUser().getProfileId(), original,
                () -> sre$loadAccountSkin(client));
    }

    @Unique
    private static CompletableFuture<PlayerSkin> sre$loadAccountSkin(Minecraft client) {
        CompletableFuture<ProfileResult> profileFuture = ((MinecraftProfileAccessor) client).sre$getProfileFuture();
        try {
            ProfileResult result = profileFuture.getNow(null);
            if (result == null || !client.getUser().getProfileId().equals(result.profile().getId())
                    || !result.profile().getProperties().containsKey("textures")) {
                return null;
            }
            // 仍走皮肤管理器的正常加载与验证，不改服务器档案或其 textures。
            return client.getSkinManager().getOrLoad(result.profile());
        } catch (CompletionException | CancellationException ignored) {
            return null;
        }
    }
}

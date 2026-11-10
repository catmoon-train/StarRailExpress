package io.wifi.starrailexpress.mixin.compat.lightspeed;

import io.wifi.srepackcompat.PackArchiveFileUri;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

/** Keep Eating Animation's builtin pack registration intact while fixing Lightspeed's archive URI. */
@Pseudo
@Mixin(targets = "io.wifi.starrailexpress.client.resource.ArchiveFileUri", remap = false)
public abstract class ArchiveFileUriMixin {
    @Inject(method = "resolve(Ljava/net/URI;Ljava/net/URI;)Ljava/net/URI;",
            at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private static void srepack$asciiFileUri(URI root, URI fallback, CallbackInfoReturnable<URI> cir) {
        URI original = cir.getReturnValue();
        URI normalized = PackArchiveFileUri.normalizeFileUri(original);
        if (normalized != original) cir.setReturnValue(normalized);
    }
}

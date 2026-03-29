package pro.fazeclan.river.stupid_express.mixin.client.modifier.split_personality;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        final var instance = Minecraft.getInstance();
        if (instance == null) {
            return false;
        }
        final var player = instance.player;
        if (player == null) {
            return false;
        }
        final var options = instance.options;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && player.isSpectator()
                && WorldModifierComponent.KEY.get(player.level()).isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {

            final var splitPersonalityComponent = SplitPersonalityComponent.KEY.get(player);
            if (splitPersonalityComponent == null || splitPersonalityComponent.getMainPersonality() == null
                    || splitPersonalityComponent.getSecondPersonality() == null)
                return false;
            if (splitPersonalityComponent.getTemporaryRevivalStartTick() > 0)
                return false;
            if (splitPersonalityComponent.isCurrentlyActive())
                return false;
            return this.same(options.keySwapOffhand) ||
                    this.same(options.keyJump) ||
                    this.same(options.keyDrop) ||
                    this.same(options.keyUp) ||
                    this.same(options.keyRight) ||
                    this.same(options.keyPlayerList) ||
                    this.same(options.keyLeft) ||
                    this.same(options.keyDown) ||
                    this.same(options.keyUse) ||
                    this.same(options.keyShift) ||
                    this.same(options.keyAdvancements);

        }
        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        return !this.shouldSuppressKey() && original;
    }
}

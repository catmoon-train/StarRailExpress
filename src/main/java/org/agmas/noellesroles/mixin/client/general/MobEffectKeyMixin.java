package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class MobEffectKeyMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        final var instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        final LocalPlayer player = instance.player;
        if (player == null)
            return false;
        final var options = instance.options;
        if (player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.OTHERWORLD_AURA) || player.hasEffect(ModEffects.TAROT_ASSEMBLY)
                || player.hasEffect(ModEffects.GHOST_CURSE)) {
            if (this.same(NoellesrolesClient.abilityBind)) {
                return true;
            }
        }
        if (player.hasEffect(ModEffects.MOVE_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)) {
            if (this.same(options.keyJump) || this.same(options.keyLeft) || this.same(options.keyRight)
                    || this.same(options.keyUp) || this.same(options.keyShift) || this.same(options.keyDown))
                return true;
        }

        if (player.hasEffect(ModEffects.TAROT_ASSEMBLY) || player.hasEffect(ModEffects.INVENTORY_BANED)){
            if (this.same(options.keyInventory))return true;
        }
        if (player.hasEffect(ModEffects.USED_BANED) || player.hasEffect(ModEffects.GHOST_CURSE) || player.hasEffect(ModEffects.TAROT_ASSEMBLY)) {
            if (this.same(options.keyAttack) || this.same(options.keyDrop))
                return true;
            if (this.same(options.keyUse)) {
                // 怀旧者在里世界中「只能潜行与侦察」：放行对方块的使用键（按按钮、用钥匙开门），
                // 仅在准星指向方块时放行；指向空气/实体时仍抑制使用物品（开枪、消耗品等）。
                if (player.hasEffect(ModEffects.NOSTALGIST_BACKWORLD) && isLookingAtBlock(instance)) {
                    return false;
                }
                // 60s 做客：访客带 USED_BANED，但离开/对话都要在避难所门上操作——准星指向避难所门时放行
                if (isLookingAtShelterDoor(instance)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean isLookingAtBlock(Minecraft instance) {
        final HitResult hit = instance.hitResult;
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    @Unique
    private boolean isLookingAtShelterDoor(Minecraft instance) {
        return instance.level != null
                && instance.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && instance.hitResult.getType() == HitResult.Type.BLOCK
                && instance.level.getBlockState(blockHit.getBlockPos())
                        .getBlock() instanceof net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
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

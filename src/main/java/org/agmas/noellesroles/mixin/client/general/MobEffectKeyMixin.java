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
        if (player.isCreative())
            return false;
        final var options = instance.options;
        if (player.isSpectator()) {
            if (this.same(options.keySwapOffhand)) {
                return true;
            }
        }
        if (player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.OTHERWORLD_AURA)
                || player.hasEffect(ModEffects.TAROT_ASSEMBLY)
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

        if (player.hasEffect(ModEffects.TAROT_ASSEMBLY) || player.hasEffect(ModEffects.INVENTORY_BANED)) {
            if (this.same(options.keyInventory))
                return true;
            if (player.hasEffect(ModEffects.INVENTORY_BANED)) {
                for (KeyMapping hotbar : options.keyHotbarSlots) {
                    if (this.same(hotbar))
                        return true;
                }
                if (this.same(options.keySwapOffhand) || this.same(options.keyPickItem))
                    return true;
            }
        }
        if (player.hasEffect(ModEffects.TURN_BANED) && this.same(options.keyTogglePerspective))
            return true;
        if (player.hasEffect(ModEffects.USED_BANED) || player.hasEffect(ModEffects.GHOST_CURSE)
                || player.hasEffect(ModEffects.TAROT_ASSEMBLY)) {
            if (this.same(options.keyAttack) || this.same(options.keyDrop))
                return true;
            if (this.same(options.keyUse)) {
                // 里世界（怀旧者）/ 维度（冤魂）：仅准星指向方块时放行右键（按按钮、用钥匙开门等），
                // 指向空气/实体时仍抑制使用物品（开枪、消耗品等）。显形时 USED_BANED 已被清除，不会进入此分支。
                if (isLookingAtBlock(instance)
                        && (player.hasEffect(ModEffects.NOSTALGIST_BACKWORLD)
                            || player.hasEffect(ModEffects.WRAITH_DIMENSION))) {
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

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        return original && !this.shouldSuppressKey();
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        return original && !this.shouldSuppressKey();
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        return original && !this.shouldSuppressKey();
    }
}

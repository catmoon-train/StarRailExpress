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

package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    public boolean tmm$hasInstinctOutline(boolean original, @Local(argsOnly = true) Entity entity) {
        if (SRE.isLobby)
            return original;
        var color = SREClient.getCachedInstinctHighlight(entity);
        if (!color.isEmpty()) {
            return true;
        }
        return original;
    }

    @WrapWithCondition(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;itemUsed(Lnet/minecraft/world/InteractionHand;)V"))
    private boolean tmm$cancelRevolverUpdateAnimation(ItemInHandRenderer instance, InteractionHand hand) {
        if (SRE.isLobby)
            return true;
        return !Minecraft.getInstance().player.getItemInHand(hand).is(TMMItemTags.HELD_LIKE_GUNS_ITEMS);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"))
    private void tmm$invalid(@NotNull Inventory instance, int value, Operation<Void> original) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(instance.player.level());
        if (gameComponent.gameStatus == SREGameWorldComponent.GameStatus.ACTIVE) {
            original.call(instance, value);
            return;
        }
        int oldSlot = instance.selected;
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(instance.player);

        if (component.getPsychoTicks() > 0) {

            Item psychoItem = TMMItems.BAT;
            SRERole role = SREClient.gameComponent.getRole(player);
            if (role != null) {
                psychoItem = role.getPsychoItem();
            }
            if ((instance.getItem(oldSlot).is(psychoItem)) &&
                    (!instance.getItem(value).is(psychoItem)))
                return;
        }
        original.call(instance, value);

    }
}

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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 「自定义手铐·是否限制玩家行为」为是时，屏蔽手铐同款按键。
 *
 * <p>
 * 与 {@code org.agmas.noellesroles.mixin.client.HandCuffsKeyBindingMixin}（原版手铐）完全对应，
 * 只是判定条件换成「被自定义手铐铐住且该手铐勾选了限制行为」，两者互不影响。
 */
@Mixin(KeyMapping.class)
public abstract class CustomCuffKeyBindingMixin {

    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean sre$cuffRestricted() {
        if (SRE.isLobby) {
            return false;
        }
        Minecraft instance = Minecraft.getInstance();
        if (instance == null || instance.player == null) {
            return false;
        }
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
            return false;
        }
        if (!SREClient.isPlayerAliveAndInSurvival()) {
            return false;
        }
        CustomItemData data = CustomItemRuntime.getCuffData(instance.player);
        if (data == null || !data.cuffRestrict) {
            return false;
        }
        var options = instance.options;
        return this.same(options.keySwapOffhand)
                || this.same(options.keyJump)
                || this.same(options.keyTogglePerspective)
                || this.same(options.keyDrop)
                || this.same(options.keyAttack)
                || this.same(options.keyUse)
                || this.same(options.keyAdvancements);
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean sre$restrainConsumeClick(boolean original) {
        return original && !sre$cuffRestricted();
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean sre$restrainIsDown(boolean original) {
        return original && !sre$cuffRestricted();
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean sre$restrainMatches(boolean original) {
        return original && !sre$cuffRestricted();
    }
}

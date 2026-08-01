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

package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents;
import org.agmas.noellesroles.game.roles.innocence.photographer.SrePhotographerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 exposure 照片框实体增加：
 * <ul>
 *   <li>"是否摄影师放置"的标记（含 NBT 持久化），供开局只清理摄影师放置的画框。</li>
 *   <li>tick 中检测穿过画框的玩家并触发传送（见 {@link PhotographerFrameEvents}）。</li>
 * </ul>
 */
@Mixin(PhotographFrameEntity.class)
public class PhotographFrameEntityMixin implements SrePhotographerFrame {

    @Unique
    private boolean sre$photographerPlaced = false;

    @Unique
    private int sre$teleportCount = 0;

    @Override
    public boolean sre$isPhotographerPlaced() {
        return this.sre$photographerPlaced;
    }

    @Override
    public void sre$setPhotographerPlaced(boolean placed) {
        this.sre$photographerPlaced = placed;
    }

    @Override
    public int sre$getTeleportCount() {
        return this.sre$teleportCount;
    }

    @Override
    public void sre$setTeleportCount(int count) {
        this.sre$teleportCount = count;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sre$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("SrePhotographerPlaced", this.sre$photographerPlaced);
        tag.putInt("SrePhotographerTeleportCount", this.sre$teleportCount);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sre$load(CompoundTag tag, CallbackInfo ci) {
        this.sre$photographerPlaced = tag.getBoolean("SrePhotographerPlaced");
        this.sre$teleportCount = tag.getInt("SrePhotographerTeleportCount");
    }

    // 画框传送改为玩家主动右键触发（见 PhotographerFrameEvents 的 UseEntityCallback），
    // 不再在 tick 中扫描穿过画框的玩家自动传送。
}

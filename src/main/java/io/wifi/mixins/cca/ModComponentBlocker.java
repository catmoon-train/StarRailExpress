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

package io.wifi.mixins.cca;

import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "dev.doctor4t.wathe.cca.WatheComponents", remap = false)
public class ModComponentBlocker {
    // Caused by:
    // org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException:
    // @Overwrite method registerWorldComponentFactories in
    // wathe_blocker.mixins.json:cca.ModComponentBlocker from mod starrailexpress
    // was not located in the target class dev.doctor4t.wathe.cca.WatheComponents.
    // No refMap loaded.
    /**
     * @author io.wifi
     * @reason 阻止 CCA 注册 wathe 的 ComponentKey（因为 <clinit> 已被清空，type 为 null 会崩溃）
     */
    @Overwrite(remap = false)
    public void registerWorldComponentFactories(
            WorldComponentFactoryRegistry registry) {
        // 空实现
    }

    @Overwrite(remap = false)
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // 空实现
    }

    @Overwrite(remap = false)
    public void registerScoreboardComponentFactories(ScoreboardComponentFactoryRegistry registry) {

    }
}
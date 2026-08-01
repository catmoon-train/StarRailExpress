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

package pro.fazeclan.river.stupid_express.constants;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.arsonist.effect.BurningEffect;

public class SEEffects {

    /**
     * 燃烧效果：纵火犯点燃目标后，目标持续着火，燃烧结束时被烧死。
     */
    public static final Holder<MobEffect> BURNING = register("arsonist_burning", new BurningEffect());

    private static Holder<MobEffect> register(String id, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, StupidExpress.id(id), effect);
    }

    /**
     * 触发类加载，确保上述静态字段在 mod 初始化阶段完成注册（注册表冻结前）。
     */
    public static void init() {
    }
}

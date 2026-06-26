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

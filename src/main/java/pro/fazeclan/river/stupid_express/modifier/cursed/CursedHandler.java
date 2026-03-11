package pro.fazeclan.river.stupid_express.modifier.cursed;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class CursedHandler {
    private static int tickCounter = 0;
    private static final int INTERVAL = 30 * 20; // 30 seconds

    private static final List<Holder<MobEffect>> POSSIBLE_EFFECTS = Arrays.asList(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DIG_SPEED,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.CONFUSION,
            MobEffects.INVISIBILITY,
            MobEffects.BLINDNESS,
            MobEffects.NIGHT_VISION,
            MobEffects.LUCK,
            MobEffects.UNLUCK,
            MobEffects.GLOWING,
            MobEffects.SLOW_FALLING,
            MobEffects.DARKNESS,
            MobEffects.WEAKNESS
    );

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= INTERVAL) {
                tickCounter = 0;
                
                Random random = new Random();

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    CursedComponent component = CursedComponent.KEY.get(player);
                    if (component.isCursed()) {
                        Holder<MobEffect> effect = POSSIBLE_EFFECTS.get(random.nextInt(POSSIBLE_EFFECTS.size()));
                        // 5 seconds = 100 ticks, amplifier 1 = level 2
                        player.addEffect(new MobEffectInstance(effect, 100, 1));
                    }
                }
            }
        });
    }
}
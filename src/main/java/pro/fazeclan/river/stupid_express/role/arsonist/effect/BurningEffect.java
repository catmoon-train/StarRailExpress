package pro.fazeclan.river.stupid_express.role.arsonist.effect;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEEffects;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.util.UUID;

/**
 * 燃烧效果（纵火犯点燃）
 *
 * <ul>
 * <li>有害效果，火焰橙色。</li>
 * <li>持续期间每 tick 刷新目标的着火状态，目标身上一直冒火（视觉，会同步给所有客户端）。</li>
 * <li>点燃时一并施加隐藏的原版“防火”效果，使目标在燃烧期间不受原版火焰伤害，
 * 死亡时机完全由本效果掌控，而不会被火焰伤害提前烧死。</li>
 * <li>效果走到最后一 tick 时，目标被烧死，死亡原因为 {@code stupid_express:ignited}，
 * 击杀者归属为点燃他的纵火犯（通过 {@link DousedPlayerComponent#getBurningKiller()} 记录）。</li>
 * </ul>
 */
public class BurningEffect extends MobEffect {

    public BurningEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6A00);
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel)) {
            return super.applyEffectTick(entity, amplifier);
        }
        // 持续刷新着火状态（视觉）；实际死亡由本效果在结束时统一结算
        entity.setRemainingFireTicks(40);

        if (entity instanceof ServerPlayer victim) {
            MobEffectInstance instance = victim.getEffect(SEEffects.BURNING);
            // duration 在本 tick 的 applyEffectTick 之后才递减，==1 即为最后一 tick
            if (instance != null && instance.getDuration() <= 1) {
                scheduleBurnDeath(victim);
            }
        }
        return super.applyEffectTick(entity, amplifier);
    }

    /**
     * 把烧死延迟到本 tick 结束后再执行：此刻仍在遍历目标的效果列表，直接 killPlayer
     * 会在死亡时清空效果列表，造成对效果列表的并发修改。改用 server.execute 推迟即可避开。
     */
    private void scheduleBurnDeath(ServerPlayer victim) {
        if (victim.getServer() == null) {
            return;
        }
        DousedPlayerComponent doused = DousedPlayerComponent.KEY.get(victim);
        UUID killerId = doused.getBurningKiller();
        doused.setBurningKiller(null);
        victim.getServer().execute(() -> {
            if (!victim.isAlive()) {
                return;
            }
            ServerPlayer killer = killerId != null && victim.getServer() != null
                    ? victim.getServer().getPlayerList().getPlayer(killerId)
                    : null;
            victim.setRemainingFireTicks(0);
            GameUtils.killPlayer(victim, true, killer, StupidExpress.id("ignited"));
        });
    }
}

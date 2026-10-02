package org.agmas.noellesroles.katana;

import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.KatanaItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.content.item.RiotShieldHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 武士刀的右键格挡处理器。
 *
 * <p>死亡拦截与防暴盾牌走同一个 {@link AllowPlayerDeathWithKiller} 事件；
 * 「可格挡的死亡原因」<b>直接复用</b> {@link RiotShieldHandler#isBlockableDeathReason}
 * 的白名单——修改防暴盾牌的可格挡死亡原因时武士刀会一起变化。
 *
 * <p>格挡时序：
 * <ul>
 * <li>前摇 0.4 秒（衔接招式命中后释放格挡时前摇为 0）；</li>
 * <li>有效时间 1.2 秒；</li>
 * <li>未衔接招式的格挡结束后进入 5 秒内置冷却；</li>
 * <li>格挡成功随机播放 sword_parry_1 / sword_parry_2，并消耗 1 点耐久
 * （耐久不会低于 1，耐久等于 1 时无法格挡）。</li>
 * </ul>
 */
public final class KatanaHandler {

    private KatanaHandler() {
    }

    public static void register() {
        AllowPlayerDeathWithKiller.EVENT.register(KatanaHandler::allowDeath);
    }

    /**
     * 死亡拦截：受害者正处于格挡有效窗口内且死因可被格挡时，取消死亡。
     *
     * @return true 允许死亡 / false 已被格挡（取消死亡）
     */
    public static boolean allowDeath(Player victim, Player attacker, ResourceLocation deathReason) {
        if (attacker == null) {
            return true;
        }
        if (attacker.isSpectator() || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victim)) {
            return true;
        }
        // 必须正在使用武士刀格挡
        if (!victim.isUsingItem()) {
            return true;
        }
        ItemStack stack = victim.getUseItem();
        if (stack == null || !stack.is(ModItems.KATANA)) {
            return true;
        }
        // 可格挡的死亡原因同防暴盾牌（共用白名单）
        if (!RiotShieldHandler.isBlockableDeathReason(deathReason)) {
            return true;
        }

        KatanaState.PlayerState state = KatanaState.get(victim);
        int ticksUsing = victim.getTicksUsingItem();
        // 前摇：普通格挡 0.4 秒；衔接招式命中后的格挡为 0
        int windup = state.blockLinked ? 0 : KatanaState.BLOCK_WINDUP_TICKS;
        if (ticksUsing < windup || ticksUsing >= KatanaState.BLOCK_USE_DURATION) {
            // 前摇未完成，或已超出 1.2 秒有效时间
            return true;
        }
        // 耐久等于 1 时无论如何无法格挡（正常情况下 use 阶段已拦截，这里双保险）
        if (stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            return true;
        }

        // ── 格挡成功 ──
        SoundEvent parrySound = ThreadLocalRandom.current().nextBoolean()
                ? NRSounds.KATANA_PARRY_1
                : NRSounds.KATANA_PARRY_2;
        victim.level().playSound(null, victim.blockPosition(), parrySound, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 消耗 1 点耐久，但耐久不会降至低于 1
        if (!victim.isCreative() && stack.getMaxDamage() > 0
                && stack.getDamageValue() < stack.getMaxDamage() - 1) {
            stack.setDamageValue(stack.getDamageValue() + 1);
            if (victim instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
        }
        return false;
    }
}

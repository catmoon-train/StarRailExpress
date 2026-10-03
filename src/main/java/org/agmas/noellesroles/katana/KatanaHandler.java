package org.agmas.noellesroles.katana;

import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * <li>格挡成功随机播放 sword_parry_1 / sword_parry_2，<b>每次格挡消耗 1 点耐久</b>
 * （耐久不会低于 1，耐久等于 1 时无法格挡）；</li>
 * <li><b>只在格挡有效窗口内生效</b>：窗口内没有格挡次数上限，挡几次完全由耐久决定；
 * 窗口外完全没有防御。</li>
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
     * <p>可格挡范围与防暴盾牌完全一致（共用白名单），因此也包含走原版攻击路线的
     * Dream 铁斧 / 钻石剑 / 重锤 / 武士刀——它们致死时死因即
     * {@link RiotShieldHandler#isVanillaRouteDeathReason} 中的那几项。
     *
     * <p>格挡<b>只在右键格挡的有效窗口内生效</b>，窗口内不限制格挡次数、
     * 不消耗耐久。
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
        // 可格挡的死亡原因同防暴盾牌（共用白名单）
        if (!RiotShieldHandler.isBlockableDeathReason(deathReason)) {
            return true;
        }
        return !parry(victim);
    }

    /**
     * 供<b>走原版攻击路线</b>的武器（Dream 铁斧 / 钻石剑 / 重锤 / 武士刀）
     * 在扣除虚拟血量<b>之前</b>调用。
     *
     * <p>这些武器的伤害落在虚拟血量上、不会在每次攻击时进入死亡管线，
     * 只靠 {@link #allowDeath} 只能兜住「虚拟血量归零的致死一击」，
     * 中间的伤害照扣。因此必须由武器自身在扣血前询问武士刀是否正在格挡。
     *
     * @return true 已被格挡，调用方应放弃本次攻击的全部伤害
     */
    public static boolean tryBlockAttack(Player victim, Player attacker) {
        if (attacker == null || attacker.isSpectator()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) {
            return false;
        }
        return parry(victim);
    }

    /**
     * 格挡核心：<b>仅在格挡有效窗口内</b>且耐久足够时格挡成功，
     * 播放格挡音效并消耗 1 点耐久。
     *
     * <p>耐久规则：每格挡成功一次扣 1 点，<b>耐久不会低于 1</b>；
     * 耐久只剩 1 点时无法再格挡（此时 {@link KatanaItem#use} 已提前拦截）。
     *
     * @return true 本次攻击已被格挡
     */
    private static boolean parry(Player victim) {
        if (!isInActiveBlockWindow(victim)) {
            return false;
        }
        ItemStack stack = victim.getUseItem();
        // 耐久只剩 1 点时无法格挡（正常情况下 use 阶段已拦截，这里双保险）
        if (stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            return false;
        }

        // ── 格挡成功 ──
        playParrySound(victim);
        // 消耗 1 点耐久（耐久不会降至低于 1）
        if (!victim.isCreative() && stack.getMaxDamage() > 0
                && stack.getDamageValue() < stack.getMaxDamage() - 1) {
            stack.setDamageValue(stack.getDamageValue() + 1);
            if (victim instanceof ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
        }
        return true;
    }

    /**
     * 受害者当前是否正处于武士刀的<b>格挡有效窗口</b>内。
     *
     * <p>成立条件：正在使用武士刀（右键举刀）、职业已开启 {@code canUseSpVanillaWeapon}、
     * 且已使用时间落在「前摇之后 ~ 收刀之前」的区间内。
     */
    public static boolean isInActiveBlockWindow(Player victim) {
        // 必须正在使用武士刀格挡
        if (!victim.isUsingItem()) {
            return false;
        }
        ItemStack stack = victim.getUseItem();
        if (stack == null || !stack.is(ModItems.KATANA)) {
            return false;
        }
        // 职业门禁：只有开启 canUseSpVanillaWeapon 的职业才能用武士刀格挡（双保险）
        if (!KatanaItem.canPlayerUse(victim)) {
            return false;
        }
        KatanaState.PlayerState state = KatanaState.get(victim);
        int ticksUsing = victim.getTicksUsingItem();
        // 前摇：普通格挡 0.4 秒；衔接招式命中后的格挡为 0
        int windup = state.blockLinked ? 0 : KatanaState.BLOCK_WINDUP_TICKS;
        // 前摇未完成，或已超出 1.2 秒有效时间 / 已收刀
        return ticksUsing >= windup && ticksUsing < KatanaState.BLOCK_USE_DURATION;
    }

    /** 格挡成功的格挡音效（sword_parry_1 / sword_parry_2 随机）。 */
    private static void playParrySound(Player victim) {
        SoundEvent parrySound = ThreadLocalRandom.current().nextBoolean()
                ? NRSounds.KATANA_PARRY_1
                : NRSounds.KATANA_PARRY_2;
        victim.level().playSound(null, victim.blockPosition(), parrySound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}

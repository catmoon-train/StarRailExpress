package org.agmas.noellesroles.content.item.katana;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
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
 * <p><b>可格挡范围不限死因</b>：与斗士「钢筋铁骨」同一口径——只要受害者正处在格挡有效窗口内，
 * 任何死因的致命伤害都能被挡下（不再复用 {@link RiotShieldHandler#isBlockableDeathReason}
 * 的白名单；防暴盾牌仍按自己的白名单走，两者互不影响）。
 *
 * <p>唯一挡不住的是<b>强制死亡</b>（{@code forcekill}，例如摔出列车、挂机、阿蒙夺舍）：它在死亡
 * 管线里会忽略监听器的返回值照常处死玩家，因此这里必须先查
 * {@link io.wifi.starrailexpress.game.GameUtils#isForcedDeathInProgress()} 直接放行，
 * 否则会出现「播放了格挡音效、扣了耐久、回放写着格挡成功，玩家却照常死亡」。
 *
 * <p>逐次伤害的格挡（走虚拟血量的特殊原版武器）统一在
 * {@code DreamHealthComponent#hurt} 里询问 {@link #tryBlockAttack}，因此不再局限于某几把
 * 写死的武器——任何走虚拟血量的武器（Dream 铁斧 / 钻石剑 / 重锤、矛、武士刀……）都算。
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
        // 没有击杀者的死亡（毒发、环境判死等）同样能被格挡：与斗士「钢筋铁骨」一致的口径。
        // 两个事件在死亡管线里先后触发，先命中者会直接取消死亡，因此不会重复消耗耐久。
        AllowPlayerDeath.EVENT.register(KatanaHandler::allowDeathWithoutKiller);
    }

    /**
     * 死亡拦截（有击杀者）：受害者正处于格挡有效窗口内时取消死亡，<b>不限死因</b>。
     *
     * @return true 允许死亡 / false 已被格挡（取消死亡）
     */
    public static boolean allowDeath(Player victim, Player attacker, ResourceLocation deathReason) {
        if (attacker != null && attacker.isSpectator()) {
            return true;
        }
        return allowDeathWithoutKiller(victim, deathReason);
    }

    /**
     * 死亡拦截（无击杀者）：判定与 {@link #allowDeath} 完全一致。
     *
     * <p>格挡<b>只在右键格挡的有效窗口内生效</b>，窗口内不限制格挡次数，每次成功消耗 1 点耐久。
     *
     * @return true 允许死亡 / false 已被格挡（取消死亡）
     */
    public static boolean allowDeathWithoutKiller(Player victim, ResourceLocation deathReason) {
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victim)) {
            return true;
        }
        // 强制死亡（forcekill）挡不住：死亡管线会忽略返回值照常处死，这里不能假装挡下
        if (GameUtils.isForcedDeathInProgress()) {
            return true;
        }
        // 不限死因：窗口内任何死因的致命伤害都能被挡下
        return !parry(victim);
    }

    /**
     * 供<b>走虚拟血量</b>的武器（Dream 铁斧 / 钻石剑 / 重锤、矛、武士刀……）
     * 在扣除虚拟血量<b>之前</b>调用。
     *
     * <p>这些武器的伤害落在虚拟血量上、不会在每次攻击时进入死亡管线，
     * 只靠 {@link #allowDeath} 只能兜住「虚拟血量归零的致死一击」，
     * 中间的伤害照扣。因此必须在扣血前询问武士刀是否正在格挡。
     *
     * <p>统一在 {@code DreamHealthComponent#hurt} 的入口调用，所以<b>不再局限于某几把写死的
     * 武器</b>：任何走虚拟血量的伤害（含后续新增的特殊原版武器）都会先过一遍格挡判定。
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
        recordParryReplay(victim);
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

    /** 格挡成功的回放播报：「xx 成功用武士刀格挡了一次伤害」。 */
    private static void recordParryReplay(Player victim) {
        if (!(victim instanceof ServerPlayer sp) || SRE.REPLAY_MANAGER == null) {
            return;
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.katana.parry",
                GameReplayUtils.getReplayPlayerDisplayText(sp, true)));
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

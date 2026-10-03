package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.katana.KatanaState;
import org.agmas.noellesroles.role.vigilante.SwordsmanRole;

/**
 * 剑客的「淬血」状态。
 *
 * <p>淬血期间持续维持三件事：
 * <ol>
 * <li>武士刀虚拟血量伤害倍率（{@link KatanaState#setVirtualDamageMultiplier}）；</li>
 * <li>手上武士刀的附魔光效（{@link SwordsmanRole#applyQuXueGlint}）；</li>
 * <li>速度 II + 急迫 II（每 tick 续 20 tick，天然覆盖整个持续时间）。</li>
 * </ol>
 *
 * <p>倍率与光效都只在「状态翻转」的那一 tick 写入一次，避免每 tick 改物品组件
 * 造成held 物品同步包的额外开销。
 */
public class SwordsmanRoleData extends SimpleRoleData {

    /** 淬血剩余 tick（同步给客户端画 HUD 倒数）。 */
    private int quXueLeftTicks;
    /** 服务端：淬血结束时刻（gameTime）。 */
    private long quXueUntil;
    /** 上一次 serverTick 时淬血是否处于生效中，用于检测状态翻转。 */
    private boolean lastActive;

    public SwordsmanRoleData(RoleDataContext context) {
        super(context);
    }

    /** 淬血剩余 tick。 */
    public int quXueLeftTicks() {
        return quXueLeftTicks;
    }

    /** 淬血是否正在生效。 */
    public boolean isQuXueActive() {
        return quXueLeftTicks > 0;
    }

    /**
     * 开启淬血：立即挂上伤害倍率与附魔光效（不等到下一次 serverTick）。
     *
     * @param durationTicks 持续时间
     * @param multiplier    虚拟血量伤害倍率
     */
    public void activateQuXue(int durationTicks, int multiplier) {
        quXueUntil = player.level().getGameTime() + durationTicks;
        quXueLeftTicks = durationTicks;
        lastActive = true;
        SwordsmanRole.applyQuXueGlint(player, true);
        KatanaState.setVirtualDamageMultiplier(player, multiplier);
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        boolean active = GameUtils.isPlayerAliveAndSurvival(sp) && sp.level().getGameTime() < quXueUntil;
        if (active != lastActive) {
            // 状态翻转：统一挂载 / 卸载倍率与光效
            lastActive = active;
            SwordsmanRole.applyQuXueGlint(player, active);
            KatanaState.setVirtualDamageMultiplier(player,
                    active ? SwordsmanRole.QUXUE_DAMAGE_MULTIPLIER : 1);
        }
        if (!active) {
            if (quXueLeftTicks != 0) {
                quXueLeftTicks = 0;
                sync();
            }
            return;
        }
        // 速度 II + 急迫 II：每 tick 续 20 tick，持续时间自然与淬血对齐
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, true, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20, 1, true, false, false));
        quXueLeftTicks = (int) Math.max(0, quXueUntil - sp.level().getGameTime());
    }

    /** 退出职业 / 局末：卸掉倍率与光效，避免残留到下一局。 */
    @Override
    public void clear() {
        KatanaState.setVirtualDamageMultiplier(player, 1);
        SwordsmanRole.applyQuXueGlint(player, false);
        quXueUntil = 0;
        quXueLeftTicks = 0;
        lastActive = false;
    }

    // 淬血状态只同步给本人，用于 HUD 倒数。
    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return sp == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("quXueLeftTicks", quXueLeftTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        quXueLeftTicks = tag.getInt("quXueLeftTicks");
    }
}

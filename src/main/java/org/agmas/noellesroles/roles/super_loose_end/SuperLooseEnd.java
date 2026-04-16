package org.agmas.noellesroles.roles.super_loose_end;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.item.TimeStopClock;
import org.jetbrains.annotations.Nullable;

/**
 * 超级亡命徒
 *  - 击杀获得增益
 */
public class SuperLooseEnd extends NormalRole {
    public static final RandomSource RANDOM = RandomSource.create();
    public static final int MAX_SPEED_LVL = 10;
    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public SuperLooseEnd(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }
    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        serverPlayer.removeEffect(MobEffects.MOVEMENT_SPEED);
        serverPlayer.addEffect(
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,  // 速度效果
                        2400,                  // 持续时间（tick）
                        2,
                        false,                // 是否显示粒子效果
                        false                  // 是否显示图标
                ));
    }
    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        if (killer != null) {
            var effect = killer.getEffect(MobEffects.MOVEMENT_SPEED);
            int speedLvl = 0;
            if (effect != null) {
                speedLvl = effect.getAmplifier();
            }
            killer.removeEffect(MobEffects.MOVEMENT_SPEED);
            killer.addEffect(
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,  // 速度效果
                            2400,                  // 持续时间（tick）
                            Math.min(speedLvl + 1, MAX_SPEED_LVL),
                            false,                // 是否显示粒子效果
                            false                  // 是否显示图标
                    ));
            // 每次击杀给予时停钟或防御药剂
            int r = RANDOM.nextInt(100);
            if (r < 50) {
                // 如果有缺失耐久的时停钟则恢复耐久，否则给予一个新的耐久为1的
                Inventory inventory = killer.getInventory();
                boolean hasRecovery = false;
                for (var item : inventory.items) {
                    if (item.isEmpty())
                        continue;
                    if (item.is(ModItems.TIME_STOP_CLOCK) && item.getDamageValue() > 0) {
                        item.setDamageValue(item.getDamageValue() - 1);
                        hasRecovery = true;
                        break;
                    }
                }
                if (!hasRecovery) {
                    ItemStack timeStopClock = new ItemStack(ModItems.TIME_STOP_CLOCK);
                    ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_STOP_TIME, SREConfig.instance().antWarClockStopTick);
                    ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_COOLDOWN, SREConfig.instance().antWarClockCooldownTick);
                    timeStopClock.setDamageValue(TimeStopClock.MAX_DURABILITY - 1);
                    killer.addItem(timeStopClock);
                }
            }
            else
                killer.addItem(TMMItems.DEFENSE_VIAL.getDefaultInstance());
            // 击杀获得50
            SREPlayerShopComponent.KEY.get(killer).addToBalance(50);
        }
    }
}

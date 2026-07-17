package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 系统总控：tick 调度（每日刷新 + 偷窃会话）、死亡掉落、局末清理。
 * <p>接线（本模式无 tickable 注册表，全是手写调用点）：
 * <ul>
 *   <li>{@link #register()} ← {@code SixtySecondsMod.init()}（注册死亡掉落事件）</li>
 *   <li>{@link #tick} ← {@code SixtySecondsManager.tick} 的 DAY 分支</li>
 *   <li>{@link #onDayStart} ← {@code SixtySecondsManager.startDay}</li>
 *   <li>{@link #reset} ← {@code SixtySecondsGameMode.stopGame}</li>
 * </ul>
 */
public final class SixtySecondsNpcSystem {
    private SixtySecondsNpcSystem() {
    }

    /** init 注册一次：NPC 死亡掉落（主手武器 + 随身物资 + 废料）。 */
    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, damageSource) -> {
                    if (!(entity.level() instanceof ServerLevel level)
                            || !(entity instanceof SixtySecondsNpcEntity npc)) {
                        return;
                    }
                    dropLoot(level, npc);
                });
    }

    /**
     * 死亡掉落：商人不掉货（{@code onAttackedBy} 让它被打就跑，本就走不到这儿；
     * 真被秒杀也不给「杀商人抢货」的正反馈）；其余掉随身物资 + 1~2 废料。
     * <p>夜袭强盗（带 {@code ASSAULT_TAG}）的废料由 {@code SixtySecondsDefenseSystem.register} 统一发放，
     * 这里<b>不再补发</b>，否则同一只怪掉两份（照抄 {@code SixtySecondsPveSystem} 的同款分工）。
     */
    private static void dropLoot(ServerLevel level, SixtySecondsNpcEntity npc) {
        if (npc.getVariant() == SixtySecondsNpcEntity.Variant.MERCHANT) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < npc.getCarry().size(); i++) {
            ItemStack stack = npc.getCarry().get(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        if (!npc.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG)) {
            drops.add(new ItemStack(ModItems.SIXTY_SECONDS_SCRAP, 1 + level.getRandom().nextInt(2)));
        }
        for (ItemStack stack : drops) {
            ItemEntity drop = new ItemEntity(level, npc.getX(), npc.getY() + 0.3D, npc.getZ(), stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /** DAY 相位每 tick 调：子相位首 tick 刷新 NPC，并推进偷窃会话。 */
    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        // 子相位首 tick 刷新（照抄 SixtySecondsPveSystem 的 Boss 判定写法）
        long elapsed = SixtySecondsDayCycle.elapsed(data, now);
        if (elapsed == SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.DAYTIME)) {
            SixtySecondsNpcSpawner.spawnDaily(level, data, false);
        } else if (elapsed == SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT)) {
            SixtySecondsNpcSpawner.spawnDaily(level, data, true);
        }
        // 海盗：每 PIRATE_CHECK_INTERVAL 对水面附近的玩家做一次遭遇判定（夜里更凶）
        if (now % SixtySecondsBalance.PIRATE_CHECK_INTERVAL == 0) {
            SixtySecondsNpcSpawner.spawnPirates(level, data, SixtySecondsDayCycle.isNight(data, now));
        }
        // 海洋生物（鲨鱼/海怪）：每 OCEAN_CHECK_INTERVAL 对水上玩家做刷新判定
        if (now % OceanCreatureSpawner.CHECK_INTERVAL == 0) {
            OceanCreatureSpawner.tick(level);
        }
        SixtySecondsNpcTheft.tick(level);
    }

    /** 换日：第一天按配置落位手动放置的 NPC；之后每天在绑定门门口概率刷。 */
    public static void onDayStart(ServerLevel level, SixtySecondsState.Data data) {
        if (data.dayNumber == 1) {
            SixtySecondsNpcSpawner.spawnConfigured(level, data);
        }
        SixtySecondsNpcSpawner.spawnAtDoors(level, data, false);
    }

    /** 找门外指定范围内最近的、可交谈的 NPC（供门菜单的「门外的访客」用）。 */
    @Nullable
    public static SixtySecondsNpcEntity doorVisitor(ServerLevel level, BlockPos door, double radius) {
        SixtySecondsNpcEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (SixtySecondsNpcEntity npc : level.getEntitiesOfClass(SixtySecondsNpcEntity.class,
                new AABB(door).inflate(radius))) {
            if (!npc.isAlive() || npc.getVariant() == SixtySecondsNpcEntity.Variant.BANDIT) {
                continue;
            }
            double dist = npc.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = npc;
            }
        }
        return best;
    }

    /**
     * 局末清理：清偷窃会话 + 全图 discard NPC。
     * <p>⚠ 必须<b>先收集再 discard</b>：遍历 {@code getAllEntities()} 途中删实体会并发修改吐 null 崩服。
     * <p>本清扫只扫得到<b>已加载</b>的实体；卸载区块里的残留由
     * {@code SixtySecondsArena} 的 ENTITY_LOAD 清理窗口兜底（NPC 类已加入其白名单）。
     */
    public static void reset(ServerLevel level) {
        SixtySecondsNpcTheft.reset();
        List<SixtySecondsNpcEntity> doomed = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof SixtySecondsNpcEntity npc) {
                doomed.add(npc);
            }
        }
        for (SixtySecondsNpcEntity npc : doomed) {
            npc.discard();
        }
    }
}

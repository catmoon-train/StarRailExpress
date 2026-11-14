package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PendulumRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 钟摆：定钟保护自身并回溯位置，定摆将目标拉回后开放处决。 */
public class PendulumRole extends NormalRole {
    public static final ResourceLocation DEATH_NEEDLE = org.agmas.noellesroles.Noellesroles.id("second_needle");
    public static final ResourceLocation DEATH_MINUTE = org.agmas.noellesroles.Noellesroles.id("minute_needle");
    public static final ResourceLocation CLOCK_SKILL = SRE.id("pendulum_clock");
    public static final ResourceLocation MARK_SKILL = SRE.id("pendulum_mark");
    public static final int CLOCK_DURATION = 24 * 20;
    public static final int CLOCK_COOLDOWN = 90 * 20;
    public static final int HAMMER_EXTENSION = 5 * 20;
    public static final double MARK_RANGE = 10.0D;
    public static final int SWING_DELAY = 4 * 20;
    public static final int RETURN_WINDOW = 6 * 20;
    public static final int RETURN_BIND = 8 * 20;
    public static final double RETURN_LIMIT = 28.0D;
    private static boolean eventsRegistered;

    public PendulumRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.PENDULUM,
                RoleSkill.skill(CLOCK_SKILL, "skill.noellesroles.pendulum.clock", PendulumRole::toggleClock)
                        .cooldownTicks(CLOCK_COOLDOWN).toggleable(true).showOnHud(true).build(),
                // charges 本身只是有限使用次数；真正的逐层恢复由 RoleData 驱动并镜像到技能栏。
                RoleSkill.skill(MARK_SKILL, "skill.noellesroles.pendulum.mark", context -> mark(context.player()))
                        .cooldownTicks(PendulumRules.RECHARGE_TICKS).charges(PendulumRules.MAX_CHARGES)
                        .toggleable(true).shifted(true).showOnHud(true).build());
        registerEvents();
    }

    /** 只在初始化时注册一次，不能让每个职业数据实例各自注册监听器。 */
    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                amount <= 0.0F || !(entity instanceof ServerPlayer victim) || !absorbClockHit(victim));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim && absorbClockHit(victim)) {
                // 原版致死兜底：取消死亡后必须保留正血量，避免下一 tick 再次死亡。
                victim.setHealth(Math.max(1.0F, victim.getHealth()));
                return false;
            }
            return true;
        });
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> !absorbClockHit(victim));
        // 在公共击杀入口拦截，包含强制击杀，避免核心强杀参数跳过后续死亡否决。
        GameUtilityEvents.BEFORE_KILL.register((victim, body, killer, reason, force) -> !absorbClockHit(victim));
    }

    private static boolean absorbClockHit(Player victim) {
        if (!(victim instanceof ServerPlayer self) || self.isSpectator()
                || !SREGameWorldComponent.KEY.get(self.level()).isRunning()
                || !SREGameWorldComponent.KEY.get(self.level()).isRole(self, ModRoles.PENDULUM)) {
            return false;
        }
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, self);
        long now = self.level().getGameTime();
        if (data == null || !data.isClockProtecting(now)) {
            return false;
        }
        // BEFORE_KILL 可能来自异步调用；否决可立即返回，计数和效果必须交给服务端线程。
        if (!self.server.isSameThread()) {
            self.server.execute(() -> {
                if (RoleData.isAttached(data)) {
                    data.absorbClockHit(now);
                }
            });
            return true;
        }
        return data.absorbClockHit(now);
    }

    public static boolean canUse(Player player) {
        return GameUtils.isPlayerAliveAndSurvival(player)
                && SREGameWorldComponent.KEY.get(player.level()).isRunning()
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.PENDULUM);
    }

    public static boolean toggleClock(RoleSkill.RoleSkillContext context) {
        ServerPlayer player = context.player();
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, player);
        if (data == null || !canUse(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (data.hasActiveClock()) {
            data.finishClock(now);
            // 叠加钟锤可使持续时间超过冷却。此时关闭不应再次消耗一次九十秒冷却。
            return !context.skillReady();
        }
        return context.skillReady() && data.startClock(now);
    }

    public static boolean mark(ServerPlayer player) {
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, player);
        if (data == null || !canUse(player)) {
            return false;
        }
        ServerPlayer target = GazeRays.firstVisiblePlayer(player, MARK_RANGE, 0.35D);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.pendulum.miss"), true);
            return false;
        }
        return data.mark(target, player.level().getGameTime());
    }

    /** 分针命中时才判定处决资格，不能凭投出时的资格延长六秒窗口。 */
    public static void onMinuteHit(ServerPlayer owner, ServerPlayer target) {
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, owner);
        if (data == null || !canUse(owner) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        if (data.canFinish(target, owner.level().getGameTime())) {
            CommandBlockCombat.tryKill(owner, target, DEATH_MINUTE, false, null);
        } else {
            target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 2 * 20, 0, false, true, true));
        }
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 230, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(ModItems.MINUTE_NEEDLE.getDefaultInstance(), 80, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.CLOCK_HAMMER.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.SECOND_NEEDLE.getDefaultInstance());
    }
}

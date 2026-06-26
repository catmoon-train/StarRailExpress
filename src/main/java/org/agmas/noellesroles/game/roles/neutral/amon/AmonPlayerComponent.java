package org.agmas.noellesroles.game.roles.neutral.amon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.AmonSkinS2CPacket;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 阿蒙（诡秘之主）—— 中立独立胜利角色，核心机制为「寄生」。
 *
 * <p>阿蒙隐秘地给附近活人种下「时之虫」（受害者无感），时之虫潜伏成熟后该宿主成为可「夺舍」的载体。
 * 阿蒙可主动夺舍，也会在受到致命伤时自动夺舍续命（{@link AmonEventHandler}）；只有在没有成熟宿主时才会真正死亡。
 * 胜利条件「夺舍并幸存」：游戏结算时若阿蒙存活且至少夺舍过一次，则独立获胜
 * （在 {@code CustomWinnerClass} 中判定）。
 *
 * <p>隐私要点：时之虫状态仅同步给阿蒙本人，且绝不给受害者任何可见效果。
 */
public final class AmonPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<AmonPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("amon"), AmonPlayerComponent.class);

    /** 时之虫潜伏成熟时间：90 秒。 */
    public static final int INCUBATION_TICKS = 90 * 20;
    /** 终幕「寻找阿蒙」持续时间：2 分钟。 */
    public static final int FINALE_TICKS = 120 * 20;
    /** 种植半径：4 格。 */
    private static final double PLANT_RADIUS_SQR = 4.0 * 4.0;

    private final Player player;

    // ===== 服务端权威状态 =====
    /** 宿主 UUID -> 已潜伏 tick。 */
    private final Map<UUID, Integer> seeds = new HashMap<>();
    /** 已成熟（可夺舍）宿主 UUID。 */
    private final Set<UUID> maturedHosts = new HashSet<>();
    /** 当前伪装目标（夺舍后顶替其皮肤+名字）。 */
    private UUID disguiseTarget;

    public int usurpCount;
    public boolean hasUsurped;
    public int seedCap = 2;

    // ===== 终幕状态 =====
    /** 是否处于「终幕·寻找阿蒙」阶段。 */
    public boolean finalePhase;
    /** 终幕剩余 tick。 */
    public int finaleTicks;
    /** 备用能力（续命/逃脱）剩余次数 = 回归的寄宿体数。 */
    public int reserveLives;

    // ===== 客户端镜像（仅用于 HUD 显示）=====
    public int clientSeeds;
    public int clientMatured;

    public AmonPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }

    /** 隐私：时之虫状态只对阿蒙本人可见。 */
    @Override public boolean shouldSyncWith(ServerPlayer target) { return this.player == target; }

    public void sync() { KEY.sync(player); }

    @Override
    public void init() {
        seeds.clear();
        maturedHosts.clear();
        disguiseTarget = null;
        usurpCount = 0;
        hasUsurped = false;
        seedCap = 2;
        finalePhase = false;
        finaleTicks = 0;
        reserveLives = 0;
        clientSeeds = 0;
        clientMatured = 0;
        sync();
    }

    @Override
    public void clear() {
        clearDisguise();
        seeds.clear();
        maturedHosts.clear();
        disguiseTarget = null;
        usurpCount = 0;
        hasUsurped = false;
        seedCap = 2;
        finalePhase = false;
        finaleTicks = 0;
        reserveLives = 0;
        clientSeeds = 0;
        clientMatured = 0;
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer amon)) return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());

        // 角色被中途更换：清理残留状态与伪装。
        if (!game.isRole(amon, ModRoles.AMON)) {
            if (!seeds.isEmpty() || !maturedHosts.isEmpty() || disguiseTarget != null) {
                clearDisguise();
                seeds.clear();
                maturedHosts.clear();
                disguiseTarget = null;
                sync();
            }
            return;
        }
        if (!game.isRunning()) return;
        boolean alive = GameUtils.isPlayerAliveAndSurvival(amon);

        // 终幕阶段：驱动倒计时、发光与胜利判定（结束不再潜伏新虫）。
        if (finalePhase) {
            if (alive) tickFinale(amon, game);
            return;
        }
        if (!alive) return;

        seedCap = Math.max(1, (game.getPlayerCount() ) / 6);

        boolean changed = false;

        // 潜伏与成熟
        Iterator<Map.Entry<UUID, Integer>> it = seeds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Player host = amon.level().getPlayerByUUID(entry.getKey());
            if (host == null || !GameUtils.isPlayerAliveAndSurvival(host)) {
                it.remove();
                changed = true;
                continue;
            }
            int ticks = entry.getValue() + 1;
            if (ticks >= INCUBATION_TICKS) {
                it.remove();
                maturedHosts.add(entry.getKey());
                amon.displayClientMessage(Component.translatable("message.noellesroles.amon.seed_matured")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                changed = true;
            } else {
                entry.setValue(ticks);
            }
        }

        // 剪枝离线/死亡的成熟宿主
        if (maturedHosts.removeIf(uuid -> {
            Player host = amon.level().getPlayerByUUID(uuid);
            return host == null || !GameUtils.isPlayerAliveAndSurvival(host);
        })) {
            changed = true;
        }

        if (changed || amon.tickCount % 20 == 0) sync();
    }

    /** 种下时之虫（静默）。target 为准星指向的玩家。 */
    public boolean plantSeed(ServerPlayer target) {
        if (!(player instanceof ServerPlayer amon)) return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON) || !GameUtils.isPlayerAliveAndSurvival(amon)) {
            return false;
        }
        if (seeds.size() + maturedHosts.size() >= seedCap) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.cap_reached", seedCap)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (target == null || target == amon || !GameUtils.isPlayerAliveAndSurvival(target)
                || seeds.containsKey(target.getUUID()) || maturedHosts.contains(target.getUUID())
                || amon.distanceToSqr(target) > PLANT_RADIUS_SQR || !amon.hasLineOfSight(target)) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        seeds.put(target.getUUID(), 0);
        // 仅通知阿蒙自己；绝不给受害者任何反馈。
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.seed_planted")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    /** 主动夺舍：夺舍最近的成熟宿主。 */
    public boolean usurpNearestMatured() {
        if (!(player instanceof ServerPlayer amon)) return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON) || !GameUtils.isPlayerAliveAndSurvival(amon)) {
            return false;
        }
        UUID host = pickAliveMaturedHost(amon);
        if (host == null) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.no_matured_host")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return usurp(host);
    }

    /**
     * 致命伤时的死亡转移：消耗一名成熟宿主续命。
     *
     * @return true 表示已夺舍（死亡应被取消）；false 表示无成熟宿主（真正死亡）。
     */
    public boolean tryDeathTransfer() {
        if (!(player instanceof ServerPlayer amon)) return false;
        while (true) {
            UUID host = pickAliveMaturedHost(amon);
            if (host == null) return false;
            if (usurp(host)) {
                amon.displayClientMessage(Component.translatable("message.noellesroles.amon.death_transfer")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                return true;
            }
            // usurp 失败已将该宿主移除，继续尝试下一个。
        }
    }

    private UUID pickAliveMaturedHost(ServerPlayer amon) {
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<UUID> it = maturedHosts.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player host = amon.level().getPlayerByUUID(uuid);
            if (host == null || !GameUtils.isPlayerAliveAndSurvival(host)) {
                it.remove();
                continue;
            }
            double d = amon.distanceToSqr(host);
            if (d < bestDist) {
                bestDist = d;
                best = uuid;
            }
        }
        return best;
    }

    /** 夺舍指定宿主：杀死宿主、传送到其位置、顶替其皮肤与名字。 */
    private boolean usurp(UUID hostUuid) {
        if (!(player instanceof ServerPlayer amon)) return false;
        if (!(amon.level() instanceof ServerLevel level)) return false;
        Player hostPlayer = level.getPlayerByUUID(hostUuid);
        if (!(hostPlayer instanceof ServerPlayer host) || !GameUtils.isPlayerAliveAndSurvival(host)) {
            seeds.remove(hostUuid);
            maturedHosts.remove(hostUuid);
            return false;
        }

        Vec3 pos = host.position();
        float yRot = host.getYRot();
        float xRot = host.getXRot();

        // 强制击杀宿主以确保夺舍确定性（留下尸体，死因 AMON_USURP）。
        GameUtils.forceKillPlayer(host, true, null, GameConstants.DeathReasons.AMON_USURP);

        // 阿蒙占据宿主位置并顶替其外观。
        amon.teleportTo(level, pos.x, pos.y, pos.z, yRot, xRot);
        setDisguise(hostUuid);

        usurpCount++;
        hasUsurped = true;
        seeds.remove(hostUuid);
        maturedHosts.remove(hostUuid);

        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.usurp_done")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    private void setDisguise(UUID hostUuid) {
        disguiseTarget = hostUuid;
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level)) return;
        AmonSkinS2CPacket packet = new AmonSkinS2CPacket(amon.getUUID(), hostUuid);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /** 清除阿蒙伪装（向所有客户端广播）。在真正死亡 / 重置 / 角色变更时调用。 */
    public void clearDisguise() {
        if (disguiseTarget == null) return;
        disguiseTarget = null;
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level)) return;
        AmonSkinS2CPacket packet = new AmonSkinS2CPacket(amon.getUUID(), null);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /**
     * 游戏结算钩子（供 CustomWinnerClass 调用）。
     * 若存在持有 ≥1 寄宿体的存活阿蒙：开启/维持「终幕」，返回 {@code NONE} 阻止常规结算；
     * 否则返回 {@code NOT_MODIFY} 让常规结算继续（阿蒙按常规失败）。
     * 阿蒙的最终胜利由 {@link #tickFinale} 在终幕结束时通过 customWinnerWin 宣布。
     */
    public static GameUtils.WinStatus handleGameEnd(ServerLevel level, GameUtils.WinStatus pendingStatus) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        boolean block = false;
        for (ServerPlayer p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || !game.isRole(p, ModRoles.AMON)) continue;
            AmonPlayerComponent comp = KEY.get(p);
            if (comp.finalePhase) {
                block = true;
            } else if (comp.maturedHosts.size() >= 1) {
                comp.startFinale();
                block = true;
            }
        }
        return block ? GameUtils.WinStatus.NONE : GameUtils.WinStatus.NOT_MODIFY;
    }

    /** 开启终幕：寄宿体回归为备用能力、窃取全场物品、发光、全服播报。 */
    private void startFinale() {
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level)) return;
        finalePhase = true;
        finaleTicks = FINALE_TICKS;
        reserveLives = maturedHosts.size();
        // 寄宿体回归阿蒙：清空潜伏/成熟状态。
        maturedHosts.clear();
        seeds.clear();
        applyGlow(amon);
        stealItems(amon, level);
        // 全服播报：终幕降临，阿蒙戴上单片眼镜、全程发光。
        MutableComponent msg = Component.translatable("message.noellesroles.amon.finale_start")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        BroadcastMessageS2CPacket packet = new BroadcastMessageS2CPacket(msg);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, packet);
        }
        sync();
    }

    private void tickFinale(ServerPlayer amon, SREGameWorldComponent game) {
        applyGlow(amon);
        // 杀光其余所有人 → 立即获胜。
        if (countOtherAlive(amon) == 0) { declareWin(amon); return; }
        finaleTicks--;
        if (finaleTicks <= 0) { declareWin(amon); return; }
        if (finaleTicks % 20 == 0) sync();
    }

    private void applyGlow(ServerPlayer amon) {
        if (!amon.hasEffect(MobEffects.GLOWING)) {
            amon.addEffect(new MobEffectInstance(MobEffects.GLOWING, FINALE_TICKS + 40, 0, false, false, false));
        }
    }

    private int countOtherAlive(ServerPlayer amon) {
        int n = 0;
        for (ServerPlayer p : amon.serverLevel().players()) {
            if (p != amon && GameUtils.isPlayerAliveAndSurvival(p)) n++;
        }
        return n;
    }

    private void declareWin(ServerPlayer amon) {
        if (!(amon.level() instanceof ServerLevel level)) return;
        finalePhase = false;
        RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM,
                ModRoles.AMON_ID.getPath(), OptionalInt.of(ModRoles.AMON.color()));
    }

    /** 终幕续命/逃脱：消耗一个备用能力，瞬移逃脱并短暂无敌。无备用能力则真正死亡。 */
    public boolean tryFinaleEscape() {
        if (!(player instanceof ServerPlayer amon) || reserveLives <= 0) return false;
        reserveLives--;
        blinkAway(amon);
        amon.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 40, 0, false, false, false));
        amon.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, false));
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.finale_escape", reserveLives)
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    private void blinkAway(ServerPlayer amon) {
        double angle = amon.getRandom().nextDouble() * Math.PI * 2;
        double dist = 6 + amon.getRandom().nextDouble() * 4;
        double nx = amon.getX() + Math.cos(angle) * dist;
        double nz = amon.getZ() + Math.sin(angle) * dist;
        amon.teleportTo(amon.serverLevel(), nx, amon.getY(), nz, amon.getYRot(), amon.getXRot());
    }

    /** 窃取全场存活玩家的随机一件物品（钥匙/信件除外；枪械则复制而非夺走）。 */
    private void stealItems(ServerPlayer amon, ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p == amon || !GameUtils.isPlayerAliveAndSurvival(p)) continue;
            Inventory inv = p.getInventory();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || isProtectedItem(s)) continue;
                slots.add(i);
            }
            if (slots.isEmpty()) continue;
            int slot = slots.get(amon.getRandom().nextInt(slots.size()));
            ItemStack stack = inv.getItem(slot);
            ItemStack stolen = stack.copy();
            // 枪/左轮：复制给阿蒙，目标保留；其他物品：夺走。
            if (!stack.is(TMMItemTags.GUNS)) {
                inv.setItem(slot, ItemStack.EMPTY);
            }
            RoleUtils.insertStackInFreeSlot(amon, stolen);
        }
    }

    private boolean isProtectedItem(ItemStack s) {
        return s.is(TMMItems.KEY) || s.is(TMMItems.IRON_DOOR_KEY) || s.is(TMMItems.LETTER)
                || s.is(ModItems.LETTER_ITEM) || s.is(ModItems.COURIER_MAIL) || s.is(ModItems.RECEIVED_MAIL);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        // 仅同步给阿蒙本人（见 shouldSyncWith）：写计数而非宿主 UUID，避免泄露。
        tag.putInt("Seeds", seeds.size());
        tag.putInt("Matured", maturedHosts.size());
        tag.putInt("UsurpCount", usurpCount);
        tag.putBoolean("HasUsurped", hasUsurped);
        tag.putInt("SeedCap", seedCap);
        tag.putBoolean("Finale", finalePhase);
        tag.putInt("FinaleTicks", finaleTicks);
        tag.putInt("Reserve", reserveLives);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        clientSeeds = tag.getInt("Seeds");
        clientMatured = tag.getInt("Matured");
        usurpCount = tag.getInt("UsurpCount");
        hasUsurped = tag.getBoolean("HasUsurped");
        seedCap = tag.getInt("SeedCap");
        finalePhase = tag.getBoolean("Finale");
        finaleTicks = tag.getInt("FinaleTicks");
        reserveLives = tag.getInt("Reserve");
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) { }
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) { }
}

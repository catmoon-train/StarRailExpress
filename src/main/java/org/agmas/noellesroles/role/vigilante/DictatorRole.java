package org.agmas.noellesroles.role.vigilante;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.DictatorBookItem;
import org.agmas.noellesroles.content.item.JudgmentSwordItem;
import org.agmas.noellesroles.content.item.WatchmanShopEntry;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vigilante.DictatorRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 独裁者 —— 警长阵营特殊警卫（占用 2 个警长位，仅 18 人及以上对局出现）。
 *
 * <p>
 * 特性（注册见 {@code ModRoles.DICTATOR}）：
 * <ul>
 * <li>假心情（心情条为蓝色）、能看到计分板、体力为平民的 2.5 倍、拥有被动收入；</li>
 * <li>本能透视只能看到 8 格内的玩家，且所有玩家都显示为自己的颜色（见 {@code RoleInstinctRegister}）；</li>
 * <li>开局自带一把左轮手枪；商店可购买裁决之剑（150 金币，每次购买涨价 25）与独裁之书（150 金币，仅一次）；</li>
 * <li>裁决之剑：右键尸体 → 选死因 + 选凶手，全部猜中则凶手位置劈下闪电并按「裁断」死因判死；</li>
 * <li>独裁之书：猜一名玩家的职业，猜中同样闪电处决（死因「裁断」）；</li>
 * <li>死亡时掉落两把左轮手枪。</li>
 * </ul>
 */
public class DictatorRole extends NormalRole {

    /** 本能透视范围（格）：仅能透视 8 格内的玩家 */
    public static final int INSTINCT_RANGE = 8;

    /** 本能透视范围的平方（客户端距离判定用） */
    public static final int INSTINCT_RANGE_SQR = INSTINCT_RANGE * INSTINCT_RANGE;

    /** 「裁断」死因 */
    public static final ResourceLocation JUDGMENT_DEATH_REASON = Noellesroles.id("dictator_judgment");

    public DictatorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 该玩家是否是独裁者。 */
    public static boolean isDictator(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRole(player, ModRoles.DICTATOR);
    }

    /** 开局自带一把左轮手枪。 */
    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(TMMItems.REVOLVER.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(
                // 裁决之剑：150 金币，每购买一次涨价 25 金币
                new WatchmanShopEntry(ModItems.JUDGMENT_SWORD, 150, 25, ShopEntry.Type.TOOL),
                // 独裁之书：150 金币，仅能购买一次
                new ShopEntry(ModItems.DICTATOR_BOOK.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean canBuy(@NotNull Player player) {
                        DictatorRoleData data = RoleData.getNullable(DictatorRoleData.class, player);
                        return data != null && !data.bookPurchased && super.canBuy(player);
                    }

                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        DictatorRoleData data = RoleData.getNullable(DictatorRoleData.class, player);
                        if (data == null || data.bookPurchased) {
                            return false;
                        }
                        if (!super.onBuy(player)) {
                            return false;
                        }
                        data.bookPurchased = true;
                        return true;
                    }
                });
    }

    /** 独裁官死亡时掉落两把左轮手枪。 */
    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason, boolean forceDeath) {
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
        if (victim instanceof ServerPlayer serverPlayer) {
            serverPlayer.drop(TMMItems.REVOLVER.getDefaultInstance().copy(), false);
            serverPlayer.drop(TMMItems.REVOLVER.getDefaultInstance().copy(), false);
        }
    }

    // ==================== 裁决之剑 ====================

    /**
     * 裁决之剑结算：用右键尸体时锁定的那具尸体校验「死因 + 凶手」。
     *
     * <p>全部猜中 → 凶手位置劈下一道闪电并按「裁断」死因判死；
     * 凶手已死亡 → actionbar 提示，不触发闪电和死亡；
     * 无论正确与否，裁决之剑都会消耗。
     */
    public static void handleJudgment(ServerPlayer dictator, String deathReasonId, java.util.UUID killerUuid) {
        if (!isDictator(dictator) || !GameUtils.isPlayerAliveAndSurvival(dictator)) {
            return;
        }
        DictatorRoleData data = RoleData.getNullable(DictatorRoleData.class, dictator);
        if (data == null || data.pendingBodyUuid == null) {
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.no_body").withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        java.util.UUID pendingBodyUuid = data.pendingBodyUuid;
        data.pendingBodyUuid = null;

        PlayerBodyEntity body = dictator.serverLevel().getEntity(pendingBodyUuid) instanceof PlayerBodyEntity b
                ? b
                : null;
        if (body == null) {
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.body_gone").withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 无论正确与否，使用后裁决之剑消失
        consumeItem(dictator, JudgmentSwordItem.class);

        boolean reasonOk = equalsReason(body.getDeathReason(), deathReasonId);
        ServerPlayer killer = killerUuid == null ? null
                : dictator.serverLevel().getServer().getPlayerList().getPlayer(killerUuid);
        boolean killerOk = killer != null && killer.getUUID().equals(body.getKillerUuid());

        if (!reasonOk || !killerOk) {
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.judgment_wrong")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        if (killer == null || !GameUtils.isPlayerAliveAndSurvival(killer)) {
            // 目标已死亡：提示，不触发闪电和死亡（物品已正常消耗）
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.target_dead")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        strikeLightning(killer);
        GameUtils.killPlayer(killer, true, dictator, JUDGMENT_DEATH_REASON);
        dictator.displayClientMessage(
                Component.translatable("message.noellesroles.dictator.judgment_correct", killer.getName())
                        .withStyle(ChatFormatting.GOLD),
                true);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.dictator.judgment",
                GameReplayUtils.getReplayPlayerDisplayText(dictator, true),
                GameReplayUtils.getReplayPlayerDisplayText(killer, true)));
    }

    // ==================== 独裁之书 ====================

    /**
     * 独裁之书结算：猜测目标玩家当前的职业。
     *
     * <p>猜中 → 目标位置劈下闪电并按「裁断」死因判死；目标已死亡 → actionbar 提示；
     * 无论正确与否，独裁之书都会消耗。
     */
    public static void handleGuess(ServerPlayer dictator, java.util.UUID targetUuid, String roleId) {
        if (!isDictator(dictator) || !GameUtils.isPlayerAliveAndSurvival(dictator)) {
            return;
        }
        if (targetUuid == null || roleId == null || roleId.isBlank()) {
            return;
        }
        // 无论正确与否，使用后独裁之书消失
        consumeItem(dictator, DictatorBookItem.class);

        ServerPlayer target = dictator.serverLevel().getServer().getPlayerList().getPlayer(targetUuid);
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(dictator.level());
        if (target == null || game == null) {
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.guess_wrong").withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        var targetRole = game.getRole(target);
        if (targetRole == null || !targetRole.identifier().toString().equals(roleId)) {
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.guess_wrong").withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            // 猜测正确但目标已经死亡：提示并消耗（上面已经消耗）
            dictator.displayClientMessage(
                    Component.translatable("message.noellesroles.dictator.target_dead").withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        strikeLightning(target);
        GameUtils.killPlayer(target, true, dictator, JUDGMENT_DEATH_REASON);
        dictator.displayClientMessage(
                Component.translatable("message.noellesroles.dictator.guess_correct", target.getName(),
                        RoleUtils.getRoleName(targetRole)).withStyle(ChatFormatting.GOLD),
                true);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.dictator.guess",
                GameReplayUtils.getReplayPlayerDisplayText(dictator, true),
                GameReplayUtils.getReplayPlayerDisplayText(target, true),
                RoleUtils.getRoleName(targetRole)));
    }

    // ==================== 工具 ====================

    /** 尸体记录的死因是否与猜测一致（解析失败按不相等处理）。 */
    private static boolean equalsReason(@Nullable String actual, @Nullable String guessed) {
        if (actual == null || actual.isBlank() || guessed == null || guessed.isBlank()) {
            return false;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(actual.trim());
        ResourceLocation guessedId = ResourceLocation.tryParse(guessed.trim());
        return parsed != null && parsed.equals(guessedId);
    }

    /** 从主手 / 副手消耗一件指定道具。 */
    private static void consumeItem(ServerPlayer player, Class<?> itemClass) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (itemClass.isInstance(stack.getItem())) {
                stack.shrink(1);
                return;
            }
        }
    }

    /** 在目标位置劈下一道闪电（仅视觉效果，死亡由 killPlayer 按「裁断」死因结算）。 */
    private static void strikeLightning(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(target.getX(), target.getY(), target.getZ());
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }
        level.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.WEATHER, 2.0F, 1.0F);
    }
}

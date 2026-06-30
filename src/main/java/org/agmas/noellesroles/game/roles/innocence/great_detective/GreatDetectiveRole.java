package org.agmas.noellesroles.game.roles.innocence.great_detective;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue.ClueType;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 大侦探（平民阵营）。
 *
 * <p>
 * 开局自带"推理之书"。对着尸体右键发动推理技能：
 * <ul>
 * <li>若尸体没有凶手（如坠车/中毒等无击杀者死亡），无法推敲；</li>
 * <li>一具尸体只能使用一次技能；</li>
 * <li>成功触发后，随机获得该凶手的一条线索（修饰符 / 凶器大类 / 具体职业 /
 * 名字中的 2-3 个字 / 所在房间），写入推理之书（若身上没有则补发一本）。</li>
 * </ul>
 * 推理之书每页对应一名凶手，线索 >= 3 条时可点击"目标情况"查明其与自己的距离（快照）。
 */
public class GreatDetectiveRole extends NormalRole {

    public GreatDetectiveRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>(super.getDefaultItems());
        // items.add(new ItemStack(ModItems.DEDUCTION_BOOK));
        return items;
    }

    @Override
    public InteractionResult rightClickEntity(Player player, Entity victim) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(victim instanceof PlayerBodyEntity body)) {
            return InteractionResult.PASS;
        }
        Level level = serverPlayer.level();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRunning()) {
            return InteractionResult.PASS;
        }
        if (!gameWorld.isRole(serverPlayer, ModRoles.GREAT_DETECTIVE)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return InteractionResult.PASS;
        }

        GreatDetectivePlayerComponent comp = GreatDetectivePlayerComponent.KEY.get(serverPlayer);
        UUID corpseUuid = body.getUUID();

        // 一具尸体只能用一次
        if (comp.hasUsedCorpse(corpseUuid)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.corpse_used")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.SUCCESS;
        }
        comp.enterCooldown();
        comp.markCorpseUsed(corpseUuid);

        // 无凶手无法推敲，但进入CD
        UUID killerUuid = body.getKillerUuid();
        if (killerUuid == null) {
            comp.sync();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.no_killer")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.SUCCESS;
        }

        List<DetectiveClue> candidates = buildCandidates(serverPlayer, gameWorld, killerUuid, body);
        candidates.removeIf(c -> comp.hasClue(killerUuid, c));
        if (candidates.isEmpty()) {
            comp.sync();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.no_new_clue")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.SUCCESS;
        }

        DetectiveClue chosen = candidates.get(level.getRandom().nextInt(candidates.size()));
        comp.addClue(killerUuid, chosen);
        comp.sync();
        ensureBook(serverPlayer);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.great_detective.clue_found")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return InteractionResult.SUCCESS;
    }

    /** 根据尸体与凶手当前状态，构建本次可获得的候选线索。 */
    private List<DetectiveClue> buildCandidates(ServerPlayer detective, SREGameWorldComponent gameWorld,
            UUID killerUuid, PlayerBodyEntity body) {
        List<DetectiveClue> list = new ArrayList<>();
        Level level = detective.level();

        // 凶器大类（来自尸体死因，本就同步）
        ForensicCategory cat = ForensicCategory.fromDeathReason(ResourceLocation.tryParse(body.getDeathReason()));
        if (cat != ForensicCategory.UNKNOWN) {
            list.add(new DetectiveClue(ClueType.WEAPON, cat.name()));
        }

        // 具体职业
        SRERole role = gameWorld.getRole(killerUuid);
        if (role != null) {
            list.add(new DetectiveClue(ClueType.ROLE, role.identifier().toString()));
        }

        // 携带的修饰符
        WorldModifierComponent wmc = WorldModifierComponent.KEY.get(level);
        if (wmc != null) {
            for (SREModifier mod : wmc.getModifiers(killerUuid)) {
                if (mod != null) {
                    list.add(new DetectiveClue(ClueType.MODIFIER, mod.identifier().toString()));
                }
            }
        }

        // 需要在线的凶手实体才能取名字片段与所在房间
        Player killer = level.getPlayerByUUID(killerUuid);
        if (killer != null) {
            String fragment = pickNameFragment(killer.getName().getString(), level.getRandom());
            if (fragment != null && !fragment.isEmpty()) {
                list.add(new DetectiveClue(ClueType.NAME, fragment));
            }
            int room = computeRoom(killer);
            if (room > 0) {
                list.add(new DetectiveClue(ClueType.ROOM, String.valueOf(room)));
            }
        }

        return list;
    }

    /** 从名字中随机取一个 1 个字的片段。 */
    private static String pickNameFragment(String name, RandomSource random) {
        if (name == null) {
            return null;
        }
        name = name.trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() <= 1) {
            return name;
        }
        int len = 1;
        int start = random.nextInt(name.length() - len + 1);
        return name.substring(start, start + len);
    }

    /** 依据游玩区域沿长轴等分 roomCount 段，计算凶手所在的房间/车厢号（1 起）。 */
    private static int computeRoom(Player killer) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(killer.level());
        if (areas == null) {
            return -1;
        }
        AABB play = areas.getPlayArea();
        if (play == null) {
            return -1;
        }
        int roomCount = Math.max(1, areas.getRoomCount());
        double lenX = play.maxX - play.minX;
        double lenZ = play.maxZ - play.minZ;
        double pos;
        double min;
        double len;
        if (lenX >= lenZ) {
            pos = killer.getX();
            min = play.minX;
            len = lenX;
        } else {
            pos = killer.getZ();
            min = play.minZ;
            len = lenZ;
        }
        if (len <= 0) {
            return 1;
        }
        double t = (pos - min) / len;
        t = Math.max(0.0, Math.min(0.999999, t));
        return (int) (t * roomCount) + 1;
    }

    /** 若身上没有推理之书则补发一本。 */
    private static void ensureBook(ServerPlayer player) {
        for (var compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(ModItems.DEDUCTION_BOOK)) {
                    return;
                }
            }
        }
        ItemStack book = new ItemStack(ModItems.DEDUCTION_BOOK);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}

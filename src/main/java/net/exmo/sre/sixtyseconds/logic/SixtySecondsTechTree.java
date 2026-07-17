package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.network.OpenTechTreeS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 科技树：用<b>废料</b>（{@code sixty_seconds_scrap}）解锁科技，按队共享
 * （{@link SixtySecondsState.TeamData#unlockedTech}）。科技门控合成台配方
 * （{@link SixtySecondsRecipes}）与功能方块。
 *
 * <h3>结构（重构版）</h3>
 * 按大类（{@link #CATEGORIES}）分为若干分支，每个分支内逐级链式前置（parentId）。
 * <b>解锁费用按节点级别递增</b>（{@link #costOf}，以第一天全队约可搜到 40 个废料为基准：
 * I 级 3 / II 级 6 / III 级 10 / IV 级 14 / V 级 18；工作环境与神秘技术单列）；
 * 跨分支门控（如需先解锁「更好的工作环境-N」）也用 parentId 表达——分支首节点的
 * parent 指向门控节点。两个特殊门控见 {@link #gateSatisfied}：综合补剂=医疗大类全解锁；
 * 神秘技术=全树 75%。入口：{@code /sre:60s tech} 打开科技树界面。
 */
public final class SixtySecondsTechTree {

    /** 兜底解锁费用（正常节点都会被 {@link #costOf} 覆盖）。 */
    public static final int UNLOCK_COST = 3;

    /** 神秘技术入口门槛：非神秘节点解锁比例。 */
    public static final double MYSTIC_GATE_RATIO = 0.75;

    /**
     * 科技节点：父节点未解锁不能解锁本节点（分支链 + 跨分支门控统一用 parentId）。
     * 名称/描述用 {@code tech.noellesroles.sixty_seconds.<id>} 键。
     * category 为展示大类 id（{@code techcat.noellesroles.sixty_seconds.<category>} 键）。
     */
    public record TechNode(String id, int scrapCost, String parentId, String category) {
    }

    /** 展示大类顺序（科技树界面标签页）。 */
    public static final List<String> CATEGORIES = List.of(
            "survival", "agriculture", "cooking", "power", "purification",
            "defense", "raiding", "medical", "brewing", "metallurgy",
            "military", "base", "transport", "mystic");

    /** 静态科技表（两端共享，只发解锁状态）。 */
    public static final List<TechNode> NODES = buildNodes();

    private static List<TechNode> buildNodes() {
        List<TechNode> list = new ArrayList<>();
        // ── 生存与工具 ───────────────────────────────────────────────
        chain(list, "survival", null, "work_env_1", "work_env_2", "work_env_3", "work_env_4");
        chain(list, "survival", null, "materials_1", "materials_2", "materials_3");
        chain(list, "survival", null, "tools_1", "tools_2", "tools_3");
        // 背包在裁缝台制作 → 需先解锁「更好的工作环境-I」
        chain(list, "survival", "work_env_1", "backpack_1", "backpack_2", "backpack_3", "backpack_4", "backpack_5");
        // ── 农业 ────────────────────────────────────────────────────
        chain(list, "agriculture", null, "agri_1", "agri_2", "agri_3");
        chain(list, "agriculture", null, "planter_1", "planter_2");
        chain(list, "agriculture", "planter_1", "misc_planter_1", "misc_planter_2");
        chain(list, "agriculture", null, "fertilizer_1", "fertilizer_2");
        chain(list, "agriculture", null, "tobacco");
        // ── 炊事 ────────────────────────────────────────────────────
        chain(list, "cooking", null, "cooking_1", "cooking_2", "cooking_3", "cooking_4");
        chain(list, "cooking", null, "waste_1", "waste_2", "waste_3", "waste_4");
        // ── 电力 ────────────────────────────────────────────────────
        chain(list, "power", null, "power_1", "power_2", "power_3");
        chain(list, "power", "power_2", "power_facility_1", "power_facility_2");
        // ── 净化 ────────────────────────────────────────────────────
        chain(list, "purification", null, "water_collect_1", "water_collect_2", "water_collect_3");
        chain(list, "purification", null, "water_purify_1", "water_purify_2", "water_purify_3");
        chain(list, "purification", null, "tea");
        chain(list, "purification", null, "drinks");
        // ── 防御工事（高级工作台，需「更好的工作环境-II」）─────────────────
        chain(list, "defense", "work_env_2", "door_1", "door_2", "door_3", "door_4");
        chain(list, "defense", "work_env_2", "vault_1", "vault_2", "vault_3");
        chain(list, "defense", "work_env_2", "mob_defense_1", "mob_defense_2", "mob_defense_3");
        // ── 抄家技术（高级工作台，需「更好的工作环境-II」）─────────────────
        chain(list, "raiding", "work_env_2", "lockpick_craft_1", "lockpick_craft_2", "lockpick_craft_3");
        // ── 医疗（无菌台，需「更好的工作环境-I」）───────────────────────
        chain(list, "medical", "work_env_1", "med_materials_1", "med_materials_2");
        chain(list, "medical", "work_env_1", "drugs_1", "drugs_2", "drugs_3", "drugs_4", "drugs_5");
        chain(list, "medical", "work_env_1", "tonics_1", "tonics_2");
        chain(list, "medical", "work_env_1", "sanity_1", "sanity_2", "sanity_4");
        chain(list, "medical", "work_env_1", "decontam_1", "decontam_2", "decontam_3");
        // 综合补剂：需医疗大类其余节点全部解锁（特殊门控，见 gateSatisfied）
        chain(list, "medical", null, "omni_tonic");
        // ── 酿造（酿造台，需「更好的工作环境-II」）───────────────────────
        chain(list, "brewing", "work_env_2", "brew_1", "brew_2", "brew_3");
        chain(list, "brewing", "work_env_2", "potion_purify");
        // ── 冶金（冶金炉，需「更好的工作环境-II」）───────────────────────
        chain(list, "metallurgy", "work_env_2", "smelt_1", "smelt_2", "smelt_3");
        // ── 军械装备（需「更好的工作环境-III」）─────────────────────────
        chain(list, "military", "work_env_3", "armor_1", "armor_2", "armor_3", "armor_4");
        chain(list, "military", "work_env_3", "func_armor_1", "func_armor_2");
        chain(list, "military", "work_env_3", "melee_1", "melee_2", "melee_3");
        chain(list, "military", "work_env_3", "bullets_1", "bullets_2", "bullets_3");
        chain(list, "military", "work_env_3", "firearms_1", "firearms_2", "firearms_3");
        chain(list, "military", "work_env_3", "throwables_1", "throwables_2", "throwables_3");
        // 弓弩（军械大类，需「更好的工作环境-III」）：弓术链解锁弓/弩，箭矢工艺链解锁弹药
        chain(list, "military", "work_env_3", "archery_1", "archery_2", "archery_3");
        chain(list, "military", "archery_1", "arrow_craft_1", "arrow_craft_2");
        // ── 基地设施（车床，需「更好的工作环境-IV」）─────────────────────
        chain(list, "base", "work_env_4", "base_expand_1", "base_expand_2", "base_expand_3");
        chain(list, "base", "work_env_4", "base_facility_1", "base_facility_2", "base_facility_3");
        // ── 交通工具（需「更好的工作环境-IV」）──────────────────────────
        chain(list, "transport", "work_env_4", "fuel_1", "fuel_2");
        chain(list, "transport", "work_env_4", "horse_1", "horse_2");
        chain(list, "transport", "work_env_4", "vehicle_1", "vehicle_2", "vehicle_3", "vehicle_repair");
        // ── 神秘技术（全树 75% 门控，见 gateSatisfied）───────────────────
        chain(list, "mystic", null, "sacrifice_1", "sacrifice_2");
        chain(list, "mystic", "sacrifice_1", "undying_totem");
        chain(list, "mystic", "sacrifice_2", "revival_totem");
        return List.copyOf(list);
    }

    /** 追加一条分支链：首节点 parent=gate（可为 null），其余逐级前置，费用按 {@link #costOf}。 */
    private static void chain(List<TechNode> list, String category, String gate, String... ids) {
        String parent = gate;
        for (String id : ids) {
            list.add(new TechNode(id, costOf(id), parent, category));
            parent = id;
        }
    }

    /**
     * 节点解锁费用（废料）。基准：第一天全队约能搜到 40 个废料——
     * 第一天能点下 工作环境-I(2) + 七八个 I 级基础(各 3)，II 级起明显放缓。
     * <ul>
     *   <li>工作环境（全树主干门控）：I=2 起步价，II/III/IV=8/14/20。</li>
     *   <li>常规分支按罗马级别：I=3、II=6、III=10、IV=14、V=18（按 id 尾缀 _N 判定）。</li>
     *   <li>无级别单节点（茶艺/饮料/烟草等）：4~5；药剂净化 8；综合补剂 12。</li>
     *   <li>神秘技术（后期目标）：献祭 16、不死图腾 24、复活图腾 30。</li>
     * </ul>
     */
    private static int costOf(String id) {
        switch (id) {
            case "work_env_1": return 2;
            case "work_env_2": return 8;
            case "work_env_3": return 14;
            case "work_env_4": return 20;
            case "tea", "drinks": return 4;
            case "tobacco": return 5;
            case "potion_purify": return 8;
            case "omni_tonic": return 12;
            case "sacrifice_1", "sacrifice_2": return 16;
            case "undying_totem": return 24;
            case "revival_totem": return 30;
            default:
        }
        int tier = id.charAt(id.length() - 1) - '0';
        if (id.length() >= 2 && id.charAt(id.length() - 2) == '_' && tier >= 1 && tier <= 5) {
            return switch (tier) {
                case 1 -> 3;
                case 2 -> 6;
                case 3 -> 10;
                case 4 -> 14;
                default -> 18;
            };
        }
        return UNLOCK_COST;
    }

    private SixtySecondsTechTree() {
    }

    public static TechNode byId(String id) {
        for (TechNode node : NODES) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    public static boolean isUnlocked(SixtySecondsState.TeamData team, String techId) {
        return team != null && team.unlockedTech.contains(techId);
    }

    /**
     * 特殊门控（parentId 之外的额外条件，两端共用）：
     * <ul>
     *   <li>{@code omni_tonic} —— 医疗大类其余节点全部解锁；</li>
     *   <li>神秘技术大类首节点（{@code sacrifice_1}）—— 非神秘节点解锁数 ≥ 75%。</li>
     * </ul>
     */
    public static boolean gateSatisfied(TechNode node, Set<String> unlocked) {
        if ("arrow_craft_2".equals(node.id())) {
            return unlocked.contains("archery_2") && unlocked.contains("arrow_craft_1");
        }
        if ("omni_tonic".equals(node.id())) {
            for (TechNode other : NODES) {
                if ("medical".equals(other.category()) && !other.id().equals(node.id())
                        && !unlocked.contains(other.id())) {
                    return false;
                }
            }
            return true;
        }
        if ("sacrifice_1".equals(node.id())) {
            int total = 0;
            int have = 0;
            for (TechNode other : NODES) {
                if (!"mystic".equals(other.category())) {
                    total++;
                    if (unlocked.contains(other.id())) {
                        have++;
                    }
                }
            }
            return have >= (int) Math.ceil(total * MYSTIC_GATE_RATIO);
        }
        return true;
    }

    /** 解锁附带效果（服务端；蓝图随机解锁也走这里）：房门维护每级给家门 +1 级。 */
    public static void applyUnlockSideEffects(SixtySecondsState.TeamData team, String techId) {
        switch (techId) {
            case "door_1", "door_2", "door_3", "door_4" -> team.doorLevel = Math.min(4, team.doorLevel + 1);
            default -> {
            }
        }
    }

    /** 打开科技树界面（命令入口）。 */
    public static void open(ServerPlayer player) {
        if (!SixtySecondsMod.isActive(player.level())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"), false);
            return;
        }
        sendState(player);
    }

    private static void sendState(ServerPlayer player) {
        SixtySecondsState.Data data = SixtySecondsState.get(player.serverLevel());
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        List<String> unlocked = team == null ? List.of() : new ArrayList<>(team.unlockedTech);
        ServerPlayNetworking.send(player, new OpenTechTreeS2CPacket(unlocked.toArray(new String[0])));
    }

    /** C2S 解锁请求：校验前置科技 + 特殊门控 + 背包废料，消耗后解锁并通知全队。 */
    public static void handleUnlock(ServerPlayer player, String techId) {
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        TechNode node = byId(techId);
        if (team == null || node == null || team.unlockedTech.contains(techId)) {
            return;
        }
        if ((node.parentId() != null && !team.unlockedTech.contains(node.parentId()))
                || !gateSatisfied(node, team.unlockedTech)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.tech_parent_locked"), true);
            return;
        }
        Item scrap = org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP;
        if (!consume(player, scrap, node.scrapCost())) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.tech_no_scrap", node.scrapCost()), true);
            return;
        }
        team.unlockedTech.add(techId);
        applyUnlockSideEffects(team, techId);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        Component name = Component.translatable("tech.noellesroles.sixty_seconds." + techId);
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.tech_unlocked",
                        player.getGameProfile().getName(), name).withStyle(ChatFormatting.GREEN), false);
            }
        }
        sendState(player); // 刷新界面
    }

    /** 统计并消耗背包中的指定物品；不足则不动返回 false。 */
    public static boolean consume(ServerPlayer player, Item item, int count) {
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        if (have < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        return true;
    }
}

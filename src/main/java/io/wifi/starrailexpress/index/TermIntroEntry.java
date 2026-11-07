package io.wifi.starrailexpress.index;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * U 键介绍界面「专有名词」类别的条目。
 * <p>
 * 全部文案都放在独立命名空间 {@code assets/term_intro/lang/} 下，代码里不写死文本：
 * <ul>
 *     <li>{@code term_intro.category} —— 类别显示名</li>
 *     <li>{@code term_intro.<id>.name} —— 词条名</li>
 *     <li>{@code term_intro.<id>.desc} —— 词条正文（用 {@code \n} 分段）</li>
 * </ul>
 * 语言文件会被客户端原版 ResourceManager 与专用服务器的 ServerLanguageManager 自动合并，
 * 因此新增词条只需要改语言文件与 {@link #ALL}，无需注册任何东西。
 */
public record TermIntroEntry(String id) {

    public static final String NAMESPACE = "term_intro";

    /** 词条顺序即界面中的展示顺序。 */
    public static final List<TermIntroEntry> ALL = List.of(
            new TermIntroEntry("faction"),
            new TermIntroEntry("civilian_faction"),
            new TermIntroEntry("sheriff_faction"),
            new TermIntroEntry("killer_faction"),
            new TermIntroEntry("neutral_faction"),
            new TermIntroEntry("pro_innocent_neutral"),
            new TermIntroEntry("pro_killer_neutral"),
            new TermIntroEntry("special_neutral"),
            new TermIntroEntry("independent_neutral"),
            new TermIntroEntry("event_neutral"),
            new TermIntroEntry("modifier"),
            new TermIntroEntry("bound_exclusive_spawn"),
            new TermIntroEntry("map_exclusive_role"),
            new TermIntroEntry("player_count_limit"),
            new TermIntroEntry("occupied_slots"),
            new TermIntroEntry("mood_sanity"),
            new TermIntroEntry("instinct_vision"),
            new TermIntroEntry("game_time"),
            new TermIntroEntry("stamina"),
            new TermIntroEntry("death_reason"),
            new TermIntroEntry("safe_time"),
            new TermIntroEntry("skill_switch"),
            new TermIntroEntry("victory_settlement"),
            new TermIntroEntry("coins"),
            new TermIntroEntry("map_status_bar"),
            new TermIntroEntry("warmth"),
            new TermIntroEntry("thirst"),
            new TermIntroEntry("hunger"),
            new TermIntroEntry("pollution"),
            new TermIntroEntry("special_vanilla_weapon"),
            new TermIntroEntry("virtual_health"),
            new TermIntroEntry("task"),
            new TermIntroEntry("passive_income"),
            new TermIntroEntry("corpse"),
            new TermIntroEntry("minigame_sabotage_task"),
            new TermIntroEntry("manhole"),
            new TermIntroEntry("fog"),
            new TermIntroEntry("shield"),
            new TermIntroEntry("normal_shield"),
            new TermIntroEntry("timed_shield"),
            new TermIntroEntry("weak_shield"),
            new TermIntroEntry("virtual_shield"),
            new TermIntroEntry("poison_fake_poison"),
            new TermIntroEntry("infection"),
            new TermIntroEntry("damage_immunity"),
            new TermIntroEntry("invincibility"),
            new TermIntroEntry("domain"),
            new TermIntroEntry("meeting"),
            new TermIntroEntry("event"),
            new TermIntroEntry("disguise"));

    public static final int CATEGORY_COLOR = 0xFFFFAA44;

    public ResourceLocation identifier() {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
    }

    private String key(String suffix) {
        return NAMESPACE + "." + id + "." + suffix;
    }

    public MutableComponent getName() {
        return Component.translatable(key("name"));
    }

    public MutableComponent getNameWithColor() {
        return getName().copy().withStyle(ChatFormatting.AQUA);
    }

    /** 完整正文，内部用 {@code \n} 已经分好段，渲染时由 font.split 自动换行。 */
    public MutableComponent getDescription() {
        return Component.translatable(key("desc"));
    }

    /** 「简介」页用正文的第一段作为摘要，避免再维护一套简略文本。 */
    public MutableComponent getSimpleDescription() {
        String full = getDescription().getString();
        int lineBreak = full.indexOf('\n');
        return Component.literal(lineBreak >= 0 ? full.substring(0, lineBreak) : full);
    }
}

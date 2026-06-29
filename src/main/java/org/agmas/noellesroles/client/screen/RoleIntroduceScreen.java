package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SREAbstractInfoClass;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.customrole.CustomNormalRole;
import io.wifi.starrailexpress.index.TMMDescItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class RoleIntroduceScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 【模式切换】四种模式：全部、谋杀、修机、过滤
    // ══════════════════════════════════════════════════════════════════
    public enum IntroductionGameMode {
        ALL("screen.roleintroduce.mode.all", 0xFF55DD88),
        MURDER("screen.roleintroduce.mode.murder", 0xFFCC2233),
        REPAIR("screen.roleintroduce.mode.repair", 0xFF44AACC),
        FILTER("screen.roleintroduce.mode.flag", 0xFF11AA33);

        public final String labelKey;
        public final int color;

        IntroductionGameMode(String labelKey, int color) {
            this.labelKey = labelKey;
            this.color = color;
        }
    }

    private IntroductionGameMode currentMode = IntroductionGameMode.FILTER;
    public static HashSet<String> filterFlags = new HashSet<>();

    private int modeButtonX = 0;
    private int modeButtonY = 0;
    private int modeButtonW = 0;
    private int modeButtonH = 16;
    private static final int MODE_GAP = 4;

    Screen parent = null;

    public static class RoleCategory {
        public final String labelKey;
        public final int activeColor;
        public final Predicate<Object> filter;

        public RoleCategory(String labelKey, int activeColor, Predicate<Object> filter) {
            this.labelKey = labelKey;
            this.activeColor = activeColor;
            this.filter = filter;
        }
    }

    public static final List<RoleCategory> CATEGORIES = new ArrayList<>();
    static {
        CATEGORIES.add(new RoleCategory("screen.roleintroduce.category.all", 0xFFEEEEEE, item -> true));
        CATEGORIES.add(new RoleCategory("display.type.role.innocent", 0xFF44BB66,
                item -> item instanceof SRERole r && (PlayerRoleWeightManager.getRoleType(r) == 0
                        || PlayerRoleWeightManager.getRoleType(r) == 1)));
        CATEGORIES.add(new RoleCategory("display.type.role.vigilante", 0xFF22BBCC,
                item -> item instanceof SRERole r && PlayerRoleWeightManager.getRoleType(r) == 5));
        CATEGORIES.add(new RoleCategory("display.type.role.neutral", 0xFFCCAA22,
                item -> item instanceof SRERole r && PlayerRoleWeightManager.getRoleType(r) == 2));
        CATEGORIES.add(new RoleCategory("display.type.role.neutral_for_killer", 0xFFAA44CC,
                item -> item instanceof SRERole r && PlayerRoleWeightManager.getRoleType(r) == 3));
        CATEGORIES.add(new RoleCategory("display.type.role.killer", 0xFFCC2233,
                item -> item instanceof SRERole r && PlayerRoleWeightManager.getRoleType(r) == 4));
        CATEGORIES.add(new RoleCategory("screen.roleintroduce.category.modifier", 0xFF8877BB,
                item -> item instanceof SREModifier));
        CATEGORIES.add(new RoleCategory("screen.roleintroduce.category.item", 0x55FF22BB, item -> item instanceof Item));
        CATEGORIES.add(new RoleCategory("screen.roleintroduce.category.sponsor", 0xFFFF66AA,
                item -> item instanceof Item it
                        && io.wifi.starrailexpress.client.data.ClientSponsorCache.isSponsorPlush(it)));
    }

    private static final int MAX_USABLE_WIDTH = 700;
    private static final float USABLE_RATIO = 0.9f;
    private static final float LEFT_RATIO = 0.30f;

    private static final int PANEL_PAD = 6;
    private static final int CARD_H = 42;
    private static final int CARD_SPACING = 4;
    private static final int ICON_SIZE = 26;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int BANNER_H = 26;

    private static final int TOP_BAR_H = 18;
    private static final int TOP_BAR_GAP = 6;
    private static final int TAB_GAP = 2;

    private static final int DETAIL_TAB_H = 18;
    private static final int DETAIL_TAB_GAP = 2;

    private static final ResourceLocation ICON_DEFAULT = SRE.watheId("textures/gui/sprites/hud/mood_happy.png");
    private static final Map<String, ResourceLocation> TYPE_ICON_MAP = new HashMap<>();
    static {
        TYPE_ICON_MAP.put("role_1", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
        TYPE_ICON_MAP.put("role_2", Noellesroles.id("textures/gui/sprites/hud/mood_neu.png"));
        TYPE_ICON_MAP.put("role_3", Noellesroles.id("textures/gui/sprites/hud/mood_jester.png"));
        TYPE_ICON_MAP.put("role_4", SRE.watheId("textures/gui/sprites/hud/mood_killer.png"));
        TYPE_ICON_MAP.put("role_5", Noellesroles.id("textures/gui/sprites/hud/mood_vig.png"));
        TYPE_ICON_MAP.put("modifier", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
    }

    private static ResourceLocation getTypeIcon(Object role) {
        if (role instanceof SRERole rrole)
            return TYPE_ICON_MAP.getOrDefault("role_" + PlayerRoleWeightManager.getRoleType(rrole), ICON_DEFAULT);
        return TYPE_ICON_MAP.getOrDefault("modifier", ICON_DEFAULT);
    }

    private final List<SRERole> availableRoles = new ArrayList<>();
    private final List<Object> filteredItems = new ArrayList<>();

    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH;
    private int leftX, rightX;
    private int topBarY;

    private int selectedCategoryIndex = 0;
    private final int[] tabX = new int[64];
    private final int[] tabW = new int[64];

    private final int[] modeBtnX = new int[IntroductionGameMode.values().length + 2];
    private final int[] modeBtnW = new int[IntroductionGameMode.values().length + 2];

    private int listScrollOffset = 0;
    private int maxListScroll = 0;
    private boolean isDraggingListScroll = false;
    private double dragListStartY = 0;
    private int dragListStartOffset = 0;

    private final Map<Object, Float> hoverAnims = new HashMap<>();

    private Object selectedRole = null;
    private int activeTabIndex = 0;
    private final List<DetailTab> tabs = new ArrayList<>();

    private enum FocusArea {
        SEARCH, CATEGORY_BAR, LEFT_LIST, MODE_BUTTONS, RIGHT_TABS
    }
    private FocusArea currentFocusArea = FocusArea.SEARCH;

    private boolean isDraggingDetailScroll = false;
    private double dragDetailStartY = 0;
    private int dragDetailStartOffset = 0;

    private EditBox searchWidget = null;
    private String searchContent = null;

    public RoleIntroduceScreen() {
        super(Component.translatable("gui.roleintroduce.select_role.title"));
        availableRoles.addAll(Noellesroles.getAllRolesSorted(true));
        filterFlags.clear();
    }

    public RoleIntroduceScreen(Screen parent) {
        this();
        this.parent = parent;
    }

    public RoleIntroduceScreen(Screen parent, SRERole sreRole) {
        this();
        this.parent = parent;
        this.selectedRole = sreRole;
    }

    public RoleIntroduceScreen(Screen parent, SREModifier modifier) {
        this();
        this.parent = parent;
        this.selectedRole = modifier;
    }

    private static SRERole getRole(Player player) {
        if (SREClient.gameComponent != null) return SREClient.gameComponent.getRole(player);
        return null;
    }

    public RoleIntroduceScreen(Player player) {
        this(player, getRole(player));
    }

    public RoleIntroduceScreen(Player player, SRERole sreRole) {
        this();
        this.selectedRole = sreRole;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        initSearchBox();
        refreshFilter();
        if (selectedRole == null && !filteredItems.isEmpty()) selectedRole = filteredItems.get(0);
        onSelectionChanged();
        setFocusArea(FocusArea.SEARCH);
    }

    private void computeLayout() {
        boolean isSmall = this.height <= 300;
        usableWidth = Math.min((int) (width * USABLE_RATIO), MAX_USABLE_WIDTH);
        leftW = (int) (usableWidth * LEFT_RATIO);
        rightW = usableWidth - leftW;
        panelX = (width - usableWidth) / 2;
        panelY = 48;
        panelH = isSmall ? height - panelY - 20 : height - panelY - 42;
        leftX = panelX;
        rightX = panelX + leftW;
        topBarY = panelY - TOP_BAR_H - 4;

        int totalModeW = 0;
        for (IntroductionGameMode mode : IntroductionGameMode.values())
            totalModeW += font.width(Component.translatable(mode.labelKey)) + 20 + MODE_GAP;
        totalModeW -= MODE_GAP;
        modeButtonX = panelX + PANEL_PAD * 2;
        modeButtonY = panelY + panelH - 24;
        modeButtonW = totalModeW;
    }

    private void initSearchBox() {
        int sx = leftX, sw = leftW - 2;
        if (searchWidget == null) {
            searchWidget = new EditBox(font, sx, topBarY, sw, TOP_BAR_H, Component.empty());
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder").withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                searchContent = text.isEmpty() ? null : text;
                listScrollOffset = 0;
                refreshFilter();
                selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                onSelectionChanged();
            });
        } else {
            searchWidget.setPosition(sx, topBarY);
            searchWidget.setWidth(sw);
            removeWidget(searchWidget);
        }
        addRenderableWidget(searchWidget);
        boolean noResult = filteredItems.isEmpty() && searchContent != null && !searchContent.isEmpty();
        searchWidget.setTextColor(noResult ? 0xFFAA2222 : 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void refreshFilter() {
        filteredItems.clear();
        RoleCategory cat = currentCategory();
        for (SRERole role : availableRoles) {
            if (!cat.filter.test(role) || !matchesMode(role)) continue;
            String name = RoleUtils.getRoleName(role).getString();
            if (searchContent == null || name.toLowerCase().contains(searchContent.toLowerCase())
                    || role.identifier().toString().contains(searchContent.toLowerCase())
                    || PinYinUtils.contains(searchContent, name))
                filteredItems.add(role);
        }
        for (SREModifier mod : HMLModifiers.MODIFIERS) {
            if (!cat.filter.test(mod) || !matchesModifierMode(mod)) continue;
            String name = mod.getName().getString();
            if (searchContent == null || name.toLowerCase().contains(searchContent.toLowerCase())
                    || mod.identifier().toString().contains(searchContent.toLowerCase())
                    || PinYinUtils.contains(searchContent, name))
                filteredItems.add(mod);
        }
        for (Item item : TMMDescItems.introItems) {
            if (!cat.filter.test(item) || !matchesItemMode(item)) continue;
            String name = item.getDescription().getString();
            if (searchContent == null || name.toLowerCase().contains(searchContent.toLowerCase())
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(searchContent.toLowerCase())
                    || PinYinUtils.contains(searchContent, name))
                filteredItems.add(item);
        }
        for (Item item : io.wifi.starrailexpress.client.data.ClientSponsorCache.getPlushItems()) {
            if (!cat.filter.test(item)) continue;
            String name = item.getDescription().getString();
            if (searchContent == null || name.toLowerCase().contains(searchContent.toLowerCase())
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(searchContent.toLowerCase())
                    || PinYinUtils.contains(searchContent, name))
                filteredItems.add(item);
        }
        int totalH = filteredItems.size() * (CARD_H + CARD_SPACING) - CARD_SPACING;
        maxListScroll = Math.max(0, totalH - listAreaH());
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
    }

    private boolean matchesMode(SRERole role) {
        return switch (currentMode) {
            case ALL -> true;
            case MURDER -> !isRepairRole(role) && !role.isOtherModeRole();
            case REPAIR -> isRepairRole(role);
            case FILTER -> role.isFlagWithInner(filterFlags);
        };
    }

    private boolean isRepairRole(SRERole role) { return role instanceof RepairRole; }

    private boolean matchesModifierMode(SREModifier mod) {
        return switch (currentMode) {
            case ALL -> true;
            case MURDER -> !mod.isOtherModeRole();
            case REPAIR -> false;
            case FILTER -> mod.isFlagWithInner(filterFlags);
        };
    }

    private boolean matchesItemMode(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return switch (currentMode) {
            case ALL -> true;
            case MURDER -> !isRepairItem(path) && !isOtherModeItem(path);
            case REPAIR -> isRepairItem(path);
            case FILTER -> filterFlags.isEmpty() || false;
        };
    }

    private boolean isRepairItem(String path) {
        return path.equals("repair_toolbox") || path.equals("spare_parts") || path.equals("rescue_flare")
                || path.equals("hunter_chain") || path.equals("hunter_pulse") || path.equals("hunter_blink")
                || path.equals("hunter_jammer") || path.equals("smoke_pellet") || path.equals("decoy_beacon")
                || path.equals("escape_grapple");
    }

    private boolean isOtherModeItem(String path) {
        return path.equals("magnifying_glass") || path.equals("chewing") || path.equals("clip")
                || path.equals("steel_ball") || path.equals("reversing_card") || path.equals("telephone")
                || path.equals("hot_potato");
    }

    private RoleCategory currentCategory() {
        return (selectedCategoryIndex >= 0 && selectedCategoryIndex < CATEGORIES.size())
                ? CATEGORIES.get(selectedCategoryIndex) : CATEGORIES.get(0);
    }

    private int listAreaH() { return panelH - PANEL_PAD * 2 - 24; }

    // ══════════════════════════════════════════════════════════════════
    // 标签页系统
    // ══════════════════════════════════════════════════════════════════
    private interface DetailTab {
        Component getTitle();
        boolean isVisible();
        void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);
        int getScrollOffset();
        int getMaxScroll();
        void setScrollOffset(int offset);
        int getContentHeight();
        void onSwitchTo();
        default int getColor() { return 0xFFAA88CC; }
        default void onSwitchAway() {}
    }

    private abstract class AbstractTextTab implements DetailTab {
        protected List<FormattedCharSequence> lines = new ArrayList<>();
        protected int scrollOffset = 0;
        protected int maxScroll = 0;

        protected int getLineHeight() { return font.lineHeight + 2; }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
            g.enableScissor(x, y, x + w, y + h);
            int lineY = y - scrollOffset;
            for (FormattedCharSequence line : lines) {
                if (lineY + getLineHeight() > y && lineY < y + h) {
                    g.drawString(font, line, x, lineY, 0xFFFFFF);
                }
                lineY += getLineHeight();
            }
            g.disableScissor();
            int totalH = getContentHeight();
            maxScroll = Math.max(0, totalH - h);
        }

        @Override public int getScrollOffset() { return scrollOffset; }
        @Override public int getMaxScroll() { return maxScroll; }
        @Override public void setScrollOffset(int offset) { this.scrollOffset = Mth.clamp(offset, 0, maxScroll); }
        @Override public int getContentHeight() { return lines.size() * getLineHeight(); }
        @Override public void onSwitchTo() { prepareLines(); scrollOffset = 0; }
        protected abstract void prepareLines();
    }

    private class ShopTab implements DetailTab {
        private int scrollOffset = 0;
        private int maxScroll = 0;
        private final List<ShopEntry> entries = new ArrayList<>();
        private static final int ITEM_H = 32;
        private static final int TEXT_GAP = 2;

        @Override public Component getTitle() { return Component.translatable("screen.roleintroduce.detail.shop"); }

        @Override public boolean isVisible() {
            if (!(selectedRole instanceof SRERole)) return false;
            entries.clear();
            entries.addAll(LimitedInventoryScreen.getRoleShopEntries((SRERole) selectedRole));
            return !entries.isEmpty();
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
            g.enableScissor(x, y, x + w, y + h);
            int itemY = y - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                ShopEntry entry = entries.get(i);
                if (itemY + ITEM_H > y && itemY < y + h) {
                    ItemStack stack = entry.stack();
                    g.renderItem(stack, x + 4, itemY + (ITEM_H - 16) / 2);
                    Component nameText = stack.getHoverName().copy().withStyle(ChatFormatting.WHITE);
                    Component priceText = Component.translatable("screen.roleintroduce.shop.price", entry.price())
                            .withStyle(ChatFormatting.GOLD);
                    int textX = x + 24;
                    int lineH = font.lineHeight;
                    int blockHeight = lineH * 2 + TEXT_GAP;
                    int blockTop = itemY + (ITEM_H - blockHeight) / 2;
                    g.drawString(font, nameText, textX, blockTop, 0xFFFFFF);
                    g.drawString(font, priceText, textX, blockTop + lineH + TEXT_GAP, 0xFFFF55);
                    if (i < entries.size() - 1) {
                        g.fill(x + 4, itemY + ITEM_H - 1, x + w - 4, itemY + ITEM_H, 0x20FFFFFF);
                    }
                }
                itemY += ITEM_H;
            }
            g.disableScissor();
            int totalH = entries.size() * ITEM_H;
            maxScroll = Math.max(0, totalH - h);
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                int relY = mouseY - y + scrollOffset;
                int idx = relY / ITEM_H;
                if (idx >= 0 && idx < entries.size()) {
                    ItemStack stack = entries.get(idx).stack();
                    List<Component> tooltipLines;
                    try {
                        tooltipLines = stack.getTooltipLines(Item.TooltipContext.EMPTY, minecraft.player, TooltipFlag.NORMAL);
                    } catch (Exception e) {
                        tooltipLines = List.of(Component.translatable("screen.roleintroduce.error", e.getMessage()));
                    }
                    g.renderTooltip(font, tooltipLines, stack.getTooltipImage(), mouseX, mouseY);
                }
            }
        }

        @Override public int getScrollOffset() { return scrollOffset; }
        @Override public int getMaxScroll() { return maxScroll; }
        @Override public void setScrollOffset(int offset) { this.scrollOffset = Mth.clamp(offset, 0, maxScroll); }
        @Override public int getContentHeight() { return entries.size() * ITEM_H; }
        @Override public void onSwitchTo() {
            scrollOffset = 0;
            entries.clear();
            if (selectedRole instanceof SRERole) entries.addAll(LimitedInventoryScreen.getRoleShopEntries((SRERole) selectedRole));
        }
    }

    private void buildTabs() {
        tabs.clear();
        if (selectedRole == null) return;
        int textW = rightW - PANEL_PAD * 2 - SCROLL_W - 4;

        // 简介
        tabs.add(new AbstractTextTab() {
            @Override public Component getTitle() { return Component.translatable("screen.roleintroduce.detail.intro"); }
            @Override public boolean isVisible() { return true; }
            @Override protected void prepareLines() {
                lines.clear();
                int dashCount = textW / Math.max(1, font.width("─"));
                String dashes = "─".repeat(dashCount);

                // 名称 + ID
                Component nameComp = RoleUtils.getRoleOrModifierOrItemNameWithColor(selectedRole).copy().withStyle(ChatFormatting.BOLD);
                Component idComp = Component.translatable("screen.roleintroduce.detail.name.id.warp",
                        RoleUtils.getRoleOrModifierOrItemIdentifier(selectedRole).toString()).withStyle(ChatFormatting.DARK_GRAY);
                lines.addAll(font.split(nameComp.copy().append(" ").append(idComp), textW));

                // 目标
                lines.add(FormattedCharSequence.EMPTY);
                if (selectedRole instanceof SRERole role) {
                    var rid = role.identifier();
                    String path = rid.getPath();
                    String goalKey = "announcement.star.goals." + path;
                    if (selectedRole instanceof CustomNormalRole) {
                        var cd = io.wifi.starrailexpress.customrole.CustomRoleLoader.getCustomRoleData(path);
                        if (cd == null) cd = io.wifi.starrailexpress.client.network.CustomRoleClientNetwork.getSyncedRole(path);
                        if (cd != null && !cd.goals.isEmpty()) lines.addAll(font.split(Component.literal(cd.goals), textW));
                        else lines.addAll(font.split(Component.translatable(goalKey), textW));
                    } else {
                        lines.addAll(font.split(Component.translatable(goalKey), textW));
                    }
                } else if (selectedRole instanceof Item it) {
                    lines.addAll(font.split(it.getDescription(), textW));
                } else if (selectedRole instanceof SREModifier mod) {
                    lines.addAll(font.split(mod.getName(), textW));
                }

                // 简介文本（可选，语言文件键 star.intro.<type>.<path>）
                String introKey = "star.intro." + getObjectType(selectedRole) + "." + getObjectPath(selectedRole);
                if (Language.getInstance().has(introKey)) {
                    lines.add(FormattedCharSequence.EMPTY);
                    lines.addAll(font.split(Component.translatable(introKey).withStyle(ChatFormatting.WHITE), textW));
                }

                // 分割线 + 标签
                if (selectedRole instanceof SREAbstractInfoClass flagInfoable && !flagInfoable.getFlags().isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                    lines.addAll(font.split(Component.literal(dashes).withStyle(ChatFormatting.DARK_GRAY), textW));
                    lines.add(FormattedCharSequence.EMPTY);
                    lines.addAll(font.split(Component.translatable("screen.roleintroduce.detail.flags")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), textW));
                    lines.addAll(font.split(getFlagText(flagInfoable).withStyle(ChatFormatting.WHITE), textW));
                }
            }
        });

        // 详细介绍
        tabs.add(new AbstractTextTab() {
            @Override public Component getTitle() { return Component.translatable("screen.roleintroduce.detail.description"); }
            @Override public boolean isVisible() { return true; }
            @Override protected void prepareLines() {
                lines.clear();
                if (selectedRole != null)
                    lines.addAll(font.split(RoleUtils.getRoleOrModifierOrItemDescription(selectedRole).copy()
                            .withStyle(ChatFormatting.WHITE), textW));
            }
        });

        // 商店
        tabs.add(new ShopTab());

        // 小故事
        tabs.add(new AbstractTextTab() {
            @Override public Component getTitle() { return Component.translatable("screen.roleintroduce.detail.story"); }
            @Override public boolean isVisible() {
                if (selectedRole == null) return false;
                String key = "star.story." + getObjectType(selectedRole) + "." + getObjectPath(selectedRole);
                return Language.getInstance().has(key);
            }
            @Override protected void prepareLines() {
                lines.clear();
                String key = "star.story." + getObjectType(selectedRole) + "." + getObjectPath(selectedRole);
                if (Language.getInstance().has(key))
                    lines.addAll(font.split(Component.translatable(key).withStyle(ChatFormatting.WHITE), textW));
            }
        });

        // 设置
        tabs.add(new AbstractTextTab() {
            @Override public Component getTitle() { return Component.translatable("screen.roleintroduce.detail.settings"); }
            @Override public boolean isVisible() {
                if (selectedRole == null) return false;
                String settingsKey = "star.settings." + getObjectType(selectedRole) + "." + getObjectPath(selectedRole);
                return Language.getInstance().has(settingsKey);
            }
            @Override protected void prepareLines() {
                lines.clear();
                String settingsKey = "star.settings." + getObjectType(selectedRole) + "." + getObjectPath(selectedRole);
                if (Language.getInstance().has(settingsKey))
                    lines.addAll(font.split(Component.translatable(settingsKey).withStyle(ChatFormatting.WHITE), textW));
            }
        });
    }

    private void onSelectionChanged() {
        buildTabs();
        activeTabIndex = 0;
        if (!tabs.isEmpty()) tabs.get(activeTabIndex).onSwitchTo();
    }

    private static MutableComponent getFlagText(SREAbstractInfoClass flagInfoable) {
        return ComponentUtils.formatList(flagInfoable.getFlags(), Component.literal(", "),
                t -> Component.translatable("screen.roleintroduce.flag." + t));
    }

    private String getObjectPath(Object it) {
        if (it instanceof Item) return RoleUtils.getRoleOrModifierOrItemIdentifier(it).toLanguageKey();
        return RoleUtils.getRoleOrModifierOrItemIdentifier(it).getPath();
    }

    private String getObjectType(Object it) {
        if (it instanceof Item) return "item";
        if (it instanceof SREModifier) return "modifier";
        if (it instanceof SRERole) return "role";
        return "unknown";
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderLeftPanel(g, mouseX, mouseY);
        renderRightPanel(g, mouseX, mouseY);
        boolean isSmall = this.height <= 300;

        int catBarX = rightX + TOP_BAR_GAP;
        int catBarW = rightW - TOP_BAR_GAP;
        renderCategoryBar(g, mouseX, mouseY, catBarX, topBarY, catBarW, TOP_BAR_H);
        g.fillGradient(0, 0, width, topBarY - 4, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, this.title, width / 2, 8, 0xF5E8C8);
        if (isSmall)
            g.drawCenteredString(font, Component.translatable("screen.roleintroduce.hint").withStyle(ChatFormatting.GRAY), width / 2, height - 15, 0x9E8B6E);
        else
            g.drawCenteredString(font, Component.translatable("screen.roleintroduce.hint").withStyle(ChatFormatting.GRAY), width / 2, height - 24, 0x9E8B6E);
        renderModeButtons(g, mouseX, mouseY, leftW - PANEL_PAD * 4);
    }

    private void renderModeButtons(GuiGraphics g, int mouseX, int mouseY, int maxWidth) {
        IntroductionGameMode[] modes = IntroductionGameMode.values();
        int n = modes.length;
        if (n == 0) return;

        int[] naturalW = new int[n];
        int totalNaturalWidth = 0;
        for (int i = 0; i < n; i++) {
            naturalW[i] = font.width(Component.translatable(modes[i].labelKey)) + 20;
            totalNaturalWidth += naturalW[i];
        }
        int totalGap = MODE_GAP * (n - 1);
        int totalNaturalWithGap = totalNaturalWidth + totalGap;
        float scale = 1.0f;
        if (totalNaturalWithGap > maxWidth) {
            int availableForButtons = maxWidth - totalGap;
            scale = availableForButtons > 0 ? (float) availableForButtons / totalNaturalWidth : 0;
        }

        int totalScaledWidth = 0;
        int[] scaledW = new int[n];
        for (int i = 0; i < n; i++) {
            scaledW[i] = (int) (naturalW[i] * scale);
            totalScaledWidth += scaledW[i];
        }
        totalScaledWidth += totalGap;

        int startX = modeButtonX + (maxWidth - totalScaledWidth) / 2;
        int curX = startX, curY = modeButtonY, btnH = modeButtonH;
        for (int i = 0; i < n; i++) {
            int btnW = scaledW[i];
            modeBtnX[i] = curX;
            modeBtnW[i] = btnW;

            String label = Component.translatable(modes[i].labelKey).getString();
            String truncated = font.plainSubstrByWidth(label, Math.max(4, btnW - 8));
            boolean isActive = (modes[i] == currentMode);
            boolean isHovered = !isActive && isInRect(mouseX, mouseY, curX, curY, btnW, btnH);

            if (isActive) {
                g.fillGradient(curX, curY, curX + btnW, curY + btnH,
                        blendColors(0xFF1A1008, modes[i].color, 0.55f), blendColors(0xFF120A04, modes[i].color, 0.30f));
                g.fill(curX, curY + btnH - 2, curX + btnW, curY + btnH, modes[i].color);
                g.fill(curX, curY, curX + 1, curY + btnH, (modes[i].color & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + btnW - 1, curY, curX + btnW, curY + btnH, (modes[i].color & 0x00FFFFFF) | 0xAA000000);
            } else if (isHovered) {
                g.fillGradient(curX, curY, curX + btnW, curY + btnH,
                        blendColors(0xFF1A1008, modes[i].color, 0.25f), blendColors(0xFF120A04, modes[i].color, 0.12f));
                g.renderOutline(curX, curY, btnW, btnH, (modes[i].color & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, curY, curX + btnW, curY + btnH, 0x551A1008);
                g.renderOutline(curX, curY, btnW, btnH, 0x558B6914);
            }

            int textColor = isActive ? (modes[i].color | 0xFF000000) : isHovered ? 0xFFFFF4DC : 0xFF9E8B6E;
            g.drawCenteredString(font, truncated, curX + btnW / 2, curY + (btnH - font.lineHeight) / 2, textColor);
            curX += btnW + MODE_GAP;
        }
    }

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, leftX, panelY, leftW, panelH);

        int areaX = leftX + PANEL_PAD, areaY = panelY + PANEL_PAD;
        int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2, areaH = listAreaH();
        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        for (int i = 0; i < filteredItems.size(); i++) {
            Object role = filteredItems.get(i);
            int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
            if (cardY + CARD_H < areaY || cardY > areaY + areaH) continue;

            boolean hovered = isInRect(mouseX, mouseY, areaX, cardY, areaW, CARD_H);
            boolean selected = role.equals(selectedRole);
            float anim = hoverAnims.getOrDefault(role, 0f);
            anim = Mth.lerp(0.3f, anim, (hovered && !selected) ? 1f : 0f);
            hoverAnims.put(role, anim);
            renderListCard(g, role, areaX, cardY, areaW, CARD_H, anim, selected);
        }
        g.disableScissor();

        if (filteredItems.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.noellesroles.search.empty").withStyle(ChatFormatting.RED),
                    leftX + leftW / 2, areaY + areaH / 2, 0xFF5555);
        }

        int sbX = leftX + leftW - PANEL_PAD - SCROLL_W;
        int totalH = Math.max(1, filteredItems.size() * (CARD_H + CARD_SPACING));
        renderVScrollbar(g, sbX, areaY, areaH, listScrollOffset, maxListScroll, totalH, mouseX, mouseY, isDraggingListScroll);
    }

    private void renderCategoryBar(GuiGraphics g, int mouseX, int mouseY, int barX, int barY, int barW, int barH) {
        int n = CATEGORIES.size();
        int[] naturalW = new int[n];
        int totalNatural = TAB_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            naturalW[i] = font.width(Component.translatable(CATEGORIES.get(i).labelKey)) + 10;
            totalNatural += naturalW[i];
        }
        float scale = totalNatural > barW ? (float) barW / totalNatural : 1f;
        int curX = barX;
        for (int i = 0; i < n; i++) {
            int tw = (int) (naturalW[i] * scale);
            tabX[i] = curX; tabW[i] = tw;

            boolean active = (i == selectedCategoryIndex);
            boolean hovered = !active && isInRect(mouseX, mouseY, curX, barY, tw, barH);
            int baseColor = CATEGORIES.get(i).activeColor;

            if (active) {
                g.fillGradient(curX, barY, curX + tw, barY + barH,
                        blendColors(0xFF1A1008, baseColor, 0.50f), blendColors(0xFF120A04, baseColor, 0.28f));
                g.fill(curX, barY + barH - 2, curX + tw, barY + barH, baseColor);
                g.fill(curX, barY, curX + 1, barY + barH, (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + tw - 1, barY, curX + tw, barY + barH, (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + 1, barY, curX + tw - 1, barY + 1, (baseColor & 0x00FFFFFF) | 0x55000000);
            } else if (hovered) {
                g.fillGradient(curX, barY, curX + tw, barY + barH,
                        blendColors(0xFF1A1008, baseColor, 0.22f), blendColors(0xFF120A04, baseColor, 0.10f));
                g.fill(curX, barY + barH - 1, curX + tw, barY + barH, (baseColor & 0x00FFFFFF) | 0x66000000);
                g.renderOutline(curX, barY, tw, barH, (baseColor & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, barY, curX + tw, barY + barH, 0x331A1008);
                g.renderOutline(curX, barY, tw, barH, 0x338B6914);
            }

            String label = Component.translatable(CATEGORIES.get(i).labelKey).getString();
            String truncated = font.plainSubstrByWidth(label, tw - 4);
            int textColor = active ? (baseColor | 0xFF000000) : hovered ? 0xFFFFF4DC : 0xFF9E8B6E;
            g.drawCenteredString(font, truncated, curX + tw / 2, barY + (barH - font.lineHeight) / 2, textColor);
            curX += tw + TAB_GAP;
        }
    }

    private void renderListCard(GuiGraphics g, Object role, int x, int y, int w, int h, float hover, boolean selected) {
        int rawColor = RoleUtils.getRoleOrModifierColor(role);
        int roleColor = rawColor | 0xFF000000;
        int outerBorder = selected ? 0xFFD4AF37 : (hover > 0.05f ? blendColors(0xFF5A4530, 0xFFC9A84C, hover) : 0xFF5A4530);
        g.fill(x, y, x + w, y + h, outerBorder);

        int bgL, bgR;
        if (selected) { bgL = 0xFF5A4520; bgR = 0xFF3A2A10; }
        else if (hover > 0.05f) { bgL = blendColors(0xFF1A1008, 0xFF5A4520, hover); bgR = blendColors(0xFF120A04, 0xFF3A2A10, hover); }
        else { bgL = 0xFF1A1008; bgR = 0xFF120A04; }
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, selected ? 0x44FFE8C0 : (hover > 0.05f ? 0x25FFFFFF : 0x10FFFFFF));

        int barW = 3;
        g.fill(x + 1, y + 1, x + 1 + barW, y + h - 1, roleColor);
        g.fillGradient(x + 1 + barW, y + 1, x + 1 + barW + 4, y + h - 1, (rawColor & 0x00FFFFFF) | 0x40000000, 0x00000000);

        int iconX = x + 1 + barW + 5, iconY = y + (h - ICON_SIZE) / 2;
        g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, blendColors(0xFF120A04, roleColor, 0.25f));
        boolean iconOk = false;
        if (role instanceof Item it) {
            iconOk = true;
            g.renderItem(it.getDefaultInstance(), iconX + 5, iconY + 5);
        } else {
            try {
                g.blit(getTypeIcon(role), iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                iconOk = true;
            } catch (Exception ignored) {}
        }
        if (!iconOk) {
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, blendColors(0xFF1A1008, roleColor, 0.55f));
            String initial = RoleUtils.getRoleOrModifierOrItemName(role).getString();
            if (!initial.isEmpty())
                g.drawCenteredString(font, Component.literal(String.valueOf(initial.charAt(0)).toUpperCase()).withStyle(ChatFormatting.BOLD),
                        iconX + ICON_SIZE / 2, iconY + (ICON_SIZE - font.lineHeight) / 2, 0xFFFFFF);
        }
        g.renderOutline(iconX, iconY, ICON_SIZE, ICON_SIZE, blendColors(roleColor, 0xFFFFFFFF, 0.3f));

        int textX = iconX + ICON_SIZE + 5, textMaxW = w - (textX - x) - 6;
        g.drawString(font, font.plainSubstrByWidth(RoleUtils.getRoleOrModifierOrItemTypeName(role).getString(), textMaxW),
                textX, y + 5, selected ? 0xFFD4AF37 : blendColors(0xFF9E8B6E, 0xFFC9A84C, hover), false);

        int nameY = y + 5 + font.lineHeight + 1;
        List<FormattedCharSequence> nameLines = font.split(RoleUtils.getRoleOrModifierOrItemNameWithColor(role), textMaxW);
        if (!nameLines.isEmpty())
            g.drawString(font, nameLines.get(0), textX, nameY, selected ? 0xFFF5E8C8 : (hover > 0.3f ? 0xFFFFF4DC : 0xFFE8D8B0), selected);

        Component subText = getCardSubText(role);
        if (subText != null) {
            int subColor = 0xFFFFFFFF;
            var tc = subText.getStyle().getColor();
            if (tc != null) subColor = new java.awt.Color(tc.getValue()).getRGB();
            g.drawString(font, font.plainSubstrByWidth(subText.getString(), textMaxW), textX, nameY + font.lineHeight + 1, subColor, false);
        }

        if (selected) {
            int indX = x + w - 4;
            g.fill(indX, y + 3, indX + 3, y + h - 3, blendColors(roleColor, 0xFFFFFFFF, 0.7f));
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, (rawColor & 0x00FFFFFF) | 0x55000000);
        }
        if (isItemDisabled(role)) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x88000000);
            g.renderOutline(x, y, w, h, 0xFFCC3333);
        }
    }

    private boolean isItemDisabled(Object role) {
        if (role instanceof SRERole r) return SREDisableManager.isRoleDisabled(r);
        if (role instanceof SREModifier m) return SREDisableManager.isModifierDisabled(m);
        return false;
    }

    private Component getCardSubText(Object role) {
        if (role instanceof SRERole r) {
            return switch (PlayerRoleWeightManager.getRoleType(r)) {
                case 0,1 -> Component.translatable("display.type.role.innocent").withStyle(ChatFormatting.GREEN);
                case 2 -> Component.translatable("display.type.role.neutral").withStyle(ChatFormatting.YELLOW);
                case 3 -> Component.translatable("display.type.role.neutral_for_killer").withStyle(ChatFormatting.LIGHT_PURPLE);
                case 4 -> Component.translatable("display.type.role.killer").withStyle(ChatFormatting.RED);
                case 5 -> Component.translatable("display.type.role.vigilante").withStyle(ChatFormatting.AQUA);
                default -> Component.literal("UNKNOWN");
            };
        }
        return Component.literal("");
    }

    // 辅助方法：获取标签栏和内容区域坐标
    private int getDetailTabBarTop() {
        return panelY + BANNER_H + PANEL_PAD;
    }
    private int getRightContentTop() {
        return getDetailTabBarTop() + DETAIL_TAB_H + 6; // 6px 上边距
    }
    private int getRightContentHeight() {
        return panelY + panelH - getRightContentTop() - PANEL_PAD;
    }

    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, rightX, panelY, rightW, panelH);

        if (selectedRole == null) {
            g.drawCenteredString(font, Component.translatable("screen.roleintroduce.select_hint").withStyle(ChatFormatting.GRAY),
                    rightX + rightW / 2, panelY + panelH / 2, 0x9E8B6E);
            return;
        }

        int rawColor = RoleUtils.getRoleOrModifierColor(selectedRole);
        g.fillGradient(rightX + 1, panelY + 1, rightX + rightW / 2, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0xCC000000, (rawColor & 0x00FFFFFF) | 0x44000000);
        g.fillGradient(rightX + rightW / 2, panelY + 1, rightX + rightW - 1, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0x44000000, 0x00000000);

        int bIconSize = BANNER_H - 6;
        int bIconX = rightX + PANEL_PAD, bIconY = panelY + 3;
        g.fill(bIconX, bIconY, bIconX + bIconSize, bIconY + bIconSize, blendColors(0xFF120A04, rawColor | 0xFF000000, 0.3f));
        if (selectedRole instanceof Item it) {
            g.renderItem(it.getDefaultInstance(), bIconX + (bIconSize - 16) / 2, bIconY + (bIconSize - 16) / 2);
        } else {
            try {
                g.blit(getTypeIcon(selectedRole), bIconX, bIconY, 0f, 0f, bIconSize, bIconSize, bIconSize, bIconSize);
            } catch (Exception ignored) {
                String s = RoleUtils.getRoleOrModifierOrItemName(selectedRole).getString();
                if (!s.isEmpty())
                    g.drawCenteredString(font, Component.literal(String.valueOf(s.charAt(0)).toUpperCase()).withStyle(ChatFormatting.BOLD),
                            bIconX + bIconSize / 2, bIconY + (bIconSize - font.lineHeight) / 2, 0xFFFFFF);
            }
        }
        g.renderOutline(bIconX, bIconY, bIconSize, bIconSize, (rawColor & 0x00FFFFFF) | 0xAA000000);

        Component nameLine = Component.translatable("gui.roleintroduce.right.warp",
                RoleUtils.getRoleOrModifierOrItemTypeName(selectedRole).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA),
                RoleUtils.getRoleOrModifierOrItemName(selectedRole));
        g.drawString(font, nameLine, bIconX + bIconSize + 5, panelY + (BANNER_H - font.lineHeight) / 2, 0xFFFFFF, true);

        if (isItemDisabled(selectedRole)) {
            int nameWidth = font.width(nameLine);
            g.drawString(font, Component.translatable("screen.roleintroduce.disabled").withStyle(ChatFormatting.RED),
                    bIconX + bIconSize + 5 + nameWidth + 5, panelY + (BANNER_H - font.lineHeight) / 2, 0xFFFFFF, true);
        }

        int tabBarH = DETAIL_TAB_H;
        int contentY = getRightContentTop();
        int contentH = getRightContentHeight();

        renderDetailTabBar(g, mouseX, mouseY, rightX + PANEL_PAD, getDetailTabBarTop(), rightW - PANEL_PAD * 2, tabBarH);

        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            DetailTab currentTab = tabs.get(activeTabIndex);
            int contentWidth = rightW - PANEL_PAD * 2 - SCROLL_W - 2;
            currentTab.render(g, rightX + PANEL_PAD, contentY, contentWidth, contentH, mouseX, mouseY, 0);
            int sbX = rightX + rightW - PANEL_PAD - SCROLL_W;
            renderVScrollbar(g, sbX, contentY, contentH, currentTab.getScrollOffset(), currentTab.getMaxScroll(),
                    currentTab.getContentHeight(), mouseX, mouseY, isDraggingDetailScroll);
        }
    }

    private void renderDetailTabBar(GuiGraphics g, int mouseX, int mouseY, int barX, int barY, int barW, int barH) {
        int visibleCount = 0;
        for (DetailTab tab : tabs) if (tab.isVisible()) visibleCount++;
        if (visibleCount == 0) return;

        int gap = DETAIL_TAB_GAP;
        int totalGap = gap * (visibleCount - 1);
        int btnW = (barW - totalGap) / visibleCount;
        int curX = barX;
        for (int i = 0; i < tabs.size(); i++) {
            DetailTab tab = tabs.get(i);
            if (!tab.isVisible()) continue;

            boolean active = (i == activeTabIndex);
            boolean hovered = !active && isInRect(mouseX, mouseY, curX, barY, btnW, barH);
            int baseColor = tab.getColor();
            if (active) {
                g.fillGradient(curX, barY, curX + btnW, barY + barH,
                        blendColors(0xFF1A1008, baseColor, 0.55f), blendColors(0xFF120A04, baseColor, 0.30f));
                g.renderOutline(curX, barY, btnW, barH, 0x558B6914);
                g.fill(curX, barY + barH - 2, curX + btnW, barY + barH, baseColor);
            } else if (hovered) {
                g.fillGradient(curX, barY, curX + btnW, barY + barH,
                        blendColors(0xFF1A1008, baseColor, 0.25f), blendColors(0xFF120A04, baseColor, 0.12f));
                g.renderOutline(curX, barY, btnW, barH, (baseColor & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, barY, curX + btnW, barY + barH, 0x551A1008);
                g.renderOutline(curX, barY, btnW, barH, 0x558B6914);
            }
            // 1px 左右边框
            g.fill(curX, barY, curX + 1, barY + barH, 0x55FFFFFF);
            g.fill(curX + btnW - 1, barY, curX + btnW, barY + barH, 0x55FFFFFF);

            String title = tab.getTitle().getString();
            String truncated = font.plainSubstrByWidth(title, btnW - 4);
            int textColor = active ? 0xFFFFF4DC : hovered ? 0xFFFFF4DC : 0xFF9E8B6E;
            g.drawCenteredString(font, truncated, curX + btnW / 2, barY + (barH - font.lineHeight) / 2, textColor);
            curX += btnW + gap;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 焦点切换
    // ══════════════════════════════════════════════════════════════════
    private void setFocusArea(FocusArea area) {
        if (currentFocusArea == area) return;
        if (currentFocusArea == FocusArea.SEARCH) searchWidget.setFocused(false);
        currentFocusArea = area;
        if (area == FocusArea.SEARCH) searchWidget.setFocused(true);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (modeBtnX != null && modeBtnW != null) {
                for (int i = 0; i < modeBtnX.length; i++) {
                    int btnX = modeBtnX[i], btnW = modeBtnW[i];
                    if (btnW > 0 && isInRect((int) mx, (int) my, btnX, modeButtonY, btnW, modeButtonH)) {
                        IntroductionGameMode clickedMode = IntroductionGameMode.values()[i];
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                        if (clickedMode == IntroductionGameMode.FILTER) openFilterScreen();
                        else if (currentMode != clickedMode) refreshFilter(clickedMode);
                        setFocusArea(FocusArea.MODE_BUTTONS);
                        return true;
                    }
                }
            }

            for (int i = 0; i < CATEGORIES.size(); i++) {
                if (tabW[i] > 0 && isInRect((int) mx, (int) my, tabX[i], topBarY, tabW[i], TOP_BAR_H)) {
                    if (selectedCategoryIndex != i) {
                        selectedCategoryIndex = i; listScrollOffset = 0;
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                        refreshFilter();
                        if (selectedRole != null && !filteredItems.contains(selectedRole)) {
                            selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                            onSelectionChanged();
                        }
                    }
                    setFocusArea(FocusArea.CATEGORY_BAR);
                    return true;
                }
            }

            if (selectedRole != null) {
                int tabBarY = getDetailTabBarTop(), tabBarH = DETAIL_TAB_H;
                int tabXStart = rightX + PANEL_PAD, tabWTotal = rightW - PANEL_PAD * 2;
                int visibleCount = (int) tabs.stream().filter(DetailTab::isVisible).count();
                if (visibleCount > 0) {
                    int gap = DETAIL_TAB_GAP, totalGap = gap * (visibleCount - 1);
                    int btnW = (tabWTotal - totalGap) / visibleCount;
                    int curX = tabXStart;
                    for (int i = 0; i < tabs.size(); i++) {
                        DetailTab tab = tabs.get(i);
                        if (!tab.isVisible()) continue;
                        if (isInRect((int) mx, (int) my, curX, tabBarY, btnW, tabBarH)) {
                            if (activeTabIndex != i) {
                                tabs.get(activeTabIndex).onSwitchAway();
                                activeTabIndex = i;
                                tabs.get(activeTabIndex).onSwitchTo();
                                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                            }
                            setFocusArea(FocusArea.RIGHT_TABS);
                            return true;
                        }
                        curX += btnW + gap;
                    }
                }
            }

            int areaX = leftX + PANEL_PAD, areaY = panelY + PANEL_PAD;
            int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2, areaH = listAreaH();
            if (isInRect((int) mx, (int) my, areaX, areaY, areaW, areaH)) {
                for (int i = 0; i < filteredItems.size(); i++) {
                    int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
                    if (isInRect((int) mx, (int) my, areaX, cardY, areaW, CARD_H)) {
                        selectedRole = filteredItems.get(i);
                        onSelectionChanged();
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                        setFocusArea(FocusArea.LEFT_LIST);
                        return true;
                    }
                }
                setFocusArea(FocusArea.LEFT_LIST);
                return true;
            }

            int lsbX = leftX + leftW - PANEL_PAD - SCROLL_W;
            if (isInRect((int) mx, (int) my, lsbX, areaY, SCROLL_W, areaH) && maxListScroll > 0) {
                isDraggingListScroll = true; dragListStartY = my; dragListStartOffset = listScrollOffset;
                setFocusArea(FocusArea.LEFT_LIST);
                return true;
            }

            if (selectedRole != null && activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
                int contentY = getRightContentTop();
                int contentH = getRightContentHeight();
                int rsbX = rightX + rightW - PANEL_PAD - SCROLL_W;
                if (isInRect((int) mx, (int) my, rsbX, contentY, SCROLL_W, contentH)
                        && tabs.get(activeTabIndex).getMaxScroll() > 0) {
                    isDraggingDetailScroll = true; dragDetailStartY = my;
                    dragDetailStartOffset = tabs.get(activeTabIndex).getScrollOffset();
                    setFocusArea(FocusArea.RIGHT_TABS);
                    return true;
                }
            }

            if (isInRect((int) mx, (int) my, leftX, topBarY, leftW - 2, TOP_BAR_H)) setFocusArea(FocusArea.SEARCH);
        }
        return super.mouseClicked(mx, my, button);
    }

    private void openFilterScreen() {
        LinkedHashMap<String, Component> optionMap = new LinkedHashMap<>();
        optionMap.put("inner.enable", Component.translatable("screen.roleintroduce.flag.inner.enable"));
        optionMap.put("inner.disable", Component.translatable("screen.roleintroduce.flag.inner.disable"));
        for (var it : TMMRoles.getAllFlags()) optionMap.put(it, Component.translatableWithFallback("screen.roleintroduce.flag." + it, it.toUpperCase().replaceAll("_", " ")));
        for (var it : HMLModifiers.getAllFlags()) optionMap.put(it, Component.translatableWithFallback("screen.roleintroduce.flag." + it, it.toUpperCase().replaceAll("_", " ")));
        FilterSelectionScreen screen = FilterSelectionScreen.builder(this)
                .title(Component.translatable("screen.filter_selection.title"))
                .subtitle(Component.translatable("screen.filter_selection.tip"))
                .options(optionMap).multiSelect(true).defaultSelections(filterFlags)
                .callback(selected -> { filterFlags.clear(); filterFlags.addAll(selected); refreshFilter(IntroductionGameMode.FILTER); })
                .build();
        screen.show(this.minecraft);
    }

    public void refreshFilter(IntroductionGameMode clickedMode) {
        currentMode = clickedMode; listScrollOffset = 0; refreshFilter();
        if (selectedRole != null && !filteredItems.contains(selectedRole)) {
            selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
            onSelectionChanged();
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingListScroll && maxListScroll > 0) {
            int areaH = listAreaH();
            int totalH = Math.max(1, filteredItems.size() * (CARD_H + CARD_SPACING));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (areaH * Math.min(1f, (float) areaH / totalH)));
            double trackH = areaH - thumbH;
            if (trackH > 0) listScrollOffset = Mth.clamp((int) (dragListStartOffset + (my - dragListStartY) / trackH * maxListScroll), 0, maxListScroll);
            return true;
        }
        if (isDraggingDetailScroll && !tabs.isEmpty() && activeTabIndex < tabs.size()) {
            DetailTab tab = tabs.get(activeTabIndex);
            if (tab.getMaxScroll() > 0) {
                int contentY = getRightContentTop();
                int contentH = getRightContentHeight();
                int totalH = tab.getContentHeight();
                int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (contentH * Math.min(1f, (float) contentH / totalH)));
                double trackH = contentH - thumbH;
                if (trackH > 0) tab.setScrollOffset((int) (dragDetailStartOffset + (my - dragDetailStartY) / trackH * tab.getMaxScroll()));
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingListScroll = isDraggingDetailScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= leftX && mx < leftX + leftW && my >= panelY && my < panelY + panelH) {
            listScrollOffset = Mth.clamp((int) (listScrollOffset - scrollY * (CARD_H + CARD_SPACING)), 0, maxListScroll);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= panelY && my < panelY + panelH) {
            if (!tabs.isEmpty() && activeTabIndex < tabs.size()) {
                DetailTab tab = tabs.get(activeTabIndex);
                int contentY = getRightContentTop();
                int contentH = getRightContentHeight();
                int areaX = rightX + PANEL_PAD;
                int areaW = rightW - PANEL_PAD * 2 - SCROLL_W - 2;
                if (mx >= areaX && mx < areaX + areaW && my >= contentY && my < contentY + contentH) {
                    tab.setScrollOffset(tab.getScrollOffset() - (int) (scrollY * (font.lineHeight + 2) * 3));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ══════════════════════════════════════════════════════════════════
    // 键盘事件
    // ══════════════════════════════════════════════════════════════════
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            boolean forward = !hasShiftDown();
            FocusArea[] vals = FocusArea.values();
            int ord = currentFocusArea.ordinal();
            int nextOrd = forward ? (ord + 1) % vals.length : (ord - 1 + vals.length) % vals.length;
            setFocusArea(vals[nextOrd]);
            return true;
        }

        if (keyCode == 266 || keyCode == 267) {
            List<Integer> visibleIndices = new ArrayList<>();
            for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).isVisible()) visibleIndices.add(i);
            if (!visibleIndices.isEmpty()) {
                int currentIdx = visibleIndices.indexOf(activeTabIndex);
                if (currentIdx == -1) currentIdx = 0;
                int delta = keyCode == 266 ? -1 : 1;
                int newPos = Math.floorMod(currentIdx + delta, visibleIndices.size());
                int newActiveTabIndex = visibleIndices.get(newPos);
                if (newActiveTabIndex != activeTabIndex) {
                    tabs.get(activeTabIndex).onSwitchAway();
                    activeTabIndex = newActiveTabIndex;
                    tabs.get(activeTabIndex).onSwitchTo();
                    setFocusArea(FocusArea.RIGHT_TABS);
                }
            }
            return true;
        }

        if (keyCode == 265 || keyCode == 264) {
            int idx = filteredItems.indexOf(selectedRole) + (keyCode == 265 ? -1 : 1);
            idx = Mth.clamp(idx, 0, filteredItems.size() - 1);
            if (idx >= 0 && idx < filteredItems.size()) {
                selectedRole = filteredItems.get(idx);
                onSelectionChanged();
                int cardY = idx * (CARD_H + CARD_SPACING);
                if (cardY < listScrollOffset) listScrollOffset = cardY;
                else if (cardY + CARD_H > listScrollOffset + listAreaH()) listScrollOffset = cardY + CARD_H - listAreaH();
                listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
            }
            return true;
        }

        if (keyCode == 263 || keyCode == 262) {
            if (currentFocusArea == FocusArea.SEARCH) {
                // let EditBox handle
            } else if (currentFocusArea == FocusArea.MODE_BUTTONS) {
                int currentModeIndex = currentMode.ordinal();
                int delta = keyCode == 263 ? -1 : 1;
                int newIndex = Mth.clamp(currentModeIndex + delta, 0, IntroductionGameMode.values().length - 1);
                IntroductionGameMode newMode = IntroductionGameMode.values()[newIndex];
                if (newMode == IntroductionGameMode.FILTER) openFilterScreen();
                else if (newMode != currentMode) refreshFilter(newMode);
                return true;
            } else {
                int newIdx = Mth.clamp(selectedCategoryIndex + (keyCode == 263 ? -1 : 1), 0, CATEGORIES.size() - 1);
                if (newIdx != selectedCategoryIndex) {
                    selectedCategoryIndex = newIdx; listScrollOffset = 0; refreshFilter();
                    if (selectedRole != null && !filteredItems.contains(selectedRole)) {
                        selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                        onSelectionChanged();
                    }
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFE8C0);
    }

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h,
            int scrollOffset, int maxScroll, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF1A1008);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x558B6914);
        if (maxScroll <= 0) return;

        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);

        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH, hl ? 0xFFC9A84C : 0xFF8B6914);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1, hl ? 0xFFD4AF37 : 0xFFB8960C);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }
}
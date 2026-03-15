package net.exmo.sre.loading;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
public class StarRailExpressTitleScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("narrator.screen.title");
    private static final String QQ_GROUP_URL = "https://qm.qq.com/q/8XXqKRjT7q";
    private static final String DISCORD_URL = "https://discord.gg/T7R5NkMHt3";
    private static final String WIKI_URL = "https://docs.qq.com/smartsheet/DUUNVaFJTdmFTQ0Ry?tab=sc_bw1NDg";

    // 基础尺寸常量（用于缩放）
    private static final int BASE_MENU_START_X = 70;
    private static final int BASE_MENU_START_Y_OFFSET = 110;
    private static final int BASE_MENU_SPACING = 26;
    private static final int BASE_LEFT_PANEL_X = 38;
    private static final int BASE_LEFT_PANEL_Y = 78;
    private static final int BASE_LEFT_PANEL_W = 230;
    private static final int BASE_LEFT_PANEL_H = 270; // 固定部分高度
    private static final int BASE_RIGHT_PANEL_W = 450;
    private static final int BASE_RIGHT_PANEL_HEADER_H = 24;
    private static final int BASE_RIGHT_PANEL_BODY_H = 270;
    private static final int BASE_RIGHT_PANEL_X_OFFSET = 36; // 右侧面板距右边距离
    private static final int BASE_RIGHT_PANEL_Y = 86;
    private static final int BASE_SCROLLBAR_WIDTH = 6;
    private static final int BASE_ANIM_OFFSET = 22; // 菜单动画偏移

    @Nullable
    private SplashRenderer splash;
    @Nullable
    private RealmsNotificationsScreen realmsNotificationsScreen;

    private final LogoRenderer logoRenderer;

    private long screenOpenTime;
    private float panoramaFade = 1.0F;

    // 首次进入提示状态
    private boolean waitingForContinue = true;
    private float continueAlpha = 0.0F;

    // 主菜单动画
    private float menuAnimProgress = 0.0F;

    // 更新日志面板
    private boolean changelogExpanded = true;
    private float changelogExpandAnim = 1.0F;
    private float changelogScrollOffset = 0.0F;
    private float changelogMaxScroll = 0.0F;
    private boolean changelogDragging = false;
    private double lastMouseY = 0.0;
    private List<ChangelogEntry> parsedChangelogLines;

    private final List<MenuEntry> menuEntries = new ArrayList<>();
    private final List<String> changelogRawLines = List.of(
            "#3.3 补丁更新日志",
            "注：此版本更新之后会在较长的一段时间之内不再更新任何新职业，转而完善现在已有的职业，此后模组将会进行一些底层的改动。",
            "",
            "##新职业：",
            "###锁匠（平民阵营）(未定稿，其它功能会在之后补全，有改进建议可以提出):与工程师 (平民) 绑定生成。可购买螺丝刀、乘务员钥匙。螺丝刀：可以修复被撬棍破坏的门。手持时不可见。使用螺丝刀蹲下右键门可以将门重置（可以在门被撬锁器锁上的时候使用）（并掉落加固在门上的道具）",
            "###电报员 (平民阵营，彩蛋职业)：老资历应该都玩过，只有 2% 的概率刷新。你可以按下技能键打开电报界面，向所有玩家发送匿名消息。每局游戏最多可以发送 6 次电报。电报消息会以标题形式显示在所有玩家的屏幕上。",
            "###帕秋莉（平民阵营，彩蛋职业）：你可以购买职业书 Pachuri Knowledge Book。花费 200 金币获取场上随机一名玩家 (包括已经阵亡的玩家) 的职业并记录在职业书中。",
            "###红美玲（警长阵营，彩蛋职业）:只在可跳跃地图中生成。按下技能键获得最多 10s 漂浮 II 效果。冷却 60s。期间可再次按下技能键关闭技能。可以用拳头揍人（左键）。揍同一人 5 次会使其死亡。有小脑惩罚。初始拥有一层气盾，气盾被打破后会击退周围人。被动 - 惧怕孤独：如果你周围 5 格内没人，你将在 60s 后死去。",
            "",
            "##职业/修饰符改动：",
            "1、作家：现在玩家可以通过阅读作家写的成书来完成读书任务 2、船长：现在船长可以在商店中购买万能钥匙和螺丝刀。船长的螺丝刀只能用来取下加固在门上的道具（拿着螺丝刀蹲下右键门取下道具）3、大嗓门：现在触发大嗓门技能时，你周围 5 格范围内的人（不包括你）会发光。4、工程师：现在工程师无法将玻璃门上锁，但是工程师现在可以给铁门上锁 5、巡警：现在巡警手枪的 CD 为普通左轮的 1/5。6、武术教官：双节棍的距离由 4 格改为 5 格 7、强盗：现在强盗的手枪有 90% 的概率在命中后掉落 8、老人：老人的轮椅耐久改为 90s",
            "",
            "#3.3 更新日志",
            "##新职业：",
            "###会计（平民）：你每 60s 会获得 25 金币。蹲下按技能键可以切换模式：收入与支出。直接按技能键花费 175 金币发动技能。收入：对玩家按下技能键可以查看一个人存储的金币量是否超过 300 金币。如果是的话会给予会计进行消息提示。支出：可以标记一名玩家，你会在 20s 后得知该玩家的金币数的变化方向（支出/收入）。会计商店可花费 100 金币购买存折 (仅能购买一次)。右键存折会消耗身上的所有金币，并将金币保存在存折中。再次右键存折会获得存折中的金币并消耗存折。存折会在会计死亡后掉落。",
            "###消防员 (平民)：可以购买消防斧和灭火器。消防斧和灭火器全局仅能购买一次。消防斧：拥有 3 点耐久，蹲下右键门可以将门撬开，并消耗 1 点耐久。长按右键可以将消防斧举起来刀人 (只有满耐久的时候才能刀人，刀人之后直接消耗 3 点耐久) 。灭火器：右键灭火器可对人喷射（每使用一次掉 1 点耐久，也能长按右键持续喷射，最多持续 5s）被喷射的人会获得缓慢和失明效果（1.5s）。灭火器可以清除被喷射者身上的纵火犯汽油。",
            "###药剂师（平民）：持续蹲下每 20 秒获取一次药剂素材。蹲下按技能键可以切换调制的药剂。按下技能键消耗 1 个素材 + 相应的金币可调取一份对应的药剂。每种药剂只能调两次，游戏结束时重置次数。药剂 (均为一次性用品)：1、肾上腺素（100 金币）：对目标使用后增加其体力上限 2、抗生素（100 金币）：对目标使用后解除中毒 3、鹤顶红（200 金币）：对目标使用后使目标中毒 4、狗皮膏药（150 金币）：对目标使用后使目标 30 秒内 san 值不会下降",
            "###搜救员（平民）：可以购买绳索和裹尸袋。绳索：可以将直线距离 12 格内离你最近的玩家拉到你的面前，拥有 2 点耐久，使用后进入 5s 冷却。裹尸袋：可以清除尸体。",
            "###琪露诺（平民）：与小镇做题家绑定生成，你免疫小镇做题家的强制考试。可以在商店花费 100 金币购买习题集。右键习题集进入做题界面，在规定时间内完成所有题目。你成功完成可获得 200 金币，否则 san 值降低 30%。你也可以将习题集丢给其他玩家。其他玩家成功完成可获得 100 金币，失败则立即死亡！",
            "###作家（平民）：彩蛋职业，只有 2% 的概率刷新。你拥有专属商店，可以购买书与笔。署名后的成书可以丢给别人阅读。作家死后掉落身上的书与笔和成书。",
            "###女仆咲夜（平民）：彩蛋职业，你可以购买飞刀。飞刀按 Q 键射出。可以花高价购买十四夜，饮用后获得速度 10，持续 20s。符卡：幻世「The World」按下技能键触发，使用后时停 5s。",
            "###特警（警长）：开局自带狙击枪和一颗马格南子弹，无法捡起左轮手枪。仅会在特定地图有概率生成。商店可购买马格南子弹 (150 金币)、瞄准镜 (100 金币)、铁门钥匙 (75 金币)。狙击枪需要使用左键装弹，装弹时间 6s，蹲下左键安装瞄准镜，安装时间 2s。当狙击枪不安装瞄准镜时，右键可直接发射子弹，射出后需等待 4s 才能再次射击。当狙击枪安装了瞄准镜时，右键打开瞄准镜，再次右键发射子弹。狙击枪仅可穿透屏障和屏障镶板。当狙击枪命中 50 格以外的玩家时，会无视护盾直接将其击毙。特警死后掉落左轮手枪。",
            "###武术教官（警长）：开局自带双节棍，无法捡起左轮手枪。左键将 4 格内离你最近的玩家向左击退，右键将 4 格内离你最近的玩家向右击退。蹲下左键将玩家拉到你面前，蹲下右键将玩家推开。同一个招式在 7s 内无法再次使用。如果此次攻击使受击者被击退到方块上，再次攻击则会使目标直接死亡。如果在 7s 内，同个目标连续受到 3 次棍击，则目标直接死亡。每次使用双节棍后会进入 1s 冷却，每次击杀玩家之后，双节棍会进入 7s 冷却。连续使用 3 次双节棍即使不造成击杀也会使双节棍进入 5s 冷却。武术教官死后掉落左轮手枪。",
            "###承太郎（警长）：与迪奥绑定生成。特殊技能 白金之星 世界：时停 3s，冷却 240s 被动：枪的冷却增加一倍 白金之星使你能打开所有的门 攻击方式：初始获得波纹勋章，蓄力 3s 挥出一拳，击飞旁边的人，目标死亡，向前冲刺，有小脑惩罚，冷却 60s。初始不自带枪，时停不可覆盖其他时停。",
            "###小镇做题家（杀手）：与琪露诺绑定生成。主动技能 - 强制考试：花费 50 金币，在物品栏选择一名玩家，强制其进入做题界面。目标需要在规定时间内完成数学题，连续答错 3 次则死亡。每一个因强制考试死亡的玩家会给予你 1 能量。累计获得 3 能量后，你将会获得狂暴体验券，能量将清空。狂暴体验券可以在商店中兑换激活狂暴模式。",
            "###迪奥（杀手）：占用 2 个杀手位，与承太郎绑定生成，会在白天自燃。商店可购买飞刀、停电、撬锁器。飞刀：Q 键或右键蓄力投掷飞刀，远程武器。主动技能 - 时间停止：持续时间 10 秒，冷却 15 秒，吸食尸体解锁。在时间停止期间，除了你以外的所有玩家无法移动、无法使用物品。钟表匠在时间停止期间可以移动。特殊被动 最后的狂欢 需要吸食 8 个尸体后解锁 被击中后，免疫死亡，获得 30s 的临时生命 期间每被击中一次临时生命减少 25 秒 值至吸食下一次尸体增加 30s 临时生命 期间有 50% 概率免疫伤害，速度 +30%。",
            "",
            "##新地图：",
            "###泊黎别墅：处在雪原之中的小别墅",
            "###双层列车：史上最牛逼列车图没有之一",
            "",
            "##职业/修饰符改动：",
            "1、钟表匠：现在钟表匠免疫所有时间静止技能，钟和怀表的价格更贵 2、明星（重做）：使用技能可以让 30 格范围人的视野都看向 Ta，同时发亮两秒。可以看到周围有多少人看向自己。每个看到 Ta 的人会让 Ta 加 10 金币，最多 150 金币。可以在商店购买签名纸，直接右键可以签普通签名。拥有普通签名的玩家能持续看到明星的位置。拥有签名的玩家视野内有明星的时候会获得速度 I。蹲下右键可以签生死状（仅一次），拥有生死状的玩家在受到致命伤害时你可以为它抵挡一次伤害，自己受伤。3、仇杀客：仇杀客现在进入二阶段，四阶段，五阶段时会全场播放音效 4、小偷：小偷现在多了一个模式，卖物品模式，可以将偷到的东西卖出 5、傀儡师：未觉醒时不再拥有杀手透视 6、老人：老人现在不会刷新跑步任务，轮椅有耐久 7、远征队：远征队现在会分配给小透明，且只会分配给平民阵营的职业 8、双重人格：双层人格现在只会分配给平民阵营的职业 9、炸弹客：炸弹客的炸弹在除炸弹客以外的人眼中不再显示爆炸剩余时间，炸弹客的炸弹无法再用鼠标拿起 10、赌徒：赌徒可供选择的职业中添加了毒师和医生。",
            "",
            "##优化内容：",
            "1、优化了地图加载，现在地图加载会加载的更快 2、添加了职业权重系统，玩家更容易分配到不同阵营的职业 3、优化了地图加载时的光照系统 4、优化了警长职业分配，优化了中立分配 5、再次优化了任务点透视 6、优化了修饰符分配（修饰符分配只会分配 1 个）7、旁观者无法查看玩家职业的时候能够看到玩家的边框了",
            "",
            "##Bug 修复",
            "1、修复了老人轮椅 bug 2、修复了一堆双重人格和亡命徒的 bug 4、修了一堆小 bug 5、修复纵火犯在打火机进入冷却之后点火也会导致点火失败的 bug 6、修了一堆地图 bug 7、加入了 him 8、移除了 him"
    );

    // 缩放相关
    private float globalScale = 1.0F;
    private int scaledMenuStartX;
    private int scaledMenuStartYOffset;
    private int scaledMenuSpacing;
    private int scaledLeftPanelX;
    private int scaledLeftPanelY;
    private int scaledLeftPanelW;
    private int scaledLeftPanelH;
    private int scaledRightPanelW;
    private int scaledRightPanelHeaderH;
    private int scaledRightPanelBodyH;
    private int scaledRightPanelXOffset;
    private int scaledRightPanelY;
    private int scaledScrollbarWidth;
    private int scaledAnimOffset;

    public StarRailExpressTitleScreen() {
        this(false);
    }

    public StarRailExpressTitleScreen(boolean fading) {
        this(fading, null);
    }

    public StarRailExpressTitleScreen(boolean fading, @Nullable LogoRenderer logoRenderer) {
        super(TITLE);
        this.logoRenderer = (LogoRenderer) Objects.requireNonNullElseGet(logoRenderer, () -> new LogoRenderer(false));
    }

    private int scale(int value) {
        return (int) (value * globalScale);
    }

    @Override
    protected void init() {

        // 计算缩放系数（以高度720为基准，只缩小不放大）
        this.globalScale = Math.min(this.height / 720.0f, 1.0f);

        // 计算缩放后的布局参数
        this.scaledMenuStartX = scale(BASE_MENU_START_X);
        this.scaledMenuStartYOffset = scale(BASE_MENU_START_Y_OFFSET);
        // 菜单项间距：至少18px（字体高度14+最小间距4），避免文字重叠
        this.scaledMenuSpacing = Math.max(18, scale(BASE_MENU_SPACING));
        this.scaledLeftPanelX = scale(BASE_LEFT_PANEL_X);
        this.scaledLeftPanelY = scale(BASE_LEFT_PANEL_Y);
        this.scaledLeftPanelW = (BASE_LEFT_PANEL_W);
        this.scaledRightPanelW = scale(BASE_RIGHT_PANEL_W);
        this.scaledRightPanelHeaderH = scale(BASE_RIGHT_PANEL_HEADER_H);
        this.scaledRightPanelBodyH = scale(BASE_RIGHT_PANEL_BODY_H);
        this.scaledRightPanelXOffset = scale(BASE_RIGHT_PANEL_X_OFFSET);
        this.scaledRightPanelY = scale(BASE_RIGHT_PANEL_Y);
        this.scaledScrollbarWidth = scale(BASE_SCROLLBAR_WIDTH);
        this.scaledAnimOffset = scale(BASE_ANIM_OFFSET);

        this.screenOpenTime = Util.getMillis();
        this.menuEntries.clear();

        // 解析更新日志（传入缩放后的面板宽度用于文字换行）
        this.parsedChangelogLines = parseChangelogLines(this.changelogRawLines);

        if (this.splash == null) {
            this.splash = this.minecraft.getSplashManager().getSplash();
        }

        if (this.realmsNotificationsScreen == null) {
            this.realmsNotificationsScreen = new RealmsNotificationsScreen();
        }

        if (this.realmsNotificationsEnabled()) {
            this.realmsNotificationsScreen.init(this.minecraft, this.width, this.height);
        }

        // 文字菜单
        int baseY = this.height / 2 - scaledMenuStartYOffset / 2;

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.multiplayer"),
                () -> {
                    Screen screen = this.minecraft.options.skipMultiplayerWarning
                            ? new JoinMultiplayerScreen(this)
                            : new SafetyScreen(this);
                    this.minecraft.setScreen(screen);
                }
        ));

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.singleplayer"),
                () -> this.minecraft.setScreen(new SelectWorldScreen(this))
        ));

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.options"),
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))
        ));

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.join_qq"),
                () -> Util.getPlatform().openUri(QQ_GROUP_URL)
        ));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.join_discord"),
                () -> Util.getPlatform().openUri(DISCORD_URL)
        ));

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.mod_config"),
                () -> {
                    if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                        this.minecraft.setScreen(ModMenuApi.createModsScreen(this));
                    }
                }
        ));

        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.quit"),
                () -> this.minecraft.stop()
        ));

        // 设置菜单项位置（使用缩放后的坐标）
        for (int i = 0; i < this.menuEntries.size(); i++) {
            MenuEntry entry = this.menuEntries.get(i);
            entry.x = scaledMenuStartX;
            entry.y = baseY + i * scaledMenuSpacing;
            entry.index = i;
        }

        // 计算左侧面板高度（固定部分缩放 + 菜单项高度 + 底部边距）
        int scaledLeftPanelHBase = scale(BASE_LEFT_PANEL_H);
        int scaledBottomMargin = scale(12);
        // 注意：菜单项行高14不缩放，因为字体大小不变
        this.scaledLeftPanelH = scaledLeftPanelHBase + menuEntries.size() * 14 + scaledBottomMargin;
    }

    private boolean realmsNotificationsEnabled() {
        return this.realmsNotificationsScreen != null;
    }

    @Override
    public void tick() {
        if (this.realmsNotificationsEnabled()) {
            this.realmsNotificationsScreen.tick();
        }

        long elapsed = Util.getMillis() - this.screenOpenTime;
        float t = Math.min(elapsed / 800.0F, 1.0F);
        this.continueAlpha = t;

        if (!this.waitingForContinue) {
            this.menuAnimProgress = Math.min(this.menuAnimProgress + 0.06F, 1.0F);
        }

        float targetExpand = this.changelogExpanded ? 1.0F : 0.0F;
        this.changelogExpandAnim += (targetExpand - this.changelogExpandAnim) * 0.18F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    public static CompletableFuture<Void> preloadResources(TextureManager textureManager, Executor executor) {
        return CompletableFuture.allOf(textureManager.preload(LogoRenderer.MINECRAFT_LOGO, executor), textureManager.preload(LogoRenderer.MINECRAFT_EDITION, executor), textureManager.preload(PanoramaRenderer.PANORAMA_OVERLAY, executor), CUBE_MAP.preload(textureManager, executor));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        PANORAMA.render(guiGraphics, this.width, this.height, this.panoramaFade, delta);
//        this.renderPanorama(guiGraphics, delta);
//        this.renderModernOverlay(guiGraphics);

        // 版本信息（不缩放，保持固定像素位置）
        String version = "StarRailExpress " + "3.4.0.1";
        if (Minecraft.checkModStatus().shouldReportAsModified()) {
            version += I18n.get("menu.modded");
        }
        guiGraphics.drawString(this.font, version, 8, this.height - 14, 0xB8C0CC, false);

        if (this.waitingForContinue) {
            this.renderContinuePrompt(guiGraphics, mouseX, mouseY, delta);
        } else {
            this.renderMainMenu(guiGraphics, mouseX, mouseY, delta);
        }

        if (this.realmsNotificationsEnabled() && !this.waitingForContinue) {
            RenderSystem.enableDepthTest();
            this.realmsNotificationsScreen.render(guiGraphics, mouseX, mouseY, delta);
        }
    }

    private void renderModernOverlay(GuiGraphics guiGraphics) {
        // 整体压暗 + 渐变
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x66101014, 0xAA06070A);
        guiGraphics.fillGradient(0, 0, this.width, this.height / 3, 0x2200C2FF, 0x00000000);
    }

    private void renderContinuePrompt(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        String title = I18n.get("changelog.continue.title");
        String sub = I18n.get("changelog.continue.subtitle");

        int titleWidth = this.font.width(title);
        int subWidth = this.font.width(sub);

        float pulse = 0.65F + 0.35F * (float) Math.sin((Util.getMillis() - this.screenOpenTime) / 180.0D);
        int alpha = (int) (this.continueAlpha * pulse * 255.0F);
        int color = (alpha << 24) | 0xF3F6FB;
        int subColor = ((int) (alpha * 0.6F) << 24) | 0xAAB3C2;

        int cx = this.width / 2;
        int cy = this.height / 2 + 40;

        guiGraphics.drawString(this.font, title, cx - titleWidth / 2, cy, color, false);
        guiGraphics.drawString(this.font, sub, cx - subWidth / 2, cy + 16, subColor, false);
    }

    private void renderMainMenu(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 左侧面板
        drawPanel(guiGraphics, scaledLeftPanelX, scaledLeftPanelY, scaledLeftPanelW, scaledLeftPanelH, 0x5A0E1117, 0xAA1A1F2A);

        // 标题（内部偏移不缩放，保持相对面板位置）
        guiGraphics.drawString(this.font, I18n.get("changelog.main_menu.title"), scaledLeftPanelX + 18, scaledLeftPanelY + 14, 0xDDE6F5, false);
        guiGraphics.drawString(this.font, I18n.get("changelog.main_menu.subtitle"), scaledLeftPanelX + 18, scaledLeftPanelY + 28, 0x7F8A9E, false);

        // 左侧菜单
        for (MenuEntry entry : this.menuEntries) {
            this.renderMenuEntry(guiGraphics, entry, mouseX, mouseY);
        }

        // 右侧更新日志
        this.renderChangelog(guiGraphics, mouseX, mouseY);

        // 底部版权（不缩放）
        String copyright = I18n.get("changelog.copyright");
        guiGraphics.drawString(this.font, copyright, 8, this.height - 28, 0x7F8A9E, false);
    }

    private void renderMenuEntry(GuiGraphics guiGraphics, MenuEntry entry, int mouseX, int mouseY) {
        float appearDelay = entry.index * 0.08F;
        float localProgress = Mth.clamp((this.menuAnimProgress - appearDelay) / 0.35F, 0.0F, 1.0F);
        float eased = easeOutCubic(localProgress);

        int finalX = entry.x;
        int drawX = (int) (finalX - (1.0F - eased) * scaledAnimOffset);
        int drawY = entry.y;
        int textWidth = this.font.width(entry.text);

        boolean hovered = mouseX >= drawX && mouseX <= drawX + textWidth + 12
                && mouseY >= drawY - 2 && mouseY <= drawY + 11;

        float hoverTarget = hovered ? 1.0F : 0.0F;
        entry.hoverAnim += (hoverTarget - entry.hoverAnim) * 0.22F;

        int baseColor = lerpColor(entry.hoverAnim, 0xC6CFDB, 0xFFFFFF);
        int accentColor = lerpColor(entry.hoverAnim, 0x3AA6FF, 0x7FDBFF);

        // 左侧装饰线（线宽1px不缩放）
        int lineHeight = 10;
        int lineAlpha = (int) (120 + entry.hoverAnim * 100);
        guiGraphics.fill(drawX - 14, drawY + 2, drawX - 12, drawY + 2 + lineHeight,
                (lineAlpha << 24) | (accentColor & 0xFFFFFF));

        // 文字位移
        int textOffset = (int) (entry.hoverAnim * 6.0F);
        guiGraphics.drawString(this.font, entry.text, drawX + textOffset, drawY, baseColor, false);

        // Hover 下划线
        int underlineWidth = (int) ((textWidth + 4) * entry.hoverAnim);
        if (underlineWidth > 0) {
            guiGraphics.fill(drawX + textOffset, drawY + 12, drawX + textOffset + underlineWidth, drawY + 13,
                    0xCC7FDBFF);
        }

        entry.renderX = drawX;
        entry.renderY = drawY;
        entry.renderWidth = textWidth + 16;
        entry.renderHeight = 14;
    }

    private void renderChangelog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
        int y = scaledRightPanelY;
        int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);
        int textMaxWidth = scaledRightPanelW - scale(30); // 文字区域最大宽度

        // 标题栏
        drawPanel(guiGraphics, x, y, scaledRightPanelW, scaledRightPanelHeaderH, 0x7A10141B, 0xCC1B2230);

        String foldText = this.changelogExpanded ? I18n.get("changelog.title") + "  [-]" : I18n.get("changelog.title") + "  [+]";
        guiGraphics.drawString(this.font, foldText, x + 12, y + 8, 0xE8EEF8, false);

        // 可折叠内容
        if (currentBodyH > 4) {
            drawPanel(guiGraphics, x, y + scaledRightPanelHeaderH + 2, scaledRightPanelW, currentBodyH, 0x520C1016, 0xA0181E28);

            // 计算最大滚动距离
            int totalContentHeight = this.parsedChangelogLines.stream()
                    .mapToInt(entry -> entry.totalHeight)
                    .sum();
            int visibleHeight = currentBodyH - 24;
            this.changelogMaxScroll = Math.max(0, totalContentHeight - visibleHeight);
            this.changelogScrollOffset = Mth.clamp(this.changelogScrollOffset, 0.0F, this.changelogMaxScroll);

            // 启用剔除区域
            enableScissor(x, y + scaledRightPanelHeaderH + 2, x + scaledRightPanelW, y + scaledRightPanelHeaderH + 2 + currentBodyH);

            try {
                // 渲染滚动内容
                int textY = y + scaledRightPanelHeaderH + 12;
                int currentY = textY - (int) this.changelogScrollOffset;
                for (ChangelogEntry entry : this.parsedChangelogLines) {
                    if (currentY >= textY - 80 && currentY <= y + (scaledRightPanelHeaderH + currentBodyH) * 3) {
                        var wrappedLines = this.font.split(entry.text, textMaxWidth - entry.x);
                        int lineOffset = 0;
                        for (var line : wrappedLines) {
                            guiGraphics.drawString(this.font, line, x + entry.x, currentY + lineOffset, entry.color, entry.shadow);
                            lineOffset += entry.lineHeight;
                        }
                    }
                    currentY += entry.totalHeight;
                }
            } finally {
                RenderSystem.disableScissor();
            }

            // 渲染滚动条
            if (this.changelogMaxScroll > 0) {
                int scrollbarX = x + scaledRightPanelW - scaledScrollbarWidth - 4; // 4px右边距不缩放
                int scrollbarTop = y + scaledRightPanelHeaderH + 6;
                int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;
                int scrollbarTrackHeight = scrollbarBottom - scrollbarTop;

                float scrollProgress = this.changelogScrollOffset / this.changelogMaxScroll;
                int thumbHeight = Math.max(20, (int) (scrollbarTrackHeight * (visibleHeight / (float) totalContentHeight)));
                int thumbY = scrollbarTop + (int) (scrollProgress * (scrollbarTrackHeight - thumbHeight));

                // 滚动条轨道
                guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + scaledScrollbarWidth, scrollbarBottom, 0x40FFFFFF);
                // 滚动条滑块
                int thumbAlpha = this.changelogDragging ? 0xAA : 0x66;
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scaledScrollbarWidth, thumbY + thumbHeight,
                        (thumbAlpha << 24) | 0x88AACC);
            }
        }
    }

    private List<ChangelogEntry> parseChangelogLines(List<String> rawLines) {
        List<ChangelogEntry> entries = new ArrayList<>();
        int baseX = 12; // 不缩放，因为相对于面板左边距
        int textMaxWidth = scaledRightPanelW - scale(30); // 使用缩放后的面板宽度

        for (String line : rawLines) {
            if (line.isEmpty()) {
                entries.add(new ChangelogEntry(Component.literal(""), baseX, 12, 12, 0xB8C0CC, false));
                continue;
            }

            if (line.startsWith("###")) {
                String text = line.substring(3).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        14,
                        wrapped.size() * 14,
                        0xDDE6F5,
                        false
                ));
            } else if (line.startsWith("##")) {
                String text = line.substring(2).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        13,
                        wrapped.size() * 13,
                        0xF0F4FF,
                        false
                ));
            } else if (line.startsWith("#")) {
                String text = line.substring(1).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        16,
                        wrapped.size() * 16,
                        0xFFFFFF,
                        false
                ));
            } else {
                Component normalText = Component.literal(line);
                var wrapped = this.font.split(normalText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        normalText,
                        baseX,
                        12,
                        wrapped.size() * 12,
                        0xB8C0CC,
                        false
                ));
            }
        }

        return entries;
    }

    private static class ChangelogEntry {
        final Component text;
        final int x;
        final int lineHeight;
        final int totalHeight;
        final int color;
        final boolean shadow;

        ChangelogEntry(Component text, int x, int lineHeight, int totalHeight, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.lineHeight = lineHeight;
            this.totalHeight = totalHeight;
            this.color = color;
            this.shadow = shadow;
        }
    }

    private void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = this.minecraft.getWindow();
        int scale = (int) w.getGuiScale();
        int sy0 = (int) (w.getScreenHeight() - y1 * scale);
        RenderSystem.enableScissor(x0 * scale, sy0, (x1 - x0) * scale, (y1 - y0) * scale);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h, int bg, int border) {
        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);
    }

//    protected void renderPanorama(GuiGraphics guiGraphics, float delta) {
//        // 假设 PANORAMA 是某个静态全景图渲染器
//        // PANORAMA.render(guiGraphics, this.width, this.height, this.panoramaFade, delta);
//    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.waitingForContinue) {
            // 播放点击音效
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress = 0.0F;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.waitingForContinue) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress = 0.0F;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.waitingForContinue && this.changelogExpanded) {
            int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
            int y = scaledRightPanelY;
            int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);

            // 检查鼠标是否在更新日志区域内
            if (mouseX >= x && mouseX <= x + scaledRightPanelW
                    && mouseY >= y + scaledRightPanelHeaderH + 2 && mouseY <= y + scaledRightPanelHeaderH + currentBodyH) {
                if (this.changelogMaxScroll > 0) {
                    this.changelogScrollOffset = Mth.clamp(
                            (float) (this.changelogScrollOffset - verticalAmount * 8.0),
                            0.0F,
                            this.changelogMaxScroll
                    );
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.changelogDragging && button == 0) {
            if (this.changelogMaxScroll > 0) {
                int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
                int y = scaledRightPanelY;
                int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);

                int scrollbarTop = y + scaledRightPanelHeaderH + 6;
                int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;
                int scrollbarTrackHeight = scrollbarBottom - scrollbarTop;
                int visibleHeight = currentBodyH - 24;
                int totalContentHeight = this.parsedChangelogLines.stream()
                        .mapToInt(entry -> entry.totalHeight)
                        .sum();
                int thumbHeight = Math.max(20, (int) (scrollbarTrackHeight * (visibleHeight / (float) totalContentHeight)));

                float deltaScroll = (float) (dragY * (this.changelogMaxScroll / (scrollbarTrackHeight - thumbHeight)));
                this.changelogScrollOffset = Mth.clamp(
                        this.changelogScrollOffset + deltaScroll,
                        0.0F,
                        this.changelogMaxScroll
                );
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.waitingForContinue) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress = 0.0F;
            return true;
        }

        // 点击更新日志标题折叠
        int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
        int y = scaledRightPanelY;

        if (mouseX >= x && mouseX <= x + scaledRightPanelW && mouseY >= y && mouseY <= y + scaledRightPanelHeaderH) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.changelogExpanded = !this.changelogExpanded;
            return true;
        }

        // 检查是否点击了滚动条
        if (!this.waitingForContinue && this.changelogExpanded && button == 0) {
            int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);
            int scrollbarX = x + scaledRightPanelW - scaledScrollbarWidth - 4;
            int scrollbarTop = y + scaledRightPanelHeaderH + 6;
            int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;

            if (mouseX >= scrollbarX && mouseX <= scrollbarX + scaledScrollbarWidth
                    && mouseY >= scrollbarTop && mouseY <= scrollbarBottom) {
                this.changelogDragging = true;
                this.lastMouseY = mouseY;
                return true;
            }
        }

        // 点击左侧菜单
        for (MenuEntry entry : this.menuEntries) {
            if (mouseX >= entry.renderX && mouseX <= entry.renderX + entry.renderWidth
                    && mouseY >= entry.renderY - 2 && mouseY <= entry.renderY + entry.renderHeight) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                entry.onPress.run();
                return true;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return this.realmsNotificationsEnabled() && this.realmsNotificationsScreen.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.changelogDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        if (this.realmsNotificationsScreen != null) {
            this.realmsNotificationsScreen.removed();
        }
    }

    @Override
    public void added() {
        super.added();
        if (this.realmsNotificationsScreen != null) {
            this.realmsNotificationsScreen.added();
        }
    }

    private static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    private static int lerpColor(float t, int a, int b) {
        t = Mth.clamp(t, 0.0F, 1.0F);

        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;

        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;

        int rr = (int) Mth.lerp(t, ar, br);
        int rg = (int) Mth.lerp(t, ag, bg);
        int rb = (int) Mth.lerp(t, ab, bb);

        return (rr << 16) | (rg << 8) | rb;
    }

    private static class MenuEntry {
        final Component text;
        final Runnable onPress;

        int x;
        int y;
        int index;

        float hoverAnim = 0.0F;

        int renderX;
        int renderY;
        int renderWidth;
        int renderHeight;

        MenuEntry(Component text, Runnable onPress) {
            this.text = text;
            this.onPress = onPress;
        }
    }
}
package io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Objects;

import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import net.exmo.sre.loading.StarRailExpressTitleScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerLinksScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.CommonLinks;

public class WithParentScreenPauseScreen extends PauseScreen {
    public Screen parent;
    public static final Component ROLE_INTRODUCTION = Component.translatable("menu.sre.role_introduction");
    public static final Component FEEDBACK_TRAIN = Component.translatable("menu.sre.feedback_train_bug");
    public static final Component SETTINGS = Component.translatable("menu.sre.train_options");
    public static final Component JOIN_QQ = Component.translatable("menu.sre.join_qq");
    public static final Component JOIN_DISCORD = Component.translatable("menu.sre.join_discord");
    public static final Component JOIN_FEEDBACK = Component.translatable("menu.sre.feedback");

    public WithParentScreenPauseScreen(boolean bl) {
        super(bl);
        this.parent = null;
    }

    public WithParentScreenPauseScreen(Screen screen) {
        this(true);
        this.parent = screen;
    }

    protected void init() {
        if (this.showPauseMenu) {
            this.createPauseMenu_sre();
        }

        int var10004 = this.showPauseMenu ? 40 : 10;
        int var10005 = this.width;
        Objects.requireNonNull(this.font);
        this.addRenderableWidget(new StringWidget(0, var10004, var10005, 9, this.title, this.font));
    }

    public void createPauseMenu_sre() {
        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);
        rowHelper.addChild(Button.builder(RETURN_TO_GAME, (button) -> {
            this.minecraft.setScreen((Screen) parent);
        }).width(204).build(), 2, gridLayout.newCellSettings().paddingTop(50));
        rowHelper.addChild(this.openScreenButton(ROLE_INTRODUCTION,
                () -> new RoleIntroduceScreen(this)));
        rowHelper.addChild(this.openScreenButton(SETTINGS, () -> new SettingMenuScreen(this)));
        ServerLinks serverLink = this.minecraft.player.connection.serverLinks();
        var arr = new ArrayList<>(serverLink.entries());
        try {
            arr.add(ServerLinks.Entry.custom(JOIN_QQ,
                    new URI(StarRailExpressTitleScreen.QQ_GROUP_URL)));
            arr.add(ServerLinks.Entry.custom(JOIN_DISCORD,
                    new URI(StarRailExpressTitleScreen.DISCORD_URL)));
            arr.add(ServerLinks.Entry.custom(FEEDBACK_TRAIN,
                    new URI(StarRailExpressTitleScreen.FEEDBACK_URL)));
        } catch (URISyntaxException e) {
        }
        ServerLinks serverLinks = new ServerLinks(arr);
        if (serverLinks.isEmpty()) {
            addOriginalFeedbackButtons(this, rowHelper);
        } else {
            rowHelper.addChild(this.openScreenButton(FEEDBACK_SUBSCREEN, () -> new FeedbackSubScreen(this)));
            rowHelper.addChild(this.openScreenButton(SERVER_LINKS, () -> new ServerLinksScreen(this, serverLinks)));
        }

        rowHelper.addChild(this.openScreenButton(OPTIONS, () -> new OptionsScreen(this, this.minecraft.options)));
        if (this.minecraft.hasSingleplayerServer() && !this.minecraft.getSingleplayerServer().isPublished()) {
            rowHelper.addChild(this.openScreenButton(SHARE_TO_LAN, () -> new ShareToLanScreen(this)));
        } else {
            rowHelper.addChild(this.openScreenButton(PLAYER_REPORTING, () -> new SocialInteractionsScreen(this)));
        }

        Component component = this.minecraft.isLocalServer() ? RETURN_TO_MENU : CommonComponents.GUI_DISCONNECT;
        this.disconnectButton = (Button) rowHelper.addChild(Button.builder(component, (button) -> {
            button.active = false;
            this.minecraft.getReportingContext().draftReportHandled(this.minecraft, this, this::onDisconnect, true);
        }).width(204).build(), 2);
        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
        gridLayout.visitWidgets(this::addRenderableWidget);
    }

    static void addOriginalFeedbackButtons(Screen screen, GridLayout.RowHelper rowHelper) {
        try {
            rowHelper
                    .addChild(openLinkButton(screen, FEEDBACK_TRAIN, new URI(StarRailExpressTitleScreen.FEEDBACK_URL)));
        } catch (URISyntaxException e) {
        }

        rowHelper.addChild(openLinkButton(screen, SEND_FEEDBACK,
                SharedConstants.getCurrentVersion().isStable() ? CommonLinks.RELEASE_FEEDBACK
                        : CommonLinks.SNAPSHOT_FEEDBACK));
        rowHelper.addChild(
                openLinkButton(screen, REPORT_BUGS, CommonLinks.SNAPSHOT_BUGS_FEEDBACK)).active = !SharedConstants
                        .getCurrentVersion().getDataVersion().isSideSeries();
    }

    @Environment(EnvType.CLIENT)
    static class FeedbackSubScreen extends Screen {
        private static final Component TITLE = Component.translatable("menu.feedback.title");
        public final Screen parent;
        private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

        protected FeedbackSubScreen(Screen screen) {
            super(TITLE);
            this.parent = screen;
        }

        protected void init() {
            this.layout.addTitleHeader(TITLE, this.font);
            GridLayout gridLayout = (GridLayout) this.layout.addToContents(new GridLayout());
            gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
            GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(1);
            addOriginalFeedbackButtons(this, rowHelper);
            this.layout.addToFooter(
                    Button.builder(CommonComponents.GUI_BACK, (button) -> this.onClose()).width(200).build());
            this.layout.visitWidgets(this::addRenderableWidget);
            this.repositionElements();
        }

        protected void repositionElements() {
            this.layout.arrangeElements();
        }

        public void onClose() {
            this.minecraft.setScreen(this.parent);
        }
    }

    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.roles.fool.FoolTarotVoteC2SPacket;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FoolTarotVoteScreen extends Screen {
    private final List<org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry> candidates;
    private final int durationSeconds;
    private long openTime;
    private EditBox searchWidget;
    private final List<VoteCandidateButton> candidateButtons = new ArrayList<>();
    private Button prevPageButton;
    private Button nextPageButton;
    private int currentPage = 0;
    private int totalPages = 0;
    private static final int ENTRIES_PER_PAGE = 12;

    public FoolTarotVoteScreen(List<org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry> candidates,
            int durationSeconds) {
        super(Component.translatable("screen.noellesroles.fool.vote.title"));
        this.candidates = List.copyOf(candidates);
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = Util.getMillis();
        this.currentPage = 0;
        initCandidateSelection();
    }

    private void initCandidateSelection() {
        refreshCandidateSelection(null);
    }

    private void onCandidateSearch(String text) {
        this.currentPage = 0;
        refreshCandidateSelection(text);
    }

    private void refreshCandidateSelection(String searchText) {
        for (VoteCandidateButton widget : candidateButtons) {
            this.removeWidget(widget);
        }
        candidateButtons.clear();

        if (prevPageButton != null) this.removeWidget(prevPageButton);
        if (nextPageButton != null) this.removeWidget(nextPageButton);

        List<org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry> filtered = new ArrayList<>();
        String loweredSearch = searchText != null ? searchText.toLowerCase() : null;
        for (org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry candidate : this.candidates) {
            String candidateName = resolvePlayerName(candidate.candidateId()).getString();
            if (loweredSearch == null || loweredSearch.isEmpty()
                    || candidateName.toLowerCase().contains(loweredSearch)
                    || candidate.candidateId().toString().contains(loweredSearch)) {
                filtered.add(candidate);
            }
        }

        int totalCandidates = filtered.size();
        this.totalPages = Math.max(1, (int) Math.ceil(totalCandidates / (double) ENTRIES_PER_PAGE));
        this.currentPage = Math.max(0, Math.min(this.currentPage, this.totalPages - 1));

        int startIndex = this.currentPage * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, totalCandidates);
        int countOnPage = Math.max(0, endIndex - startIndex);

        int columns = Math.min(4, Math.max(1, countOnPage == 0 ? 4 : countOnPage));
        int rows = Math.max(1, (int) Math.ceil(countOnPage / 4.0));
        int buttonWidth = 150;
        int buttonHeight = 32;
        int spacingX = 10;
        int spacingY = 8;
        int totalWidth = columns * (buttonWidth + spacingX) - spacingX;
        int totalHeight = rows * (buttonHeight + spacingY) - spacingY;
        int startX = (this.width - totalWidth) / 2;
        int startY = this.height / 2 - totalHeight / 2 + 18;

        if (searchWidget == null) {
            searchWidget = new EditBox(this.font, startX, startY - 38, Math.max(totalWidth, 240), 20,
                    Component.empty());
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder"));
            searchWidget.setResponder(this::onCandidateSearch);
            this.addRenderableWidget(searchWidget);
        }

        for (int index = startIndex; index < endIndex; index++) {
            org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry candidate = filtered.get(index);
            int localIndex = index - startIndex;
            int column = localIndex % 4;
            int row = localIndex / 4;
            int x = startX + column * (buttonWidth + spacingX);
            int y = startY + row * (buttonHeight + spacingY);
            VoteCandidateButton widget = new VoteCandidateButton(this, x, y, buttonWidth, buttonHeight, candidate);
            candidateButtons.add(widget);
            this.addRenderableWidget(widget);
        }

        int buttonY = startY + totalHeight + 20;
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (this.currentPage > 0) {
                        this.currentPage--;
                        refreshCandidateSelection(this.searchWidget != null ? this.searchWidget.getValue() : null);
                    }
                }).bounds(this.width / 2 - 90, buttonY, 70, 20).build();
        prevPageButton.active = this.currentPage > 0;
        this.addRenderableWidget(prevPageButton);

        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (this.currentPage < this.totalPages - 1) {
                        this.currentPage++;
                        refreshCandidateSelection(this.searchWidget != null ? this.searchWidget.getValue() : null);
                    }
                }).bounds(this.width / 2 + 20, buttonY, 70, 20).build();
        nextPageButton.active = this.currentPage < this.totalPages - 1;
        this.addRenderableWidget(nextPageButton);
    }

    private void submitVote(UUID candidateUuid) {
        ClientPlayNetworking.send(new FoolTarotVoteC2SPacket(candidateUuid));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private Component resolvePlayerName(UUID playerUuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            PlayerInfo playerInfo = client.getConnection().getPlayerInfo(playerUuid);
            if (playerInfo != null) {
                return Component.literal(playerInfo.getProfile().getName());
            }
        }
        String fallback = playerUuid.toString();
        return Component.literal(fallback.substring(0, Math.min(8, fallback.length())));
    }

    private ResourceLocation resolveSkin(UUID playerUuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            PlayerInfo playerInfo = client.getConnection().getPlayerInfo(playerUuid);
            if (playerInfo != null) {
                return playerInfo.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.get(playerUuid).texture();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int elapsedSeconds = (int) ((Util.getMillis() - this.openTime) / 1000L);
        int remainSeconds = Math.max(0, this.durationSeconds - elapsedSeconds);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 28, 0xF4E3A1);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.fool.vote.subtitle"),
            centerX, 44, 0xE0E0E0);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.fool.vote.remaining", remainSeconds),
            centerX, 58, 0xC9B46E);
        if (this.totalPages > 1) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.conspirator.page_info", this.currentPage + 1, this.totalPages),
                    centerX, 72, 0xE8C15B);
        }
    }

    private static class VoteCandidateButton extends Button {
        private final FoolTarotVoteScreen parent;
        private final org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry candidate;

        protected VoteCandidateButton(FoolTarotVoteScreen parent, int x, int y, int width, int height,
                org.agmas.noellesroles.roles.fool.FoolOpenTarotVoteS2CPacket.CandidateEntry candidate) {
            super(x, y, width, height, Component.empty(),
                    button -> parent.submitVote(candidate.candidateId()), DEFAULT_NARRATION);
            this.parent = parent;
            this.candidate = candidate;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            Font renderer = Minecraft.getInstance().font;
            int bgColor = this.isHovered() ? new Color(74, 50, 18, 215).getRGB() : new Color(42, 30, 12, 185).getRGB();
            //int borderColor = this.candidate.alive() ? 0xD6B665 : 0x9B6464;
            int borderColor =  0xD6B665 ;
            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            context.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

            PlayerFaceRenderer.draw(context, parent.resolveSkin(this.candidate.candidateId()), this.getX() + 4, this.getY() + 4, 24);

            Component name = parent.resolvePlayerName(this.candidate.candidateId());
            String nameText = renderer.plainSubstrByWidth(name.getString(), this.width - 62);
            //int nameColor = this.candidate.alive() ? 0xF7E6B0 : 0xD49A9A;
            int nameColor =  0xF7E6B0 ;
            context.drawString(renderer, nameText, this.getX() + 34, this.getY() + 6, nameColor);

            Component votesText = Component.translatable("screen.noellesroles.fool.vote.entry_votes",
                    this.candidate.voteCount());
            int votesWidth = renderer.width(votesText);
            context.drawString(renderer, votesText,
                    this.getX() + this.width - votesWidth - 5,
                    this.getY() + 6,
                    0xE8C15B);

//            Component stateText = this.candidate.alive()
//                    ? Component.translatable("screen.noellesroles.fool.vote.entry_alive")
//                    : Component.translatable("screen.noellesroles.fool.vote.entry_dead");
//            Component stateText = Component.translatable("screen.noellesroles.fool.vote.entry_alive");

//                context.drawString(renderer, stateText, this.getX() + 34, this.getY() + 18,
//                    this.candidate.alive() ? 0x7CD67C : 0xD67676);
//                context.drawString(renderer, stateText, this.getX() + 34, this.getY() + 18,
//                     0x7CD67C );
        }

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }
}
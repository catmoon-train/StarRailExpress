package org.agmas.noellesroles.client;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player pagination helper class to handle player list pagination
 * for screen mixins that need to display a list of players.
 */
public class PlayerPaginationHelper<T> {
    // Pagination constants
    private static final int PLAYERS_PER_PAGE = 8;
    
    // Pagination variables
    private int currentPage = 0;
    private List<T> playerEntries = List.of();
    
    // Managed widgets
    private final List<Button> managedButtons = new ArrayList<>();
    private final List<Button> managedPlayerWidgets = new ArrayList<>();
    
    // Callbacks
    private final PlayerWidgetCreator<T> widgetCreator;
    private final PaginationTextProvider textProvider;
    
    /**
     * Functional interface to create player widgets
     */
    public interface PlayerWidgetCreator<T> {
        Button createWidget(int x, int y, T playerEntry, int index);
    }
    
    /**
     * Functional interface to provide translation keys for pagination
     */
    public interface PaginationTextProvider {
        String getPageTranslationKey();
        String getPrevTranslationKey();
        String getNextTranslationKey();
    }
    
    /**
     * Constructor
     * @param widgetCreator Callback to create player widgets
     * @param textProvider Callback to provide translation keys
     */
    public PlayerPaginationHelper(PlayerWidgetCreator<T> widgetCreator, PaginationTextProvider textProvider) {
        this.widgetCreator = widgetCreator;
        this.textProvider = textProvider;
    }
    
    /**
     * Set the player entries to be paginated
     */
    public void setPlayerEntries(List<T> playerEntries) {
        this.playerEntries = List.copyOf(playerEntries);
        this.currentPage = 0; // Reset to first page when entries change
    }
    
    /**
     * Draw pagination information
     */
    public void drawPagination(GuiGraphics context, Screen screen, int centerY) {
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            Component pageText = Component.translatable(textProvider.getPageTranslationKey(), currentPage + 1, totalPages);
            int pageTextWidth = Minecraft.getInstance().font.width(pageText);
            context.drawString(Minecraft.getInstance().font, pageText,
                    screen.width / 2 - pageTextWidth / 2,
                    centerY + 120, Color.WHITE.getRGB());
        }
    }
    
    /**
     * Add widgets for the current page
     */
    public void addPageWidgets(Screen screen) {
        int totalPages = getTotalPages();
        if (totalPages == 0) {
            return;
        }
        
        // Calculate positions
        int apart = 36;
        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playerEntries.size());
        int visibleCount = endIndex - startIndex;
        int x = screen.width / 2 - visibleCount * apart / 2 + 9;
        int centerY = (screen.height - 32) / 2;
        int y = centerY + 80;

        // Add player widgets for current page
        for (int i = startIndex; i < endIndex; ++i) {
            T playerEntry = playerEntries.get(i);
            Button playerWidget = widgetCreator.createWidget(x + apart * (i - startIndex), y, playerEntry, i);
            if (playerWidget != null) {
                managedPlayerWidgets.add(playerWidget);
            }
        }

        // Add pagination buttons if there are multiple pages
        if (totalPages > 1) {
            int buttonY = y + 40;
            
            // Previous page button
            Button prevButton = Button.builder(Component.translatable(textProvider.getPrevTranslationKey()), button -> {
                if (currentPage > 0) {
                    currentPage--;
                    refreshPage(screen);
                }
            }).bounds(screen.width / 2 - 80, buttonY, 50, 20).build();
            
            // Next page button
            Button nextButton = Button.builder(Component.translatable(textProvider.getNextTranslationKey()), button -> {
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshPage(screen);
                }
            }).bounds(screen.width / 2 + 30, buttonY, 50, 20).build();
            
            // Store managed buttons
            managedButtons.add(prevButton);
            managedButtons.add(nextButton);
            
            ((ScreenWithChildren) screen).addDrawableChild(prevButton);
            ((ScreenWithChildren) screen).addDrawableChild(nextButton);
        }
    }
    
    /**
     * Clear only the widgets managed by this helper
     */
    public void clearManagedWidgets(ScreenWithChildren screen) {
        // Clear pagination buttons
        for (Button button : managedButtons) {
            screen.removeDrawableChild(button);
        }
        managedButtons.clear();
        
        // Clear player widgets
        for (Button widget : managedPlayerWidgets) {
            screen.removeDrawableChild(widget);
        }
        managedPlayerWidgets.clear();
    }
    
    /**
     * Refresh the current page
     */
    public void refreshPage(Screen screen) {
        // Clear only managed widgets
        clearManagedWidgets((ScreenWithChildren) screen);
        
        // Recalculate pagination and add new widgets
        addPageWidgets(screen);
    }
    
    /**
     * Get the total number of pages
     */
    private int getTotalPages() {
        return playerEntries.isEmpty() ? 0 : (int) Math.ceil((double) playerEntries.size() / PLAYERS_PER_PAGE);
    }
    
    /**
     * Interface to access screen children operations
     */
    public interface ScreenWithChildren {
        void addDrawableChild(Button button);
        void removeDrawableChild(Button button);
        void clearChildren();
    }
}
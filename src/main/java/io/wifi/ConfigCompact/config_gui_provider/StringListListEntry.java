// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.ConfigCompact.config_gui_provider;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;

import org.jetbrains.annotations.ApiStatus.Internal;

@Environment(EnvType.CLIENT)
public class StringListListEntry extends
      AbstractTextFieldListListEntry<String, io.wifi.ConfigCompact.config_gui_provider.StringListListEntry.StringListCell, StringListListEntry> {

   @Internal
   public StringListListEntry(Component fieldName, List<String> value, boolean defaultExpanded,
         Supplier<Optional<Component[]>> tooltipSupplier, Consumer<List<String>> saveConsumer,
         Supplier<List<String>> defaultValue, Component resetButtonKey) {
      this(fieldName, value, defaultExpanded, tooltipSupplier, saveConsumer, defaultValue, resetButtonKey, false);
   }

   @Internal
   public StringListListEntry(Component fieldName, List<String> value, boolean defaultExpanded,
         Supplier<Optional<Component[]>> tooltipSupplier, Consumer<List<String>> saveConsumer,
         Supplier<List<String>> defaultValue, Component resetButtonKey, boolean requiresRestart) {
      this(fieldName, value, defaultExpanded, tooltipSupplier, saveConsumer, defaultValue, resetButtonKey,
            requiresRestart, true, true);
   }

   @Internal
   public StringListListEntry(Component fieldName, List<String> value, boolean defaultExpanded,
         Supplier<Optional<Component[]>> tooltipSupplier, Consumer<List<String>> saveConsumer,
         Supplier<List<String>> defaultValue, Component resetButtonKey, boolean requiresRestart,
         boolean deleteButtonEnabled, boolean insertInFront) {
      super(fieldName, value, defaultExpanded, tooltipSupplier, saveConsumer, defaultValue, resetButtonKey,
            requiresRestart, deleteButtonEnabled, insertInFront, StringListCell::new);
   }

   public StringListListEntry self() {
      return this;
   }

   public static class StringListCell
         extends AbstractTextFieldListListEntry.AbstractTextFieldListCell<String, StringListCell, StringListListEntry> {
      public StringListCell(String value, StringListListEntry listListEntry) {
         super(value, listListEntry);
      }

      protected @Nullable String substituteDefault(@Nullable String value) {
         return value == null ? "" : value;
      }

      protected boolean isValidText(@NotNull String text) {
         return true;
      }

      public String getValue() {
         return this.widget.getValue();
      }

      public Optional<Component> getError() {
         return Optional.empty();
      }
   }
}

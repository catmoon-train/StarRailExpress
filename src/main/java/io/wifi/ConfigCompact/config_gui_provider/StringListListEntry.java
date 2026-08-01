/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.ConfigCompact.config_gui_provider;

import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

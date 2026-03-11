package org.agmas.noellesroles;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.NotNull;

public interface ModDataComponentTypes {
   DataComponentType<CompoundTag> COOKED = register("cooked", (tagBuilder) -> {
      return tagBuilder.persistent(CompoundTag.CODEC);
   });

   private static <T> DataComponentType<T> register(String name,
         @NotNull UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
      return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Noellesroles.id(name),
            builderOperator.apply(DataComponentType.builder()).build());
   }

   public static Map<Integer, Float> getCookedFoodInfo(CompoundTag tag) {
      Map<Integer, Float> map = new HashMap<>();
      if (tag.contains("effects", Tag.TAG_LIST)) {
         ListTag listTag = tag.getList("effects", Tag.TAG_COMPOUND);
         for (Tag i : listTag) {
            if (i.getId() == CompoundTag.TAG_COMPOUND) {
               CompoundTag _t = (CompoundTag) i;
               if (_t.contains("time") && _t.contains("id")) {
                  map.put(_t.getInt("id"), _t.getFloat("time"));
               }
            }
         }
      }

      return map;
   }

   public static CompoundTag cookedFood(Map<Integer, Float> map) {
      var tag = new CompoundTag();
      var listTag = new ListTag();
      for (var it : map.entrySet()) {
         var _tag = new CompoundTag();
         _tag.putInt("id", it.getKey());
         _tag.putFloat("time", it.getValue());
         listTag.add(_tag);
      }
      tag.put("effects", listTag);
      return tag;
   }
}

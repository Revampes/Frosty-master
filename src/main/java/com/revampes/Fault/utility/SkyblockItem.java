

package com.revampes.Fault.utility;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.LoreComponent;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType") // intended for lazy init
public class SkyblockItem {
    private static final Object2ObjectOpenHashMap<ItemStack, SkyblockItem> CACHE = new Object2ObjectOpenHashMap<>();

    public ItemStack itemStack;

    private NbtCompound customData;
    private Optional<String> id;
    private Optional<List<Text>> styledLoreLines;

    private SkyblockItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public static Optional<SkyblockItem> from(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return Optional.empty();
        SkyblockItem instance = CACHE.get(itemStack);
        if (instance != null) {
            return Optional.of(instance);
        }

        NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return Optional.empty();

        instance = new SkyblockItem(itemStack);
        instance.customData = customData.copyNbt();
        CACHE.put(itemStack, instance);
        return Optional.of(instance);
    }

    public static void clearCache(MinecraftClient client) {
        CACHE.clear();
    }

    public Optional<String> getID() {
        if (id == null) {
            id = Optional.ofNullable(customData.get("id"))
                    .flatMap(NbtElement::asString);
        }
        return id;
    }

    public Optional<List<Text>> getStyledLoreLines() {
        if (styledLoreLines == null) {
            styledLoreLines = Optional.ofNullable(itemStack.get(DataComponentTypes.LORE))
                    .map(LoreComponent::styledLines);
        }
        return styledLoreLines;
    }
}
package com.aftertime.ratallofyou.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.text.Text;

import java.util.Optional;

public class HotbarSwapUtils {
    public static final int NOT_FOUND = -1;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Movement key bindings
    private static final KeyBinding[] MOVEMENT_KEYS = {
        mc.options.forwardKey,
        mc.options.backKey,
        mc.options.leftKey,
        mc.options.rightKey
    };
    
    // Store original key states
    private static boolean[] originalKeyStates = new boolean[4];
    private static boolean movementLocked = false;

    public static void stopInputs() {
        if (!movementLocked) {
            // Save current key states
            for (int i = 0; i < MOVEMENT_KEYS.length; i++) {
                originalKeyStates[i] = MOVEMENT_KEYS[i].isPressed();
            }
            movementLocked = true;
        }
        
        // Set all movement keys to not pressed
        for (KeyBinding key : MOVEMENT_KEYS) {
            KeyBinding.setKeyPressed(key.getDefaultKey(), false);
        }
    }

    public static void restartMovement() {
        if (movementLocked) {
            // Restore original key states
            for (int i = 0; i < MOVEMENT_KEYS.length; i++) {
                KeyBinding.setKeyPressed(MOVEMENT_KEYS[i].getDefaultKey(), originalKeyStates[i]);
            }
            movementLocked = false;
        }
    }

    /**
     * Returns the UUID of a SkyBlock item from its NBT.
     */
    public static String getUUID(ItemStack stack) {
        if (stack == null) return null;
        try {
            NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (comp == null) return null;
            NbtCompound tag = comp.copyNbt();
            if (tag == null) return null;
            if (!tag.contains("ExtraAttributes")) return null;

            Optional<NbtCompound> extraOptional = tag.getCompound("ExtraAttributes");
            if (!extraOptional.isPresent()) return null;
            NbtCompound extra = extraOptional.get();

            Optional<String> uuidOptional = extra.getString("uuid");
            if (!uuidOptional.isPresent()) return null;
            String s = uuidOptional.get();
            return s != null && !s.isEmpty() ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the SkyBlock ID from the item's NBT.
     */
    public static String getSkyblockID(ItemStack stack) {
        if (stack == null) return null;
        try {
            NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (comp == null) return null;
            NbtCompound tag = comp.copyNbt();
            if (tag == null) return null;
            if (!tag.contains("ExtraAttributes")) return null;

            Optional<NbtCompound> extraOptional = tag.getCompound("ExtraAttributes");
            if (!extraOptional.isPresent()) return null;
            NbtCompound extra = extraOptional.get();

            Optional<String> idOptional = extra.getString("id");
            if (!idOptional.isPresent()) return null;
            String s = idOptional.get();
            return s != null && !s.isEmpty() ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the display name of the item as a plain string.
     */
    public static String getDisplayName(ItemStack stack) {
        if (stack == null) return "None";
        Text name = stack.getName();
        return name.getString();
    }
}
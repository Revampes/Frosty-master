package com.revampes.Fault.utility;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import com.revampes.Fault.modules.ModuleManager;

@Environment(EnvType.CLIENT)
public final class BlockAnimationUtils {
    private BlockAnimationUtils() {}

    public static final int DEFAULT_ITEM_USE_DURATION = 72_000;

    public static boolean isPlayerBlockingWithSword(PlayerEntity player) {
        if (player == null) return false;
        return isPlayerRightClicking() && canSwordBlock(player);
    }

    public static boolean isPlayerRightClicking() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return false;
        return client.options.useKey.isPressed();
    }

    public static boolean canSwordBlock(PlayerEntity player) {
        if (!ModuleManager.blockAnimation.isEnabled()) return false;
        if (player == null) return false;
        Item mainHandItem = player.getMainHandStack().getItem();
        Item offHandItem = player.getOffHandStack().getItem();
        return isSword(mainHandItem) || isSword(offHandItem);
    }

    public static boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD ||
               item == Items.STONE_SWORD ||
               item == Items.IRON_SWORD ||
               item == Items.GOLDEN_SWORD ||
               item == Items.DIAMOND_SWORD ||
               item == Items.NETHERITE_SWORD;
    }
}

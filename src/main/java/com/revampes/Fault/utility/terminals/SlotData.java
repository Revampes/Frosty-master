package com.revampes.Fault.utility.terminals;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class SlotData {
    public int slot;
    public int id;
    public int meta;
    public int size;
    public String name;
    public String itemType; // Registry name like "minecraft:red_stained_glass_pane"
    public boolean enchanted;
    public int rawColor; // for metadata-based colors

    public SlotData(int slot, ItemStack itemStack, String name) {
        this.slot = slot;
        this.size = itemStack.getCount();
        this.name = name;
        // Some terminals mark clicked items with glint without a classic enchantment list.
        this.enchanted = itemStack.hasEnchantments() || itemStack.hasGlint();
        
        // Get proper item ID from registry
        if (!itemStack.isEmpty()) {
            this.id = Registries.ITEM.getRawId(itemStack.getItem());
            this.meta = itemStack.getDamage();
            this.itemType = Registries.ITEM.getId(itemStack.getItem()).toString();
        } else {
            this.id = -1;
            this.meta = -1;
            this.itemType = "";
        }
    }

    @Override
    public String toString() {
        return "SlotData{" +
                "slot=" + slot +
                ", itemType='" + itemType + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }
}

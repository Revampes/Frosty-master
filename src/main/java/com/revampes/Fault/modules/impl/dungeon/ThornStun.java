package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.MouseButtonEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.KeyAction;
import com.revampes.Fault.utility.Utils;
import com.revampes.Fault.utility.SkyblockItem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;

public class ThornStun extends Module {

    private final ButtonSetting afkThornStun = new ButtonSetting("AFK Thorn Stun", true);

    public ThornStun() {
        super("Thorn Stun", category.Dungeon);
        this.registerSetting(afkThornStun);
    }

    private boolean isClicking = false;

    private boolean isHoldingValidWeapon() {
        if (mc.player == null) {
            return false;
        }

        ItemStack mainHandItem = mc.player.getMainHandStack();
        if (mainHandItem == null || mainHandItem.isEmpty()) {
            return false;
        }

        SkyblockItem skyblockItem = SkyblockItem.from(mainHandItem).orElse(null);
        if (skyblockItem == null) {
            return false;
        }

        String itemID = skyblockItem.getID().orElse("");
        return itemID.equals("TRIBAL_SPEAR") || itemID.equals("BONE_BOOMERANG");
    }

    @EventHandler
    public void onMouseButton(MouseButtonEvent event) {
        // Check if feature is enabled
        if (!afkThornStun.isToggled()) {
            return;
        }

        // Check if in dungeon and on floor 4
        if (!DungeonUtils.isInDungeon()) {
            return;
        }

        String floorStr = DungeonUtils.getDungeonFloor();
        if (floorStr == null || !floorStr.endsWith("4")) {
            return;
        }

        // Use the client's useKey (right click) so this works with custom bindings
        int useKeyCode = mc.options.useKey.getDefaultKey().getCode();
        if (event.button != useKeyCode) {
            return;
        }

        // If currently in auto-hold mode, cancel incoming mouse events so player can't interrupt
        if (isClicking) {
            event.cancel();
        }

        // On press: toggle the auto-hold state only when holding the valid weapon
        if (event.action == KeyAction.Press) {
            boolean holdingWeapon = isHoldingValidWeapon();
            
            if (holdingWeapon) {
                isClicking = !isClicking;
                mc.options.useKey.setPressed(isClicking);
                event.cancel();
            }
        } else if (event.action == KeyAction.Release) {
            // If auto-hold is active, swallow release events so the simulated hold persists
            if (isClicking) {
                event.cancel();
            }
        }
    }

    @EventHandler
    public void onPreUpdate(com.revampes.Fault.events.impl.PreUpdateEvent event) {
        // If toggle is on but weapon is no longer held, disable the toggle
        if (isClicking && !isHoldingValidWeapon()) {
            isClicking = false;
            mc.options.useKey.setPressed(false);
        }
    }
}
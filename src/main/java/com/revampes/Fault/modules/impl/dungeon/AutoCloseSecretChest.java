package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

public class AutoCloseSecretChest extends Module {

	public AutoCloseSecretChest() {
		super("Auto Close Secret Chest", category.Dungeon);
	}

	@Override
	public String getDesc() {
		return "Automatically closes regular chest screens while in dungeons.";
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (!DungeonUtils.isInDungeon() || mc.player == null) {
			return;
		}

		if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
			return;
		}

		String title = Utils.stripColor(screen.getTitle().getString());
		if ("Chest".equals(title)) {
			mc.player.closeHandledScreen();
		}
	}
}

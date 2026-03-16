package com.revampes.Fault.commands.impl;

import com.revampes.Fault.commands.Command;
import com.revampes.Fault.utility.Utils;


import static com.revampes.Fault.Revampes.mc;

public class ItemCommand extends Command {
    public ItemCommand() {
        super("item", "Get item info", "i");
    }

    @Override
    public void execute(String[] args) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.player.getMainHandStack() != null && mc.player.getMainHandStack().getCustomName() != null) {
            String itemName = Utils.getLiteral(mc.player.getMainHandStack().getCustomName().toString());
            Utils.addToClipboard(itemName);
        }
    }
}

package com.revampes.Fault.commands.impl;

import com.revampes.Fault.commands.Command;
import com.revampes.Fault.utility.Utils;

import static com.revampes.Fault.Revampes.mc;

public class PosCommand extends Command {
    public PosCommand() {
        super("position", "Get the block pos under player", "pos");
    }

    @Override
    public void execute(String[] args) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.player.getBlockPos().down() != null) {
            String pos = Math.round(mc.player.getBlockPos().down().getX()) + " " + Math.round(mc.player.getBlockPos().down().getY()) + " " + Math.round(mc.player.getBlockPos().down().getZ());
            Utils.addToClipboard(pos);
        }
    }
}

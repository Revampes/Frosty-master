package com.revampes.Fault.commands.impl;

import net.minecraft.text.Text;
import com.revampes.Fault.commands.Command;
import com.revampes.Fault.commands.CommandManager;
import com.revampes.Fault.utility.Utils;

import static com.revampes.Fault.Revampes.mc;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Help info", "h");
    }

    @Override
    public void execute(String[] args) {
        if (!Utils.nullCheck()) {
            return;
        }
        Utils.addChatMessage("==============================");
        for (Command c : CommandManager.getCommands()) {
            mc.player.sendMessage(Text.literal("§b." + c.getName() + "  §7---  " + c.getDescription() + " - (§b." + c.getAliases() + "§7)"), false);
        }
        Utils.addChatMessage("==============================");
    }
}

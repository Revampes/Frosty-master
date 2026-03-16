package com.revampes.Fault.commands.impl;

import com.revampes.Fault.commands.Command;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.utility.Utils;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Toggles a module", "t");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendError("Usage: .toggle <module>");
            return;
        }

        Module module = ModuleManager.getModuleByName(args[0]);
        if (module == null) {
            sendError("Module not found: " + args[0]);
            return;
        }

        module.toggle();
        Utils.addChatMessage(module.getName() + " " + (module.isEnabled() ? "§a§lENABLED" : "§c§lDISABLED"));
    }
}

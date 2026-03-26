package com.revampes.Fault.commands.impl;

import com.revampes.Fault.commands.Command;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.modules.impl.other.HotbarSwap;
import com.revampes.Fault.utility.Utils;

public class HotbarCommand extends Command {
    public HotbarCommand() {
        super("hotbar", "Hotbar swap commands", "hb");
    }

    @Override
    public void execute(String[] args) {
        com.revampes.Fault.modules.Module module = com.revampes.Fault.modules.Module.getModule(HotbarSwap.class);
        HotbarSwap mod = module == null ? null : (HotbarSwap) module;
        if (mod == null) { sendError("HotbarSwap module not loaded"); return; }
        if (!mod.isEnabled()) { sendError("HotbarSwap module is disabled. Toggle it first (.toggle HotbarSwap)"); return; }

        if (args.length == 0) { sendError("Usage: .hotbar save <name> | load <name> | list | del <name>"); return; }
        String cmd = args[0].toLowerCase();
        try {
            switch (cmd) {
                case "save", "s" -> {
                    if (args.length < 2) { sendError("Usage: .hotbar save <name>"); return; }
                    String name = args[1]; mod.saveCurrentHotbar(name); Utils.addChatMessage("Saved hotbar " + name);
                }
                case "load", "l" -> {
                    if (args.length < 2) { sendError("Usage: .hotbar load <name>"); return; }
                    String name = args[1]; mod.loadPresetByName(name); Utils.addChatMessage("Triggered preset " + name);
                }
                case "list" -> { for (HotbarSwap.Hotbar h : mod.getPresetsView()) { Utils.addChatMessage(h.name); } }
                case "del", "delete" -> {
                    if (args.length < 2) { sendError("Usage: .hotbar del <name>"); return; }
                    mod.deletePreset(args[1]);
                }
                default -> sendError("Unknown subcommand: " + cmd);
            }
        } catch (Exception e) { sendError("Error: " + e.getMessage()); }
    }
}

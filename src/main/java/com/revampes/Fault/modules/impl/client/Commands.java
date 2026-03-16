package com.revampes.Fault.modules.impl.client;

import com.revampes.Fault.modules.Module;

public class Commands extends Module {
    public Commands() {
        super("Commands", category.Client);
    }

    @Override
    public String getDesc() {
        return "Client commands (.help)";
    }
}

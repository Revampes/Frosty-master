package com.revampes.AfterTimeFault.modules.impl.client;

import com.revampes.AfterTimeFault.modules.Module;

public class Commands extends Module {
    public Commands() {
        super("Commands", category.Client);
    }

    @Override
    public String getDesc() {
        return "Client commands (.help)";
    }
}

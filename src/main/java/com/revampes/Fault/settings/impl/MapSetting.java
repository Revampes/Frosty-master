package com.revampes.Fault.settings.impl;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.Setting;

import java.util.HashMap;
import java.util.Map;

public class MapSetting extends Setting {
    private Map<String, Map<Integer, Integer>> value;

    public MapSetting(String name, Map<String, Map<Integer, Integer>> defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public Map<String, Map<Integer, Integer>> getValue() {
        return value;
    }

    public void setValue(Map<String, Map<Integer, Integer>> value) {
        this.value = value;
    }
}

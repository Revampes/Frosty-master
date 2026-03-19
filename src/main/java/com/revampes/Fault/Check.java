package com.revampes.Fault;
import net.minecraft.client.gui.screen.Screen;

public class Check {
    public static void main(String[] args) {
        for (java.lang.reflect.Method m : Screen.class.getMethods()) {
            if (m.getName().toLowerCase().contains("shift") || m.getName().toLowerCase().contains("control")) {
                System.out.println("BINGO METHOD: " + m.getName() + " STATIC: " + java.lang.reflect.Modifier.isStatic(m.getModifiers()));
            }
        }
    }
}
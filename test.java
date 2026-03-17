
public class test {
    public static void main(String[] args) {
        for(java.lang.reflect.Field f : net.minecraft.client.gui.hud.InGameHud.class.getDeclaredFields()) {
            System.out.println(f.getName() + ' ' + f.getType().getName());
        }
    }
}

package g_mungus.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeybinds {
    public static final KeyMapping KEY_A = new KeyMapping(
            "key.zps.a",
            GLFW.GLFW_KEY_W,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_B = new KeyMapping(
            "key.zps.b",
            GLFW.GLFW_KEY_A,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_C = new KeyMapping(
            "key.zps.c",
            GLFW.GLFW_KEY_S,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_D = new KeyMapping(
            "key.zps.d",
            GLFW.GLFW_KEY_D,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_E = new KeyMapping(
            "key.zps.e",
            GLFW.GLFW_KEY_UP,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_F = new KeyMapping(
            "key.zps.f",
            GLFW.GLFW_KEY_LEFT,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_G = new KeyMapping(
            "key.zps.g",
            GLFW.GLFW_KEY_DOWN,
            "key.categories.zps"
    );

    public static final KeyMapping KEY_H = new KeyMapping(
            "key.zps.h",
            GLFW.GLFW_KEY_RIGHT,
            "key.categories.zps"
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ModKeybinds.KEY_A);
        event.register(ModKeybinds.KEY_B);
        event.register(ModKeybinds.KEY_C);
        event.register(ModKeybinds.KEY_D);
        event.register(ModKeybinds.KEY_E);
        event.register(ModKeybinds.KEY_F);
        event.register(ModKeybinds.KEY_G);
        event.register(ModKeybinds.KEY_H);
    }
} 
package dev.lyricsky;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lyricsky.ui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class LyricsSkyClient implements ClientModInitializer {
    private static KeyMapping openKey, toggleKey, recenterKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        LyricsHud.register();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lyricsky", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsky.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsky.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, cat));
        recenterKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsky.recenter", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.setScreen(new MainScreen());
            while (toggleKey.consumeClick()) PlayerController.togglePause();
            while (recenterKey.consumeClick()) PlayerController.recenter();
        });
    }
}

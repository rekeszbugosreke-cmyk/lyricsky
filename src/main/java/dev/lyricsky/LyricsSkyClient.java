package dev.lyricsky;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lyricsky.ui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class LyricsSkyClient implements ClientModInitializer {
    private static KeyMapping openKey, toggleKey, recenterKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        LyricsHud.register();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lyricsky", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsky.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, cat));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsky.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_P, cat));
        recenterKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsky.recenter", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.gui.setScreen(new MainScreen());
            while (toggleKey.consumeClick()) PlayerController.togglePause();
            while (recenterKey.consumeClick()) PlayerController.recenter();
        });
    }
}

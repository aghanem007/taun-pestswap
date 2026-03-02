package com.pestswap;

import com.pestswap.gui.SwapConfigScreen;
import com.pestswap.modules.GearSwapper;
import com.pestswap.modules.TabWatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PestSwapClient implements ClientModInitializer {

    private static KeyMapping configKey;
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        SwapConfig.load();

        Identifier categoryId = Identifier.fromNamespaceAndPath("pestswap", "main");
        KeyMapping.Category category = new KeyMapping.Category(categoryId);

        configKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pestswap.config", GLFW.GLFW_KEY_O, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pestswap.toggle", GLFW.GLFW_KEY_K, category));

        // Key handlers
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;
            while (configKey.consumeClick()) {
                client.setScreen(SwapConfigScreen.create(client.screen));
            }
            while (toggleKey.consumeClick()) {
                TabWatcher.enabled = !TabWatcher.enabled;
                if (TabWatcher.enabled) {
                    TabWatcher.reset();
                    GearSwapper.reset();
                    client.player.displayClientMessage(
                            Component.literal("\u00A7aPestSwap enabled."), true);
                } else {
                    TabWatcher.reset();
                    GearSwapper.reset();
                    // Restart the farming script so Taunahi picks back up
                    GearSwapper.sendCommand(client, SwapConfig.restartScript);
                    client.player.displayClientMessage(
                            Component.literal("\u00A7cPestSwap disabled. Script restarted."), true);
                }
            }
        });

        // Main tick loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            // Handle GUI menus when open
            if (client.screen instanceof AbstractContainerScreen<?> containerScreen) {
                GearSwapper.handleWardrobeMenu(client, containerScreen);
                GearSwapper.handleEquipmentMenu(client, containerScreen);
            }

            // Tab list watcher
            TabWatcher.tick(client);

            // Cursor cleanup
            GearSwapper.cleanupTick(client);
        });

        // Reset state on server join (handles Taunahi rest reconnects, lobby warps, etc.)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            GearSwapper.reset();
            TabWatcher.reset();
        });

        // Disabled by default — press K to enable
        TabWatcher.enabled = false;
    }
}

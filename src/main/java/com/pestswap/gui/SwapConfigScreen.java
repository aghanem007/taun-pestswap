package com.pestswap.gui;

import com.pestswap.SwapConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SwapConfigScreen {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("PestSwap Config"))
                .setSavingRunnable(SwapConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(builder.getEntryBuilder()
                .startEnumSelector(Component.literal("Gear Swap Mode"),
                        SwapConfig.GearSwapMode.class, SwapConfig.gearSwapMode)
                .setDefaultValue(SwapConfig.GearSwapMode.NONE)
                .setSaveConsumer(v -> SwapConfig.gearSwapMode = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startBooleanToggle(Component.literal("Auto-Equipment"), SwapConfig.autoEquipment)
                .setDefaultValue(true)
                .setSaveConsumer(v -> SwapConfig.autoEquipment = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Wardrobe Slot: Farming"),
                        SwapConfig.wardrobeSlotFarming, 1, 9)
                .setDefaultValue(1)
                .setSaveConsumer(v -> SwapConfig.wardrobeSlotFarming = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Wardrobe Slot: Pest"),
                        SwapConfig.wardrobeSlotPest, 1, 9)
                .setDefaultValue(2)
                .setSaveConsumer(v -> SwapConfig.wardrobeSlotPest = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Pest Swap Timing (sec left on cooldown)"),
                        SwapConfig.autoEquipmentFarmingTime, 1, 300)
                .setDefaultValue(170)
                .setSaveConsumer(v -> SwapConfig.autoEquipmentFarmingTime = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Pest Threshold"),
                        SwapConfig.pestThreshold, 1, 8)
                .setDefaultValue(5)
                .setSaveConsumer(v -> SwapConfig.pestThreshold = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("GUI Click Delay (ms)"),
                        SwapConfig.guiClickDelay, 100, 2000)
                .setDefaultValue(500)
                .setSaveConsumer(v -> SwapConfig.guiClickDelay = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Equipment Swap Delay (ms)"),
                        SwapConfig.equipmentSwapDelay, 100, 2000)
                .setDefaultValue(500)
                .setSaveConsumer(v -> SwapConfig.equipmentSwapDelay = v)
                .build());

        general.addEntry(builder.getEntryBuilder()
                .startStrField(Component.literal("Restart Script Command"),
                        SwapConfig.restartScript)
                .setDefaultValue(".ez-startscript netherwart:1")
                .setSaveConsumer(v -> SwapConfig.restartScript = v)
                .build());

        return builder.build();
    }
}

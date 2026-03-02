package com.pestswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SwapConfig {

    public enum GearSwapMode {
        NONE, WARDROBE, ROD
    }

    public static GearSwapMode gearSwapMode = GearSwapMode.NONE;
    public static boolean autoEquipment = true;
    public static int wardrobeSlotFarming = 1;
    public static int wardrobeSlotPest = 2;
    public static int autoEquipmentFarmingTime = 170;
    public static int pestThreshold = 5;
    public static int guiClickDelay = 500;
    public static int equipmentSwapDelay = 500;
    public static String restartScript = ".ez-startscript netherwart:1";

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("pestswap_config.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        ConfigData data = new ConfigData();
        data.gearSwapMode = gearSwapMode;
        data.autoEquipment = autoEquipment;
        data.wardrobeSlotFarming = wardrobeSlotFarming;
        data.wardrobeSlotPest = wardrobeSlotPest;
        data.autoEquipmentFarmingTime = autoEquipmentFarmingTime;
        data.pestThreshold = pestThreshold;
        data.guiClickDelay = guiClickDelay;
        data.equipmentSwapDelay = equipmentSwapDelay;
        data.restartScript = restartScript;

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                gearSwapMode = data.gearSwapMode != null ? data.gearSwapMode : GearSwapMode.NONE;
                autoEquipment = data.autoEquipment;
                wardrobeSlotFarming = data.wardrobeSlotFarming;
                wardrobeSlotPest = data.wardrobeSlotPest;
                autoEquipmentFarmingTime = data.autoEquipmentFarmingTime;
                pestThreshold = data.pestThreshold;
                guiClickDelay = data.guiClickDelay;
                equipmentSwapDelay = data.equipmentSwapDelay;
                if (data.restartScript != null && !data.restartScript.isBlank())
                    restartScript = data.restartScript;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        GearSwapMode gearSwapMode = GearSwapMode.NONE;
        boolean autoEquipment = true;
        int wardrobeSlotFarming = 1;
        int wardrobeSlotPest = 2;
        int autoEquipmentFarmingTime = 170;
        int pestThreshold = 5;
        int guiClickDelay = 500;
        int equipmentSwapDelay = 500;
        String restartScript = ".ez-startscript netherwart:1";
    }
}

package com.pestswap.modules;

import com.pestswap.SwapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabWatcher {

    private static final Pattern PESTS_ALIVE_PATTERN =
            Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN =
            Pattern.compile("(?i)Cooldown:\\s*\\(?(READY|MAX\\s*PESTS?|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");

    // Cycle states:
    //   IDLE           = waiting for cooldown to drop, no swaps done
    //   SWAPPED_TO_PEST = pest gear on, waiting for pests to hit threshold
    //   SWAPPED_BACK    = farming gear restored, waiting for cooldown to reset (new cycle)
    public enum CycleState { IDLE, SWAPPED_TO_PEST, SWAPPED_BACK }

    public static volatile CycleState cycleState = CycleState.IDLE;
    public static volatile boolean enabled = false;

    public static void reset() {
        cycleState = CycleState.IDLE;
    }

    public static void tick(Minecraft client) {
        if (!enabled)
            return;
        if (client.player == null || client.getConnection() == null)
            return;
        if (GearSwapper.isSwapping || GearSwapper.isSwappingWardrobe || GearSwapper.isSwappingEquipment)
            return;

        int aliveCount = -1;
        Collection<PlayerInfo> players = client.getConnection().getListedOnlinePlayers();

        for (PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                name = String.valueOf(info.getProfile());
            }

            String clean = name.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            String normalized = clean.replace('\u00A0', ' ');

            // Parse pest alive count
            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(normalized);
            if (aliveMatcher.find()) {
                int found = Integer.parseInt(aliveMatcher.group(1));
                if (found > aliveCount)
                    aliveCount = found;
            }

            if (normalized.toUpperCase().contains("MAX PESTS")) {
                aliveCount = 99;
            }

            // Parse cooldown
            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(normalized);
            if (cooldownMatcher.find()) {
                String cdVal = cooldownMatcher.group(1).toUpperCase();
                int cooldownSeconds = -1;

                if (cdVal.contains("MAX PEST")) {
                    aliveCount = 99;
                    cooldownSeconds = 999;
                } else if (cdVal.equalsIgnoreCase("READY")) {
                    cooldownSeconds = 0;
                } else {
                    int m = 0;
                    int s = 0;
                    if (cooldownMatcher.group(2) != null)
                        m = Integer.parseInt(cooldownMatcher.group(2));
                    if (cooldownMatcher.group(3) != null)
                        s = Integer.parseInt(cooldownMatcher.group(3));

                    if (m > 0 || s > 0) {
                        cooldownSeconds = (m * 60) + s;
                    }
                }

                // Reset cycle when cooldown goes high again (new pest cycle)
                if (cycleState == CycleState.SWAPPED_BACK) {
                    if (SwapConfig.autoEquipment) {
                        if (cooldownSeconds > SwapConfig.autoEquipmentFarmingTime) {
                            cycleState = CycleState.IDLE;
                        }
                    } else {
                        if (cooldownSeconds > 3) {
                            cycleState = CycleState.IDLE;
                        }
                    }
                }

                // Pest swap: cooldown low + IDLE state + pests below threshold
                if (cycleState == CycleState.IDLE
                        && cooldownSeconds != -1 && cooldownSeconds >= 0
                        && !GearSwapper.isSwapping) {

                    boolean thresholdMet = (aliveCount >= SwapConfig.pestThreshold || aliveCount >= 8);
                    if (!thresholdMet) {
                        if (SwapConfig.autoEquipment) {
                            if (cooldownSeconds <= SwapConfig.autoEquipmentFarmingTime) {
                                cycleState = CycleState.SWAPPED_TO_PEST;
                                GearSwapper.triggerPestSwap(client);
                            }
                        } else if (cooldownSeconds <= 3) {
                            cycleState = CycleState.SWAPPED_TO_PEST;
                            GearSwapper.triggerPestSwap(client);
                        }
                    }
                }
            }
        }

        // Farming swap: pests hit threshold + we're in pest gear
        if (cycleState == CycleState.SWAPPED_TO_PEST
                && (aliveCount >= SwapConfig.pestThreshold || aliveCount >= 8)
                && aliveCount < 99
                && !GearSwapper.isSwapping) {
            cycleState = CycleState.SWAPPED_BACK;
            GearSwapper.triggerFarmingSwap(client);
        }
    }
}

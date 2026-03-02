package com.pestswap.modules;

import com.pestswap.SwapConfig;
import com.pestswap.mixin.AccessorInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GearSwapper {

    // Wardrobe state
    public static volatile boolean isSwappingWardrobe = false;
    public static volatile long wardrobeInteractionTime = 0;
    public static volatile int wardrobeInteractionStage = 0;
    public static volatile int wardrobeCleanupTicks = 0;
    public static volatile int trackedWardrobeSlot = -1;
    public static volatile int targetWardrobeSlot = -1;

    // Equipment state
    public static volatile boolean isSwappingEquipment = false;
    public static volatile int equipmentInteractionStage = 0;
    public static volatile long equipmentInteractionTime = 0;
    public static volatile boolean swappingToFarmingGear = false;
    public static volatile int equipmentTargetIndex = 0;
    public static volatile Boolean trackedIsPestGear = null;

    // Swap completion
    public static volatile boolean shouldRestartFarmingAfterSwap = false;

    // Prevents overlapping swap sequences
    public static volatile boolean isSwapping = false;

    private static long lastCommandTime = 0;
    private static final long COMMAND_COOLDOWN_MS = 250;

    public static void reset() {
        isSwappingWardrobe = false;
        isSwappingEquipment = false;
        shouldRestartFarmingAfterSwap = false;
        wardrobeCleanupTicks = 0;
        trackedWardrobeSlot = -1;
        trackedIsPestGear = null;
        isSwapping = false;
    }

    // ── Command helper (ported from ClientUtils) ──

    public static void sendCommand(Minecraft client, String cmd) {
        if (client.player == null || client.getConnection() == null)
            return;

        long now = System.currentTimeMillis();
        long diff = now - lastCommandTime;
        if (diff < COMMAND_COOLDOWN_MS) {
            try {
                Thread.sleep(COMMAND_COOLDOWN_MS - diff);
            } catch (InterruptedException ignored) {
            }
        }

        if (cmd.startsWith("/")) {
            client.getConnection().sendCommand(cmd.substring(1));
        } else {
            client.getConnection().sendChat(cmd);
        }
        lastCommandTime = System.currentTimeMillis();
    }

    // ── Wait helpers ──

    private static void waitForGearAndGui(Minecraft client) {
        try {
            while (isSwappingWardrobe)
                Thread.sleep(50);
            while (isSwappingEquipment)
                Thread.sleep(50);
            long guiStart = System.currentTimeMillis();
            while (client.screen != null && System.currentTimeMillis() - guiStart < 5000)
                Thread.sleep(100);
            Thread.sleep(400);
        } catch (InterruptedException ignored) {
        }
    }

    // ── Entry points called by TabWatcher ──

    public static void triggerPestSwap(Minecraft client) {
        if (isSwapping)
            return;
        isSwapping = true;

        client.player.displayClientMessage(
                Component.literal("\u00A7ePest cooldown detected. Swapping to pest gear..."), true);

        new Thread(() -> {
            try {
                sendCommand(client, ".ez-stopscript");
                Thread.sleep(375);

                // Equipment swap to pest gear
                if (SwapConfig.autoEquipment) {
                    ensureEquipment(client, false);
                    Thread.sleep(375);
                    long equipStart = System.currentTimeMillis();
                    while (isSwappingEquipment && System.currentTimeMillis() - equipStart < 15000)
                        Thread.sleep(50);
                    if (isSwappingEquipment) {
                        isSwappingEquipment = false;
                        client.execute(() -> { if (client.screen != null) client.player.closeContainer(); });
                    }
                    Thread.sleep(250);
                }

                // Wardrobe / Rod swap
                if (SwapConfig.gearSwapMode == SwapConfig.GearSwapMode.ROD) {
                    executeRodSequence(client);
                    finishSwapAndRestart(client);
                    isSwapping = false;
                } else if (SwapConfig.gearSwapMode == SwapConfig.GearSwapMode.WARDROBE) {
                    triggerWardrobeSwap(client, SwapConfig.wardrobeSlotPest);
                    // isSwapping cleared by handleWardrobeCompletion
                } else {
                    finishSwapAndRestart(client);
                    isSwapping = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                isSwapping = false;
            }
        }).start();
    }

    public static void triggerFarmingSwap(Minecraft client) {
        if (isSwapping)
            return;
        isSwapping = true;

        client.player.displayClientMessage(
                Component.literal("\u00A7ePest threshold met. Swapping back to farming gear..."), true);

        new Thread(() -> {
            try {
                sendCommand(client, ".ez-stopscript");
                Thread.sleep(375);

                // Equipment swap back to farming gear
                if (SwapConfig.autoEquipment) {
                    ensureEquipment(client, true);
                    Thread.sleep(375);
                    while (isSwappingEquipment)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                // Wait for equipment cleanup to finish before opening wardrobe
                while (wardrobeCleanupTicks > 0)
                    Thread.sleep(50);
                Thread.sleep(300);

                // Wardrobe swap back to farming
                if (SwapConfig.gearSwapMode == SwapConfig.GearSwapMode.WARDROBE) {
                    int targetSlot = SwapConfig.wardrobeSlotFarming;
                    if (trackedWardrobeSlot != targetSlot) {
                        ensureWardrobeSlot(client, targetSlot);
                        Thread.sleep(400);
                        long wardrobeStart = System.currentTimeMillis();
                        while (isSwappingWardrobe && System.currentTimeMillis() - wardrobeStart < 15000)
                            Thread.sleep(50);
                        if (isSwappingWardrobe) {
                            isSwappingWardrobe = false;
                            client.execute(() -> { if (client.screen != null) client.player.closeContainer(); });
                        }
                        while (wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    }
                } else if (SwapConfig.gearSwapMode == SwapConfig.GearSwapMode.ROD) {
                    executeRodSequence(client);
                }

                finishSwapAndRestart(client);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isSwapping = false;
            }
        }).start();
    }

    private static void finishSwapAndRestart(Minecraft client) {
        waitForGearAndGui(client);
        client.execute(() -> swapToFarmingTool(client));
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
        }
        sendCommand(client, SwapConfig.restartScript);
    }

    // ── Wardrobe swap ──

    private static void triggerWardrobeSwap(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot) {
            // Already at target slot, just restart
            finishSwapAndRestart(client);
            isSwapping = false;
            return;
        }

        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;

        try {
            Thread.sleep(375);
        } catch (InterruptedException ignored) {
        }
        sendCommand(client, "/wardrobe");
    }

    private static void ensureWardrobeSlot(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot)
            return;
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        sendCommand(client, "/wardrobe");
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        long now = System.currentTimeMillis();
        if (now - wardrobeInteractionTime < SwapConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Wardrobe"))
            return;

        if (wardrobeInteractionStage == 0) {
            int slotIdx = 35 + targetWardrobeSlot;
            if (slotIdx >= screen.getMenu().slots.size())
                return;

            Slot slot = screen.getMenu().slots.get(slotIdx);
            ItemStack stack = slot.getItem();

            if (stack.isEmpty() || stack.getItem().toString().toLowerCase().contains("air")
                    || stack.getItem().toString().toLowerCase().contains("gray_dye")
                    || stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
                return;
            }

            String itemName = stack.getItem().toString().toLowerCase();
            String hoverName = stack.getHoverName().getString().toLowerCase();

            if (itemName.contains("green_dye") || hoverName.contains("green dye")
                    || itemName.contains("lime_dye") || hoverName.contains("lime dye")) {
                client.player.displayClientMessage(
                        Component.literal("\u00A7aWardrobe Slot " + targetWardrobeSlot + " is already active."), true);
                trackedWardrobeSlot = targetWardrobeSlot;
                isSwappingWardrobe = false;
                client.player.closeContainer();
                handleWardrobeCompletion(client);
                return;
            }

            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                    ClickType.PICKUP, client.player);
            wardrobeInteractionTime = now;
            wardrobeInteractionStage = 1;

        } else if (wardrobeInteractionStage == 1) {
            if (now - wardrobeInteractionTime < 150)
                return;

            trackedWardrobeSlot = targetWardrobeSlot;
            isSwappingWardrobe = false;
            client.player.closeContainer();
            handleWardrobeCompletion(client);
        }
    }

    private static void handleWardrobeCompletion(Minecraft client) {
        if (!shouldRestartFarmingAfterSwap)
            return;
        shouldRestartFarmingAfterSwap = false;

        client.player.displayClientMessage(
                Component.literal("\u00A7aWardrobe swap finished. Restarting farming..."), true);

        new Thread(() -> {
            try {
                waitForGearAndGui(client);
                client.execute(() -> swapToFarmingTool(client));
                Thread.sleep(250);
                sendCommand(client, SwapConfig.restartScript);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isSwapping = false;
            }
        }).start();
    }

    // ── Equipment swap ──

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        swappingToFarmingGear = toFarming;
        isSwappingEquipment = true;
        equipmentInteractionTime = 0;
        equipmentInteractionStage = 0;
        equipmentTargetIndex = 0;
        sendCommand(client, "/equipment");
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingEquipment)
            return;

        long now = System.currentTimeMillis();
        if (now - equipmentInteractionTime < SwapConfig.equipmentSwapDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Equipment"))
            return;

        int[] guiSlots = { 10, 19, 28, 37 };
        String[] keywords = { "necklace", "cloak|vest|cape", "belt", "gloves|bracelet|gauntlet" };

        if (equipmentTargetIndex >= guiSlots.length) {
            trackedIsPestGear = !swappingToFarmingGear;
            isSwappingEquipment = false;
            int containerId = screen.getMenu().containerId;
            client.setScreen(null);
            wardrobeCleanupTicks = 10;
            equipmentInteractionStage = 0;
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    client.execute(() -> {
                        if (client.player != null && client.getConnection() != null)
                            client.getConnection().send(new ServerboundContainerClosePacket(containerId));
                    });
                } catch (InterruptedException ignored) {
                }
            }).start();
            return;
        }

        int totalSlots = screen.getMenu().slots.size();
        int playerInvStart = totalSlots - 36;
        ItemStack carried = client.player.containerMenu.getCarried();

        if (equipmentInteractionStage == 0) {
            if (!carried.isEmpty())
                return;

            Slot equipmentSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
            if (equipmentSlot != null && equipmentSlot.hasItem()) {
                String itemName = equipmentSlot.getItem().getHoverName().getString().toLowerCase();
                boolean isFarming = itemName.contains("lotus") || itemName.contains("blossom")
                        || itemName.contains("zorro");
                boolean isPest = itemName.contains("pest");
                boolean matches = swappingToFarmingGear ? isFarming : isPest;

                if (matches) {
                    equipmentTargetIndex++;
                    equipmentInteractionTime = now;
                    return;
                }
            }

            String targetTypePattern = keywords[equipmentTargetIndex];
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom")
                            || invItemName.contains("zorro");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget) {
                        boolean typeMatch = false;
                        for (String type : targetTypePattern.split("\\|")) {
                            if (invItemName.contains(type)) {
                                typeMatch = true;
                                break;
                            }
                        }

                        if (typeMatch) {
                            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,
                                    invSlot.index, 0, ClickType.PICKUP, client.player);
                            equipmentInteractionTime = now;
                            equipmentInteractionStage = 1;
                            return;
                        }
                    }
                }
            }

            equipmentTargetIndex++;
            equipmentInteractionTime = now;

        } else if (equipmentInteractionStage == 1) {
            if (carried.isEmpty()) {
                equipmentInteractionStage = 0;
                return;
            }
            int gearSlotIdx = guiSlots[equipmentTargetIndex];
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, gearSlotIdx, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            equipmentInteractionStage = 0;
            equipmentTargetIndex++;
        }
    }

    // ── Farming tool swap ──

    public static void swapToFarmingTool(Minecraft client) {
        if (client.player == null)
            return;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter" };
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            String name = stack.getHoverName().getString().toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    ((AccessorInventory) client.player.getInventory()).setSelected(i);
                    final String toolName = name;
                    client.execute(() -> client.player.displayClientMessage(
                            Component.literal("\u00A7aEquipped Farming Tool: " + toolName), true));
                    return;
                }
            }
        }
    }

    // ── Rod swap ──

    public static void executeRodSequence(Minecraft client) {
        client.execute(() -> client.player.displayClientMessage(
                Component.literal("\u00A7eExecuting Rod Swap sequence..."), true));
        for (int i = 0; i < 9; i++) {
            String rodItemName = client.player.getInventory().getItem(i).getHoverName().getString().toLowerCase();
            if (rodItemName.contains("rod")) {
                ((AccessorInventory) client.player.getInventory()).setSelected(i);
                break;
            }
        }
        try {
            Thread.sleep(500);
            client.execute(
                    () -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
            Thread.sleep(375);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // ── Cleanup tick (called every tick from PestSwapClient) ──

    public static void cleanupTick(Minecraft client) {
        if (wardrobeCleanupTicks > 0) {
            wardrobeCleanupTicks--;
            if (client.player != null) {
                try {
                    if (client.player.containerMenu != null) {
                        client.player.containerMenu.setCarried(ItemStack.EMPTY);
                        client.player.containerMenu.broadcastChanges();
                    }
                    if (client.player.inventoryMenu != null) {
                        client.player.inventoryMenu.setCarried(ItemStack.EMPTY);
                        client.player.inventoryMenu.broadcastChanges();
                    }
                    client.player.connection.send(new ServerboundContainerClosePacket(0));
                } catch (Exception ignored) {
                }
            }
            if (client.mouseHandler != null) {
                client.mouseHandler.releaseMouse();
            }
        }
    }
}

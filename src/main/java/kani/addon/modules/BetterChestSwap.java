package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.utils.JumpSpoofer;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.PlayerInput;

public class BetterChestSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> legit = sgGeneral.add(new BoolSetting.Builder()
        .name("legit")
        .description("Visibly switches to the item slot before swapping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDeploy = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-deploy-elytra")
        .description("Automatically taps jump after equipping an elytra.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-movement")
        .description("Temporarily zeroes your movement inputs while swapping to avoid multi-actions.")
        .defaultValue(true)
        .build()
    );

    private enum Phase { IDLE, WAITING }
    private Phase phase = Phase.IDLE;

    private boolean equippingElytra;
    private int targetSlot;
    private int waitTicks;
    private String message;
    private PlayerInput savedPlayerInput;
    private boolean movementPaused;

    public BetterChestSwap() {
        super(KaniAddon.CATEGORY, "better-chest-swap", "Swaps between an elytra and a chestplate using input spoofing. (CURRENTLY BROKEN AHHHHHHHH)");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        if (!prepareTargets()) {
            warning("No suitable armor found in hotbar.");
            toggle();
            return;
        }

        if (pauseMovement.get()) pauseMovement();
        mc.player.closeHandledScreen();
        waitTicks = 0;
        phase = Phase.WAITING;
    }

    private boolean prepareTargets() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        equippingElytra = !isElytra(chest);
        targetSlot = equippingElytra ? findElytraSlot() : findBestChestplateSlot();
        if (targetSlot == -1) return false;
        message = equippingElytra ? "Equipped elytra." : "Equipped chestplate.";
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (phase != Phase.WAITING) return;
        if (mc.player == null) {
            resumeMovement();
            phase = Phase.IDLE;
            toggle();
            return;
        }

        waitTicks++;
        if (waitTicks == 1) mc.setScreen(new InventoryScreen(mc.player));
        else if (waitTicks == 2) InvUtils.move().from(targetSlot).toArmor(EquipmentSlot.CHEST.getEntitySlotId());
        else if (waitTicks >= 3) {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
            finishSwap();
        }

        if (pauseMovement.get()) pauseMovement();
    }

    private void finishSwap() {
        resumeMovement();
        info(message);
        if (equippingElytra && autoDeploy.get()) JumpSpoofer.requestHold(2);

        phase = Phase.IDLE;
        toggle();
    }

    private void pauseMovement() {
        if (movementPaused) {
            applyZeroMovement();
            return;
        }

        movementPaused = true;
        savedPlayerInput = mc.player.input.playerInput;
        applyZeroMovement();
    }

    private void resumeMovement() {
        if (!movementPaused) return;
        movementPaused = false;
        if (savedPlayerInput != null) mc.player.input.playerInput = savedPlayerInput;
        savedPlayerInput = null;
    }

    private void applyZeroMovement() {
        mc.player.input.playerInput = PlayerInput.DEFAULT;
    }

    private int findBestChestplateSlot() {
        int bestSlot = -1;
        int bestScore = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isChestplate(stack)) continue;

            int score = chestplateScore(stack.getItem());
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private int findElytraSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isElytra(mc.player.getInventory().getStack(slot))) return slot;
        }
        return -1;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.contains(DataComponentTypes.GLIDER)) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId();
    }

    private boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(DataComponentTypes.GLIDER);
    }

    private int chestplateScore(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 6;
        if (item == Items.DIAMOND_CHESTPLATE) return 5;
        if (item == Items.IRON_CHESTPLATE) return 4;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }
}

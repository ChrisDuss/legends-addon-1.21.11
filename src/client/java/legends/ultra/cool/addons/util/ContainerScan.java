package legends.ultra.cool.addons.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Pair;
import net.minecraft.util.collection.DefaultedList;

import java.util.Objects;
import java.util.function.Predicate;

public final class ContainerScan {
    public static boolean containerHasItem(Item target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return false;

        DefaultedList<Slot> slots = screen.getScreenHandler().slots;
        int containerSlotCount = getContainerSlotCount(slots);
        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isOf(target)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containerHasName(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return false;

        DefaultedList<Slot> slots = screen.getScreenHandler().slots;
        int containerSlotCount = getContainerSlotCount(slots);
        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && Objects.equals(stack.getName().toString().toLowerCase().replace(" ", "_"), "literal{"+name.toLowerCase()+"}")) {
                return true;
            }
        }
        return false;
    }

    public static boolean containerHasNameAndLore(String name, String lore) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            return false;
        }

        DefaultedList<Slot> slots = screen.getScreenHandler().slots;
        int containerSlotCount = getContainerSlotCount(slots);
        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            var loreComponent = stack.get(DataComponentTypes.LORE);
            if (!stack.isEmpty() && stack.getName().getString().toLowerCase().replace(" ", "_").equals(name.toLowerCase().replace(" ", "_")) &&
                    loreComponent != null && loreComponent.toString().contains(lore)) {
                return true;
            }
        }
        return false;
    }

    public static Pair<Integer, ItemStack> findFirst(Item target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return null;

        int i = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isOf(target)) {
                return new Pair<>(i, stack.copy());
            }
            i++;
        }
        return null;
    }

    public static boolean containerHasMatching(Predicate<ItemStack> matcher) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return false;

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && matcher.test(stack)) return true;
        }
        return false;
    }

    private static int getContainerSlotCount(DefaultedList<Slot> slots) {
        return Math.max(0, slots.size() - 36);
    }

}

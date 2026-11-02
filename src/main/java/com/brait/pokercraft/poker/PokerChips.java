package com.brait.pokercraft.poker;

import com.brait.pokercraft.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class PokerChips {
    private PokerChips() {}
    public static int value(ItemStack stack, PokerConfig config) {
        if (stack.isOf(ModItems.WHITE_CHIP)) return config.white();
        if (stack.isOf(ModItems.RED_CHIP)) return config.red();
        if (stack.isOf(ModItems.BLUE_CHIP)) return config.blue();
        if (stack.isOf(ModItems.GREEN_CHIP)) return config.green();
        return 0;
    }
    public static long inventoryValue(PlayerEntity player, PokerConfig config) {
        long total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            total += (long) value(stack, config) * stack.getCount();
        }
        return total;
    }
    public static void removeInventoryChips(PlayerEntity player, PokerConfig config) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (value(player.getInventory().getStack(slot), config) > 0) player.getInventory().setStack(slot, ItemStack.EMPTY);
        }
        player.getInventory().markDirty();
    }
    public static List<ItemStack> payout(long amount, PokerConfig config) {
        if (amount < 0 || amount % config.smallestChip() != 0) throw new IllegalArgumentException("Cash balance cannot be represented by chips.");
        List<Item> items = new ArrayList<>(List.of(ModItems.WHITE_CHIP, ModItems.RED_CHIP, ModItems.BLUE_CHIP, ModItems.GREEN_CHIP));
        items.sort(java.util.Comparator.comparingInt((Item item) -> value(new ItemStack(item), config)).reversed());
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : items) {
            int denomination = value(new ItemStack(item), config);
            long count = amount / denomination; amount %= denomination;
            for (; count > 0; count -= Math.min(64, count)) stacks.add(new ItemStack(item, (int) Math.min(64, count)));
        }
        return stacks;
    }

    public static long buyInAmount(PlayerEntity player, PokerConfig config, long stack) {
        return Math.clamp(config.maxBuyIn() - stack, 0, inventoryValue(player, config));
    }

    /** Remove only enough chips to cover a deposit. The caller delivers any exact change. */
    public static List<ItemStack> withdraw(PlayerEntity player, long amount, PokerConfig config) {
        if (amount < 0 || amount % config.smallestChip() != 0 || inventoryValue(player, config) < amount)
            throw new IllegalArgumentException("Insufficient chips for this deposit.");
        long remaining = amount;
        for (int slot = 0; slot < player.getInventory().size() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            int denomination = value(stack, config);
            if (denomination == 0) continue;
            int count = (int) Math.min(stack.getCount(), (remaining + denomination - 1) / denomination);
            stack.decrement(count); remaining -= (long) count * denomination;
        }
        player.getInventory().markDirty();
        return payout(-remaining, config);
    }
}

package net.minestom.arena.game.mob;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.arena.Items;
import net.minestom.arena.Messenger;
import net.minestom.arena.utils.ItemUtils;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.TransactionOption;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

final class MobShopInventory extends Inventory {
    private static final List<ItemStack> WEAPONS = Stream.of(
                    ItemStack.builder(Material.WOODEN_SWORD),
                    ItemStack.builder(Material.STONE_SWORD),
                    ItemStack.builder(Material.IRON_SWORD),
                    ItemStack.builder(Material.GOLDEN_SWORD),
                    ItemStack.builder(Material.DIAMOND_SWORD),
                    ItemStack.builder(Material.NETHERITE_SWORD))
            .map(builder -> builder.meta(ItemUtils::hideFlags)
                    .set(MobArena.WEAPON_TAG, true).build()).toList();

    private static final List<ItemStack> ARMOR = Stream.of(
                    ItemStack.builder(Material.LEATHER_CHESTPLATE),
                    ItemStack.builder(Material.CHAINMAIL_CHESTPLATE),
                    ItemStack.builder(Material.IRON_CHESTPLATE),
                    ItemStack.builder(Material.GOLDEN_CHESTPLATE),
                    ItemStack.builder(Material.DIAMOND_CHESTPLATE),
                    ItemStack.builder(Material.NETHERITE_CHESTPLATE))
            .map(builder -> builder.meta(ItemUtils::hideFlags).build()).toList();

    private static final ItemStack HEADER = ItemUtils.stripItalics(ItemStack.builder(Material.ANVIL)
            .displayName(Component.text("Shop", NamedTextColor.GOLD))
            .lore(Component.text("Select upgrades you want to buy", NamedTextColor.GRAY))
            .build());

    private final Player player;
    private final MobArena arena;

    public MobShopInventory(Player player, MobArena arena) {
        super(InventoryType.CHEST_4_ROW, Component.text("Shop"));
        this.player = player;
        this.arena = arena;

        final int currentWeaponTier = arena.currentWeaponTier(player);
        final int currentArmorTier = arena.currentArmorTier(player);
        final ItemStack nextWeapon = nextWeapon(currentWeaponTier);
        final ItemStack nextArmor = nextArmor(currentArmorTier);

        setItemStack(4, HEADER);

        if (nextWeapon == null) setItemStack(12, ItemUtils.stripItalics(ItemStack.builder(Material.BARRIER)
                .displayName(Component.text("Maximum Weapon Tier", NamedTextColor.RED))
                .lore(Component.text("Your weapon is at the maximum tier", NamedTextColor.GRAY))
                .build()));
        else setItemStack(12, ItemUtils.stripItalics(nextWeapon
                .withDisplayName(Component.text("Upgrade Weapon", NamedTextColor.RED))
                .withLore(List.of(
                        Component.text("Upgrade your weapon to ", NamedTextColor.GRAY).append(Component.translatable(nextWeapon.material().registry().translationKey(), NamedTextColor.GRAY)),
                        Component.empty(),
                        Component.text("Costs " + (4 + currentWeaponTier) + " coins", NamedTextColor.GOLD)
                ))));
        if (nextArmor == null) setItemStack(13, ItemUtils.stripItalics(ItemStack.builder(Material.BARRIER)
                .displayName(Component.text("Maximum Armor Tier", NamedTextColor.BLUE))
                .lore(Component.text("Your chestplate is at the maximum tier", NamedTextColor.GRAY))
                .build()));
        else setItemStack(13, ItemUtils.stripItalics(nextArmor
                .withDisplayName(Component.text("Upgrade Armor", NamedTextColor.BLUE))
                .withLore(List.of(
                        Component.text("Upgrade your chestplate to ", NamedTextColor.GRAY).append(Component.translatable(nextArmor.material().registry().translationKey(), NamedTextColor.GRAY)),
                        Component.empty(),
                        Component.text("Costs " + (4 + currentArmorTier) + " coins", NamedTextColor.GOLD)
                ))));
        setItemStack(14, ItemUtils.stripItalics(ItemStack.builder(Material.POTION)
                .displayName(Component.text("Restore Health", NamedTextColor.GREEN))
                .lore(
                        Component.text("Restore 2 health points", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Costs 3 coins", NamedTextColor.GOLD)
                )
                .build()));
        setItemStack(31, Items.CONTINUE);

        addInventoryCondition((p, s, c, result) -> result.setCancel(true));
        addInventoryCondition((p, slot, c, r) -> {
            if (getItemStack(slot).material() == Material.BARRIER) return;

            switch (slot) {
                case 12 -> buyWeapon(currentWeaponTier + 1, currentWeaponTier == -1 ?
                        null : WEAPONS.get(currentWeaponTier), nextWeapon);
                case 13 -> buyArmor(currentArmorTier + 1, nextArmor);
                case 14 -> buyHealth(4);
                case 31 -> {
                    player.closeInventory();
                    arena.continueToNextStage(player);
                }
            }
        });
    }

    private ItemStack nextWeapon(int currentWeaponTier) {
        int nextWeapon = currentWeaponTier + 1;
        return nextWeapon >= WEAPONS.size() ? null : WEAPONS.get(nextWeapon);
    }

    private ItemStack nextArmor(int currentArmorTier) {
        int nextArmor = currentArmorTier + 1;
        return nextArmor >= ARMOR.size() ? null : ARMOR.get(nextArmor);
    }

    private void buyWeapon(int tier, @Nullable ItemStack take, @NotNull ItemStack item) {
        buy(3 + tier, () -> {
            if (take != null) player.getInventory().takeItemStack(take, TransactionOption.ALL);
            player.getInventory().addItemStack(item);
            arena.setWeaponTier(player, tier);
        });
    }

    private void buyArmor(int tier, @NotNull ItemStack item) {
        buy(3 + tier, () -> {
            player.setChestplate(item);
            arena.setArmorTier(player, tier);
        });
    }

    private void buyHealth(float amount) {
        buy(3, () -> player.setHealth(player.getHealth() + amount));
    }

    private void buy(int cost, @NotNull Runnable execute) {
        if (player.getInventory().takeItemStack(Items.COIN.withAmount(cost), TransactionOption.ALL_OR_NOTHING)) {
            execute.run();
            player.openInventory(new MobShopInventory(player, arena));
            player.playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_YES, Sound.Source.NEUTRAL, 1, 1), Sound.Emitter.self());
        } else {
            Messenger.warn(player, "You don't have enough coins");
            player.playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_NO, Sound.Source.NEUTRAL, 1, 1), Sound.Emitter.self());
        }
    }
}

package com.yuchayuan.buildsharer.read;

import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Powder;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.CraftedGearItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.stats.type.DamageType;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import com.yuchayuan.buildsharer.encode.Build;
import com.yuchayuan.buildsharer.share.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GearReader {

    record Result(List<Snapshot.Slot> equipment, ClassType weaponClass) {}

    private GearReader() {}

    static Result read(ClassType playerClass) {
        List<Snapshot.Slot> slots = new ArrayList<>(Collections.nCopies(Build.SLOTS, new Snapshot.Nothing()));
        int rings = 0;
        for (ItemStack stack : Models.Inventory.getEquippedItems()) {
            Optional<WynnItem> item = Models.Item.getWynnItem(stack);
            GearType type = item.map(GearReader::typeOf).orElse(null);
            if (type == null || type.isWeapon()) {
                continue;
            }
            int slot = switch (type) {
                case HELMET -> 0;
                case CHESTPLATE -> 1;
                case LEGGINGS -> 2;
                case BOOTS -> 3;
                case RING -> rings++ == 0 ? 4 : 5;
                case BRACELET -> 6;
                case NECKLACE -> 7;
                default -> -1;
            };
            if (slot >= 0 && slot < Build.SLOTS && slots.get(slot) instanceof Snapshot.Nothing) {
                slots.set(slot, slot(item.get()));
            }
        }
        ClassType weaponClass = null;
        Optional<WynnItem> weapon = weapon(playerClass);
        if (weapon.isPresent()) {
            weaponClass = typeOf(weapon.get()).getClassReq();
            slots.set(Build.WEAPON, slot(weapon.get()));
        }
        return new Result(slots, weaponClass);
    }

    private static Optional<WynnItem> weapon(ClassType playerClass) {
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        Optional<WynnItem> held = weaponIn(inventory.getSelectedItem(), playerClass);
        if (held.isPresent()) {
            return held;
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            Optional<WynnItem> found = weaponIn(inventory.getItem(slot), playerClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<WynnItem> weaponIn(ItemStack stack, ClassType playerClass) {
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        GearType type = item.map(GearReader::typeOf).orElse(null);
        if (type == null || !type.isWeapon()) {
            return Optional.empty();
        }
        if (playerClass != null && playerClass != ClassType.NONE && type.getClassReq() != playerClass) {
            return Optional.empty();
        }
        return item;
    }

    private static GearType typeOf(WynnItem item) {
        if (item instanceof GearItem gear) {
            return gear.getGearType();
        }
        if (item instanceof CraftedGearItem crafted) {
            return crafted.getGearType();
        }
        return null;
    }

    private static Snapshot.Slot slot(WynnItem item) {
        if (item instanceof GearItem gear) {
            return new Snapshot.Gear(gear.getItemInfo().name(), elements(gear.getPowders()));
        }
        if (item instanceof CraftedGearItem crafted) {
            return crafted(crafted);
        }
        return new Snapshot.Nothing();
    }

    private static Snapshot.Crafted crafted(CraftedGearItem item) {
        int[] reqs = new int[5];
        for (Pair<Skill, Integer> requirement : item.getRequirements().skills()) {
            reqs[requirement.a().ordinal()] = requirement.b();
        }
        int low = -1;
        int high = -1;
        for (Pair<DamageType, RangedValue> damage : item.getDamages()) {
            if (damage.a() == DamageType.NEUTRAL) {
                low = damage.b().low();
                high = damage.b().high();
            }
        }
        Map<String, Integer> ids = new HashMap<>();
        for (StatActualValue stat : item.getIdentifications()) {
            ids.merge(stat.statType().getApiName(), stat.value(), Integer::sum);
        }
        CappedValue durability = item.getDurability();
        return new Snapshot.Crafted(item.getName(), item.getGearType().name(), item.getLevel(),
                item.getAttackSpeed().map(Enum::name).orElse(null),
                durability == null ? -1 : durability.max(), item.getHealth(), low, high, reqs, ids,
                elements(item.getPowders()));
    }

    private static List<Integer> elements(List<Powder> powders) {
        List<Integer> out = new ArrayList<>(powders.size());
        for (Powder powder : powders) {
            Element element = powder.getElement();
            out.add(element.ordinal());
        }
        return out;
    }
}

package com.yuchayuan.buildsharer.read;

import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GearReader {

    private static final int[] ARMOUR_SLOTS = {5, 6, 7, 8};
    private static final int[] ACCESSORY_SLOTS = {9, 10, 11, 12};
    private static final int HOTBAR_START = 36;

    record Result(List<Snapshot.Slot> equipment, Map<Integer, Integer> menuSlots, ClassType weaponClass) {

        Result withPowders(Map<Integer, List<Snapshot.Powder>> powders) {
            List<Snapshot.Slot> updated = new ArrayList<>(equipment);
            for (Map.Entry<Integer, Integer> entry : menuSlots.entrySet()) {
                List<Snapshot.Powder> found = powders.get(entry.getValue());
                if (found == null) {
                    continue;
                }
                int piece = entry.getKey();
                updated.set(piece, switch (updated.get(piece)) {
                    case Snapshot.Gear gear -> new Snapshot.Gear(gear.name(), found);
                    case Snapshot.Crafted c -> new Snapshot.Crafted(c.name(), c.type(), c.level(), c.attackSpeed(),
                            c.durability(), c.health(), c.damageLow(), c.damageHigh(), c.reqs(), c.apiIds(), found);
                    case Snapshot.Nothing nothing -> nothing;
                });
            }
            return new Result(updated, menuSlots, weaponClass);
        }
    }

    private GearReader() {}

    static Result read(ClassType playerClass) {
        List<Snapshot.Slot> slots = new ArrayList<>(Collections.nCopies(Build.SLOTS, new Snapshot.Nothing()));
        Map<Integer, Integer> menuSlots = new LinkedHashMap<>();
        int rings = 0;
        for (int menuSlot : concat(ARMOUR_SLOTS, ACCESSORY_SLOTS)) {
            Optional<WynnItem> item = Models.Item.getWynnItem(TooltipPages.stack(menuSlot));
            GearType type = item.map(GearReader::typeOf).orElse(null);
            if (type == null || type.isWeapon()) {
                continue;
            }
            int piece = switch (type) {
                case HELMET -> 0;
                case CHESTPLATE -> 1;
                case LEGGINGS -> 2;
                case BOOTS -> 3;
                case RING -> rings++ == 0 ? 4 : 5;
                case BRACELET -> 6;
                case NECKLACE -> 7;
                default -> -1;
            };
            if (piece >= 0 && piece < Build.SLOTS && slots.get(piece) instanceof Snapshot.Nothing) {
                slots.set(piece, slot(item.get()));
                menuSlots.put(piece, menuSlot);
            }
        }
        ClassType weaponClass = null;
        int weaponSlot = weaponSlot(playerClass);
        if (weaponSlot >= 0) {
            WynnItem weapon = Models.Item.getWynnItem(TooltipPages.stack(weaponSlot)).orElseThrow();
            weaponClass = typeOf(weapon).getClassReq();
            slots.set(Build.WEAPON, slot(weapon));
            menuSlots.put(Build.WEAPON, weaponSlot);
        }
        return new Result(slots, menuSlots, weaponClass);
    }

    private static int weaponSlot(ClassType playerClass) {
        int selected = HOTBAR_START + Minecraft.getInstance().player.getInventory().getSelectedSlot();
        if (isWeapon(selected, playerClass)) {
            return selected;
        }
        for (int hotbar = 0; hotbar < Inventory.getSelectionSize(); hotbar++) {
            if (isWeapon(HOTBAR_START + hotbar, playerClass)) {
                return HOTBAR_START + hotbar;
            }
        }
        return -1;
    }

    private static boolean isWeapon(int menuSlot, ClassType playerClass) {
        GearType type = Models.Item.getWynnItem(TooltipPages.stack(menuSlot)).map(GearReader::typeOf).orElse(null);
        if (type == null || !type.isWeapon()) {
            return false;
        }
        return playerClass == null || playerClass == ClassType.NONE || type.getClassReq() == playerClass;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
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
            return new Snapshot.Gear(gear.getItemInfo().name(), List.of());
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
                durability == null ? -1 : durability.max(), item.getHealth(), low, high, reqs, ids, List.of());
    }
}

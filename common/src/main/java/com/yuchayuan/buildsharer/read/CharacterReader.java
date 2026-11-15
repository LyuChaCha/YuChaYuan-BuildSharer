package com.yuchayuan.buildsharer.read;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.handlers.container.scriptedquery.QueryStep;
import com.wynntils.handlers.container.scriptedquery.ScriptedContainerQuery;
import com.wynntils.handlers.container.type.ContainerContent;
import com.wynntils.models.abilitytree.AbilityTreeModel;
import com.wynntils.models.abilitytree.type.AbilityTreeInfo;
import com.wynntils.models.abilitytree.type.AbilityTreeSkillNode;
import com.wynntils.models.character.CharacterModel;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.containers.containers.CharacterInfoContainer;
import com.wynntils.models.containers.containers.MasteryTomesContainer;
import com.wynntils.models.items.items.game.TomeItem;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.models.items.items.gui.SkillPointItem;
import com.wynntils.utils.wynn.ContainerUtils;
import com.yuchayuan.buildsharer.share.PowderText;
import com.yuchayuan.buildsharer.share.Snapshot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CharacterReader {

    private static final int[] SKILL_POINT_SLOTS = {11, 12, 13, 14, 15};
    private static final int ASPECT_SLOTS = 5;
    private static final int GAP_TICKS = 3;
    private static final int TOME_SLOT = 8;
    private static final String UNLOCKED = "✔";

    private CharacterReader() {}

    public static CompletableFuture<Snapshot> read(boolean includeTomes) {
        CompletableFuture<Snapshot> result = new CompletableFuture<>();
        int[] points = new int[5];
        List<String> abilities = new ArrayList<>();
        List<String> tomes = new ArrayList<>();
        ContainerUtils.closeBackgroundContainer();
        Managers.TickScheduler.scheduleLater(() -> ScriptedContainerQuery.builder("BuildSharer Skill Points")
                .onError(message -> result.completeExceptionally(new IllegalStateException(message)))
                .then(QueryStep.useItemInHotbar(CharacterModel.CHARACTER_INFO_SLOT)
                        .expectContainer(CharacterInfoContainer.class)
                        .processIncomingContainer(content -> skillPoints(content, points)))
                .conditionalThen(content -> includeTomes
                                && LoreUtils.getStringLore(content.items().get(TOME_SLOT)).contains(UNLOCKED),
                        QueryStep.clickOnSlot(TOME_SLOT)
                                .expectContainer(MasteryTomesContainer.class)
                                .processIncomingContainer(content -> tomes(content, tomes)))
                .execute(() -> Managers.TickScheduler.scheduleLater(
                        () -> abilities(result, points, abilities, tomes), GAP_TICKS))
                .build()
                .executeQuery(), GAP_TICKS);
        return result;
    }

    private static void skillPoints(ContainerContent content, int[] points) {
        for (int slot : SKILL_POINT_SLOTS) {
            Models.Item.asWynnItem(content.items().get(slot), SkillPointItem.class)
                    .ifPresent(item -> points[item.getSkill().ordinal()] = item.getAssignedAmount());
        }
    }

    private static void tomes(ContainerContent content, List<String> tomes) {
        for (ItemStack stack : content.items()) {
            Models.Item.asWynnItem(stack, TomeItem.class).ifPresent(tome -> tomes.add(tome.getItemInfo().name()));
        }
    }

    private static void abilities(CompletableFuture<Snapshot> result, int[] points, List<String> abilities,
                                  List<String> tomes) {
        ContainerUtils.closeBackgroundContainer();
        AbilityTreeModel.ABILITY_TREE_CONTAINER_QUERIES.getUnlockedAbilityTree(
                info -> names(info, abilities),
                status -> {},
                error -> result.completeExceptionally(new IllegalStateException(error)),
                done -> Managers.TickScheduler.scheduleLater(() -> finish(result, points, abilities, tomes), GAP_TICKS));
    }

    private static void names(AbilityTreeInfo info, List<String> abilities) {
        for (AbilityTreeSkillNode node : info.nodes()) {
            abilities.add(node.name());
        }
    }

    private static void finish(CompletableFuture<Snapshot> result, int[] points, List<String> abilities,
                               List<String> tomes) {
        if (result.isDone()) {
            return;
        }
        try {
            ContainerUtils.closeBackgroundContainer();
            ClassType playerClass = Models.Character.getClassType();
            GearReader.Result gear = GearReader.read(playerClass);
            ClassType resolved = playerClass == null || playerClass == ClassType.NONE ? gear.weaponClass() : playerClass;
            if (resolved == null || resolved == ClassType.NONE) {
                throw new IllegalStateException("class");
            }
            List<Snapshot.NamedAspect> aspects = new ArrayList<>();
            for (int i = 0; i < ASPECT_SLOTS; i++) {
                Optional<String> name = Models.Aspect.getEquippedAspect(i);
                name.ifPresent(n -> aspects.add(new Snapshot.NamedAspect(n,
                        Models.Aspect.getAspectTierByName(n).orElse(1))));
            }
            int level = Models.CombatXp.getCombatLevel().current();
            String className = resolved.getName();
            TooltipPages.all(new ArrayList<>(gear.menuSlots().values())).whenComplete((pages, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                Map<Integer, List<Snapshot.Powder>> powders = new HashMap<>();
                for (Map.Entry<Integer, List<List<String>>> entry : pages.entrySet()) {
                    powders.put(entry.getKey(), powdersOf(entry.getValue()));
                }
                result.complete(new Snapshot(gear.withPowders(powders).equipment(), points, level, className,
                        aspects, abilities, tomes));
            });
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }

    private static List<Snapshot.Powder> powdersOf(List<List<String>> pages) {
        for (List<String> page : pages) {
            List<Snapshot.Powder> found = PowderText.parse(page);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }
}

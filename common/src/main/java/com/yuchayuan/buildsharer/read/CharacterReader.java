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
import com.wynntils.models.items.items.gui.SkillPointItem;
import com.wynntils.utils.wynn.ContainerUtils;
import com.yuchayuan.buildsharer.share.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CharacterReader {

    private static final int[] SKILL_POINT_SLOTS = {11, 12, 13, 14, 15};
    private static final int ASPECT_SLOTS = 5;
    private static final int GAP_TICKS = 3;

    private CharacterReader() {}

    public static CompletableFuture<Snapshot> read() {
        CompletableFuture<Snapshot> result = new CompletableFuture<>();
        int[] points = new int[5];
        List<String> abilities = new ArrayList<>();
        ContainerUtils.closeBackgroundContainer();
        Managers.TickScheduler.scheduleLater(() -> ScriptedContainerQuery.builder("BuildSharer Skill Points")
                .onError(message -> result.completeExceptionally(new IllegalStateException(message)))
                .then(QueryStep.useItemInHotbar(CharacterModel.CHARACTER_INFO_SLOT)
                        .expectContainer(CharacterInfoContainer.class)
                        .processIncomingContainer(content -> skillPoints(content, points)))
                .execute(() -> Managers.TickScheduler.scheduleLater(
                        () -> abilities(result, points, abilities), GAP_TICKS))
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

    private static void abilities(CompletableFuture<Snapshot> result, int[] points, List<String> abilities) {
        ContainerUtils.closeBackgroundContainer();
        AbilityTreeModel.ABILITY_TREE_CONTAINER_QUERIES.getUnlockedAbilityTree(
                info -> names(info, abilities),
                status -> {},
                error -> result.completeExceptionally(new IllegalStateException(error)),
                done -> Managers.TickScheduler.scheduleLater(() -> finish(result, points, abilities), GAP_TICKS));
    }

    private static void names(AbilityTreeInfo info, List<String> abilities) {
        for (AbilityTreeSkillNode node : info.nodes()) {
            abilities.add(node.name());
        }
    }

    private static void finish(CompletableFuture<Snapshot> result, int[] points, List<String> abilities) {
        if (result.isDone()) {
            return;
        }
        try {
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
            result.complete(new Snapshot(gear.equipment(), points, level, resolved.getName(), aspects, abilities));
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }
}

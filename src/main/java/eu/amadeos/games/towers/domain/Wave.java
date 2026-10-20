package eu.amadeos.games.towers.domain;

import java.util.List;

/** A wave of enemies: groups that come one after another, each a number of one kind at a steady gap. */
public record Wave(List<Group> groups) {
    public record Group(UnitType unit, int count, double gap) {
    }

    public Wave {
        groups = List.copyOf(groups);
    }

    public int count() {
        return groups.stream().mapToInt(Group::count).sum();
    }
}

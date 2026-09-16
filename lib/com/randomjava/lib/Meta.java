package com.randomjava.lib;

/**
 * Static description of a project. Generated into {@code Catalog} and also
 * returned by each project so the terminal and the browser show the same text.
 *
 * @param id          1-164, matching the catalogue in {@code tools/catalog.py}
 * @param slug        kebab-case folder name, e.g. {@code simple-calculator}
 * @param name        human readable title
 * @param category    the group it belongs to, e.g. {@code Beginner Friendly}
 * @param kind        scaffold shape
 * @param difficulty  roughly how much work the full brief is
 * @param description one line summary shown in menus and cards
 * @param stack       the stack the original brief recommended, or blank when it
 *                    named none. The suite itself is always plain JDK; this is
 *                    a note about how you would build it for real, not a
 *                    description of what is in the repo.
 * @param done        true once the logic is really implemented, false for a stub
 */
public record Meta(
        int id,
        String slug,
        String name,
        String category,
        Kind kind,
        Difficulty difficulty,
        String description,
        String stack,
        boolean done) {

    /** The on-disk folder name, e.g. {@code 001-simple-calculator}. */
    public String folder() {
        return String.format("%03d-%s", id, slug);
    }

    /** True when the original brief named a stack worth recording. */
    public boolean hasStack() {
        return stack != null && !stack.isBlank();
    }
}

package ch.haddo.wands;

/** How the wand spreads over the clicked surface. */
enum WandMode {
    /** Every connected block of the same type as the clicked one. */
    SURFACE_SAME("Superficie (stesso blocco)"),
    /** Every connected solid block, whatever its type. */
    SURFACE_ANY("Superficie (qualsiasi blocco)"),
    /** A straight line along the direction you are looking at. */
    LINE("Linea"),
    /** Only the clicked block, like placing it by hand but copying its rotation. */
    SINGLE("Singolo");

    final String label;

    WandMode(String label) {
        this.label = label;
    }

    WandMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    static WandMode parse(String name) {
        try {
            return name == null ? SURFACE_SAME : valueOf(name);
        } catch (IllegalArgumentException e) {
            return SURFACE_SAME;
        }
    }
}

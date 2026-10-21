package ch.haddo.effortless;

/** How a shape is filled. EDGES only matters for cubes; other shapes treat it like HOLLOW. */
enum Fill {
    FULL("Pieno"),
    HOLLOW("Vuoto"),
    EDGES("Solo spigoli");

    final String label;

    Fill(String label) {
        this.label = label;
    }

    Fill next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

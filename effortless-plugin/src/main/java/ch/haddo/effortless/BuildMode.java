package ch.haddo.effortless;

import org.bukkit.Material;

/** Shapes, with how many clicked points each one needs. */
enum BuildMode {
    NORMAL("Normale", 1, Material.GRASS_BLOCK, "Piazzi i blocchi come sempre (con i modificatori attivi)"),
    LINE("Linea", 2, Material.END_ROD, "Inizio e fine lungo un asse"),
    WALL("Muro", 2, Material.STONE_BRICK_WALL, "Due angoli opposti di un muro verticale"),
    FLOOR("Pavimento", 2, Material.OAK_PRESSURE_PLATE, "Due angoli opposti sul piano orizzontale"),
    DIAGONAL_LINE("Linea diagonale", 2, Material.STICK, "Inizio e fine liberi, anche in diagonale"),
    CUBE("Cubo", 3, Material.CHEST, "Due angoli della base, poi l'altezza"),
    CIRCLE("Cerchio", 2, Material.HEAVY_CORE, "Centro, poi il raggio"),
    CYLINDER("Cilindro", 3, Material.COPPER_BLOCK, "Centro, raggio, poi l'altezza"),
    SPHERE("Sfera", 2, Material.SLIME_BALL, "Centro, poi il raggio");

    final String label;
    final int points;
    final Material icon;
    final String help;

    BuildMode(String label, int points, Material icon, String help) {
        this.label = label;
        this.points = points;
        this.icon = icon;
        this.help = help;
    }
}

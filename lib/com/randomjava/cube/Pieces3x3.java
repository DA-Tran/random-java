package com.randomjava.cube;

/**
 * Where every corner and edge of a 3x3 lives, as facelet indices.
 *
 * <p>Positions are named for the faces they touch, in a fixed order, and the
 * facelet list follows that same order. So {@code CORNERS[0]} is URF and its
 * three entries are the sticker on U, then R, then F.
 *
 * <p>Because a solved cube is labelled with face letters, a piece's home colours
 * are just the letters of its position name. That makes "is this piece solved"
 * a direct comparison and removes any need for a separate colour scheme.
 */
public final class Pieces3x3 {

    private Pieces3x3() {
    }

    public static final String[] CORNER_NAMES = {
            "URF", "UFL", "ULB", "UBR", "DFR", "DLF", "DBL", "DRB"};

    public static final String[] EDGE_NAMES = {
            "UR", "UF", "UL", "UB", "DR", "DF", "DL", "DB", "FR", "FL", "BL", "BR"};

    /** Facelet indices for each corner, in the order of its name. */
    public static final int[][] CORNERS = {
            {8, 9, 20},    // URF
            {6, 18, 38},   // UFL
            {0, 36, 47},   // ULB
            {2, 45, 11},   // UBR
            {29, 26, 15},  // DFR
            {27, 44, 24},  // DLF
            {33, 53, 42},  // DBL
            {35, 17, 51}}; // DRB

    /** Facelet indices for each edge, in the order of its name. */
    public static final int[][] EDGES = {
            {5, 10},   // UR
            {7, 19},   // UF
            {3, 37},   // UL
            {1, 46},   // UB
            {32, 16},  // DR
            {28, 25},  // DF
            {30, 43},  // DL
            {34, 52},  // DB
            {23, 12},  // FR
            {21, 41},  // FL
            {50, 39},  // BL
            {48, 14}}; // BR

    // ------------------------------------------------------------------
    // Reading pieces
    // ------------------------------------------------------------------

    /** The three sticker colours sitting in corner position {@code p}, in U/R/F order. */
    public static String cornerAt(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        return "" + raw[CORNERS[p][0]] + raw[CORNERS[p][1]] + raw[CORNERS[p][2]];
    }

    /** The two sticker colours sitting in edge position {@code p}. */
    public static String edgeAt(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        return "" + raw[EDGES[p][0]] + raw[EDGES[p][1]];
    }

    // These four sit in the innermost loop of the solver's search, which visits
    // millions of positions. Comparing the stickers directly rather than
    // building a String for each one is what keeps a solve fast.

    public static boolean cornerSolved(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        int[] at = CORNERS[p];
        String home = CORNER_NAMES[p];
        return raw[at[0]] == home.charAt(0)
                && raw[at[1]] == home.charAt(1)
                && raw[at[2]] == home.charAt(2);
    }

    public static boolean edgeSolved(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        int[] at = EDGES[p];
        String home = EDGE_NAMES[p];
        return raw[at[0]] == home.charAt(0) && raw[at[1]] == home.charAt(1);
    }

    /** True when the piece in this position is the right one, however it is turned. */
    public static boolean cornerPlaced(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        int[] at = CORNERS[p];
        String home = CORNER_NAMES[p];
        return home.indexOf(raw[at[0]]) >= 0
                && home.indexOf(raw[at[1]]) >= 0
                && home.indexOf(raw[at[2]]) >= 0;
    }

    public static boolean edgePlaced(FaceletCube cube, int p) {
        char[] raw = cube.raw();
        int[] at = EDGES[p];
        String home = EDGE_NAMES[p];
        return home.indexOf(raw[at[0]]) >= 0 && home.indexOf(raw[at[1]]) >= 0;
    }

    /** Finds which corner position currently holds the piece with these colours. */
    public static int findCorner(FaceletCube cube, String colours) {
        char[] raw = cube.raw();
        for (int p = 0; p < 8; p++) {
            int[] at = CORNERS[p];
            if (colours.indexOf(raw[at[0]]) >= 0
                    && colours.indexOf(raw[at[1]]) >= 0
                    && colours.indexOf(raw[at[2]]) >= 0) {
                return p;
            }
        }
        return -1;
    }

    /** Finds which edge position currently holds the piece with these colours. */
    public static int findEdge(FaceletCube cube, String colours) {
        char[] raw = cube.raw();
        for (int p = 0; p < 12; p++) {
            int[] at = EDGES[p];
            if (colours.indexOf(raw[at[0]]) >= 0 && colours.indexOf(raw[at[1]]) >= 0) {
                return p;
            }
        }
        return -1;
    }

    /** Corner index by name, e.g. {@code "DFR"}. */
    public static int corner(String name) {
        for (int i = 0; i < CORNER_NAMES.length; i++) {
            if (sameLetters(CORNER_NAMES[i], name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No corner " + name);
    }

    /** Edge index by name, e.g. {@code "FR"}. */
    public static int edge(String name) {
        for (int i = 0; i < EDGE_NAMES.length; i++) {
            if (sameLetters(EDGE_NAMES[i], name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No edge " + name);
    }

    /** True when two piece names use the same set of faces, in any order. */
    public static boolean sameLetters(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (b.indexOf(a.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** True when a corner position is in the top layer. */
    public static boolean isUpperCorner(int p) {
        return p < 4;
    }

    /** True when an edge position is in the top layer. */
    public static boolean isUpperEdge(int p) {
        return p < 4;
    }

    /** True when an edge position is one of the four middle-layer slots. */
    public static boolean isMiddleEdge(int p) {
        return p >= 8;
    }

    /** How many stickers of a given face letter sit on the U face. */
    public static int orientedUpperEdges(FaceletCube cube) {
        int count = 0;
        for (int p = 0; p < 4; p++) {
            if (cube.raw()[EDGES[p][0]] == 'U') {
                count++;
            }
        }
        return count;
    }

    public static int orientedUpperCorners(FaceletCube cube) {
        int count = 0;
        for (int p = 0; p < 4; p++) {
            if (cube.raw()[CORNERS[p][0]] == 'U') {
                count++;
            }
        }
        return count;
    }
}

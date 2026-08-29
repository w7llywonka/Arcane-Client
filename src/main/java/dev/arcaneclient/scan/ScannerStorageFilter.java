package dev.arcaneclient.scan;

/** Central deny-list that keeps inventory-bearing blocks out of Chunk Finder evidence. */
public final class ScannerStorageFilter {
    private ScannerStorageFilter() {
    }

    public static boolean isStoragePath(String path) {
        if (path == null || path.isEmpty()) return false;
        return path.contains("chest")
            || path.equals("barrel")
            || path.contains("shulker")
            || path.endsWith("shelf")
            || path.equals("hopper")
            || path.equals("crafter")
            || path.equals("dispenser")
            || path.equals("dropper")
            || path.contains("furnace")
            || path.equals("smoker")
            || path.equals("brewing_stand")
            || path.equals("beehive")
            || path.equals("bee_nest")
            || path.equals("lectern")
            || path.equals("jukebox");
    }
}

package dev.arcaneclient.scan;

import dev.arcaneclient.model.TunnelSegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class TunnelDetector {
    private static final int INNER_MASK = 16380;
    private static final int MAX_SEGMENTS = 192;

    private TunnelDetector() {
    }

    public static List<TunnelSegment> detect(Volume volume, int worldMinX, int worldMinZ) {
        ArrayList<TunnelSegment> segments = new ArrayList<TunnelSegment>();
        TunnelDetector.detectTwoByOne(volume, worldMinX, worldMinZ, segments);
        TunnelDetector.detectThreeByThree(volume, worldMinX, worldMinZ, segments);
        return List.copyOf(segments);
    }

    private static void detectTwoByOne(Volume volume, int worldMinX, int worldMinZ, ArrayList<TunnelSegment> segments) {
        int maxBaseY = Math.min(48, volume.maxY() - 2);
        int[] alongZ = new int[16];
        for (int y = volume.minY() + 1; y <= maxBaseY && segments.size() < 192; ++y) {
            Arrays.fill(alongZ, 0);
            for (int z = 1; z <= 14; ++z) {
                int lower = volume.row(y, z);
                int upper = volume.row(y + 1, z);
                int open = lower & upper;
                int floorOrCeiling = volume.row(y - 1, z) | volume.row(y + 2, z);
                int sideOpenAlongX = volume.row(y, z - 1) | volume.row(y + 1, z - 1) | volume.row(y, z + 1) | volume.row(y + 1, z + 1);
                int alongX = open & ~floorOrCeiling & ~sideOpenAlongX & 0x3FFC;
                TunnelDetector.emitRunsAlongX(alongX, 4, y, z, TunnelSegment.Type.TWO_BY_ONE, worldMinX, worldMinZ, segments);
                int sideOpenAlongZ = TunnelDetector.neighbors(lower) | TunnelDetector.neighbors(upper);
                alongZ[z] = open & ~floorOrCeiling & ~sideOpenAlongZ & 0x3FFC;
            }
            TunnelDetector.emitRunsAlongZ(alongZ, 4, y, TunnelSegment.Type.TWO_BY_ONE, worldMinX, worldMinZ, segments);
        }
    }

    private static void detectThreeByThree(Volume volume, int worldMinX, int worldMinZ, ArrayList<TunnelSegment> segments) {
        int maxBaseY = Math.min(48, volume.maxY() - 3);
        int[] alongZ = new int[16];
        for (int y = volume.minY() + 1; y <= maxBaseY && segments.size() < 192; ++y) {
            Arrays.fill(alongZ, 0);
            for (int z = 2; z <= 13; ++z) {
                int openAlongX = 65535;
                for (int layer = 0; layer < 3; ++layer) {
                    openAlongX &= volume.row(y + layer, z - 1) & volume.row(y + layer, z) & volume.row(y + layer, z + 1);
                }
                int blockedAlongX = volume.row(y - 1, z - 1) | volume.row(y - 1, z) | volume.row(y - 1, z + 1) | volume.row(y + 3, z - 1) | volume.row(y + 3, z) | volume.row(y + 3, z + 1);
                for (int layer = 0; layer < 3; ++layer) {
                    blockedAlongX |= volume.row(y + layer, z - 2) | volume.row(y + layer, z + 2);
                }
                TunnelDetector.emitRunsAlongX(openAlongX & ~blockedAlongX & 0x3FFC, 3, y, z, TunnelSegment.Type.THREE_BY_THREE, worldMinX, worldMinZ, segments);
                int openAlongZ = 65535;
                int blockedAlongZ = TunnelDetector.expanded(volume.row(y - 1, z)) | TunnelDetector.expanded(volume.row(y + 3, z));
                for (int layer = 0; layer < 3; ++layer) {
                    int row = volume.row(y + layer, z);
                    openAlongZ &= row & row << 1 & row >>> 1;
                    blockedAlongZ |= TunnelDetector.distanceTwo(row);
                }
                alongZ[z] = openAlongZ & ~blockedAlongZ & 0x3FFC;
            }
            TunnelDetector.emitRunsAlongZ(alongZ, 3, y, TunnelSegment.Type.THREE_BY_THREE, worldMinX, worldMinZ, segments);
        }
    }

    private static void emitRunsAlongX(int mask, int minimumLength, int y, int z, TunnelSegment.Type type, int worldMinX, int worldMinZ, ArrayList<TunnelSegment> segments) {
        int start = -1;
        for (int x = 2; x <= 14; ++x) {
            boolean present;
            boolean bl = present = x <= 13 && (mask & 1 << x) != 0;
            if (present && start < 0) {
                start = x;
                continue;
            }
            if (present || start < 0) continue;
            int end = x - 1;
            if (end - start + 1 >= minimumLength && segments.size() < 192) {
                int halfWidth = type == TunnelSegment.Type.THREE_BY_THREE ? 1 : 0;
                int height = type == TunnelSegment.Type.THREE_BY_THREE ? 3 : 2;
                segments.add(new TunnelSegment(worldMinX + start, y, worldMinZ + z - halfWidth, worldMinX + end + 1, y + height, worldMinZ + z + halfWidth + 1, type));
            }
            start = -1;
        }
    }

    private static void emitRunsAlongZ(int[] masks, int minimumLength, int y, TunnelSegment.Type type, int worldMinX, int worldMinZ, ArrayList<TunnelSegment> segments) {
        for (int x = 2; x <= 13 && segments.size() < 192; ++x) {
            int start = -1;
            for (int z = 2; z <= 14; ++z) {
                boolean present;
                boolean bl = present = z <= 13 && (masks[z] & 1 << x) != 0;
                if (present && start < 0) {
                    start = z;
                    continue;
                }
                if (present || start < 0) continue;
                int end = z - 1;
                if (end - start + 1 >= minimumLength) {
                    int halfWidth = type == TunnelSegment.Type.THREE_BY_THREE ? 1 : 0;
                    int height = type == TunnelSegment.Type.THREE_BY_THREE ? 3 : 2;
                    segments.add(new TunnelSegment(worldMinX + x - halfWidth, y, worldMinZ + start, worldMinX + x + halfWidth + 1, y + height, worldMinZ + end + 1, type));
                }
                start = -1;
            }
        }
    }

    private static int neighbors(int row) {
        return row << 1 | row >>> 1;
    }

    private static int expanded(int row) {
        return row | TunnelDetector.neighbors(row);
    }

    private static int distanceTwo(int row) {
        return row << 2 | row >>> 2;
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Volume {
        private final int minY;
        private final int maxY;
        private final int[][] openRows;

        public Volume(int minY, int maxY) {
            if (maxY < minY) {
                throw new IllegalArgumentException("maxY must be at least minY");
            }
            this.minY = minY;
            this.maxY = maxY;
            this.openRows = new int[maxY - minY + 1][16];
        }

        public int minY() {
            return this.minY;
        }

        public int maxY() {
            return this.maxY;
        }

        public void setOpen(int x, int y, int z) {
            if (x >= 0 && x < 16 && z >= 0 && z < 16 && y >= this.minY && y <= this.maxY) {
                int[] nArray = this.openRows[y - this.minY];
                int n = z;
                nArray[n] = nArray[n] | 1 << x;
            }
        }

        public void fillSection(int sectionMinY) {
            int from = Math.max(this.minY, sectionMinY);
            int to = Math.min(this.maxY, sectionMinY + 15);
            for (int y = from; y <= to; ++y) {
                Arrays.fill(this.openRows[y - this.minY], 65535);
            }
        }

        private int row(int y, int z) {
            if (y < this.minY || y > this.maxY || z < 0 || z >= 16) {
                return 0;
            }
            return this.openRows[y - this.minY][z];
        }
    }
}

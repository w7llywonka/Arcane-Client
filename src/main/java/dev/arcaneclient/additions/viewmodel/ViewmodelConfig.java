package dev.arcaneclient.additions.viewmodel;

/** Optional first-person presentation; no entity, interaction or attack timing changes. */
public final class ViewmodelConfig {
    public boolean enabled;
    public HandConfig mainHand = new HandConfig();
    public HandConfig offHand = new HandConfig();

    public void sanitize() {
        if (mainHand == null) mainHand = new HandConfig();
        if (offHand == null) offHand = new HandConfig();
        mainHand.sanitize();
        offHand.sanitize();
    }

    public static final class HandConfig {
        /** Centimeters in camera space: right, up and toward the camera. */
        public int offsetX;
        public int offsetY;
        public int offsetZ;
        public int scale = 100;
        public int rotateX;
        public int rotateY;
        public int rotateZ;
        public int swingAmount = 100;
        public boolean hidden;

        public void sanitize() {
            offsetX = Math.clamp(offsetX, -100, 100);
            offsetY = Math.clamp(offsetY, -100, 100);
            offsetZ = Math.clamp(offsetZ, -100, 100);
            scale = Math.clamp(scale, 25, 200);
            rotateX = Math.clamp(rotateX, -180, 180);
            rotateY = Math.clamp(rotateY, -180, 180);
            rotateZ = Math.clamp(rotateZ, -180, 180);
            swingAmount = Math.clamp(swingAmount, 0, 200);
        }

        public void reset() {
            offsetX = offsetY = offsetZ = 0;
            rotateX = rotateY = rotateZ = 0;
            scale = swingAmount = 100;
            hidden = false;
        }
    }
}

package dev.arcaneclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

@Environment(value=EnvType.CLIENT)
final class TracerLines {
    private TracerLines() {
    }

    static void draw(MatrixStack.Entry pose, VertexConsumer lines, double startX, double startY, double startZ, double endX, double endY, double endZ, int color, float width) {
        double x = endX - startX;
        double y = endY - startY;
        double z = endZ - startZ;
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if (length < 0.001f) {
            return;
        }
        float nx = (float)x / length;
        float ny = (float)y / length;
        float nz = (float)z / length;
        lines.vertex(pose, (float)startX, (float)startY, (float)startZ).color(color).normal(pose, nx, ny, nz).lineWidth(width);
        lines.vertex(pose, (float)endX, (float)endY, (float)endZ).color(color).normal(pose, nx, ny, nz).lineWidth(width);
    }
}

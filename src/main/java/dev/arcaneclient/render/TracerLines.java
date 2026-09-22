package dev.arcaneclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class TracerLines {
    private TracerLines() {
    }

    static void draw(PoseStack.Pose pose, VertexConsumer lines, double startX, double startY, double startZ, double endX, double endY, double endZ, int color, float width) {
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
        lines.addVertex(pose, (float)startX, (float)startY, (float)startZ).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        lines.addVertex(pose, (float)endX, (float)endY, (float)endZ).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }
}

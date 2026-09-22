package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.screen.vector.VectorUi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vector-selected Xuong/Sora typography with scale-aware bundled fallbacks for both families. */
@Environment(EnvType.CLIENT)
public final class ArcaneFont {
    private static final int MAX_UI_SCALE = 6;
    private static final Identifier[] SCALED_FONT_IDS = new Identifier[MAX_UI_SCALE];
    private static final Identifier[] SORA_FONT_IDS = new Identifier[MAX_UI_SCALE];

    static {
        for (int scale = 1; scale <= MAX_UI_SCALE; scale++) {
            SCALED_FONT_IDS[scale - 1] = ArcaneClient.id("ui_x" + scale);
            SORA_FONT_IDS[scale - 1] = ArcaneClient.id("sora_x" + scale);
        }
    }

    private static Identifier styledId;
    private static Style cachedStyle;
    private static final int CACHE_LIMIT = 512;
    private static final Map<TextKey, Component> TEXT_CACHE = lru();
    private static final Map<WidthKey, Integer> WIDTH_CACHE = lru();
    private static final Map<TrimKey, FormattedCharSequence> TRIM_CACHE = lru();

    private ArcaneFont() {
    }

    public static Font renderer(Minecraft client) {
        return client.font;
    }

    public static Component text(String value) {
        Identifier id = fontId();
        TextKey key = new TextKey(id, value);
        return TEXT_CACHE.computeIfAbsent(key, ignored -> Component.literal(value).setStyle(style(id)));
    }

    public static int width(Font renderer, String value) {
        boolean vector = VectorUi.recording();
        WidthKey key = new WidthKey(fontId(), value, vector);
        Integer cached = WIDTH_CACHE.get(key);
        if (cached != null) return cached;
        int measured;
        if (vector) {
            float width = VectorUi.textWidth(value, UiDraw.FONT_SIZE);
            if (width >= 0) {
                measured = (int)Math.ceil(width);
                WIDTH_CACHE.put(key, measured);
                return measured;
            }
        }
        measured = renderer.width(text(value));
        WIDTH_CACHE.put(key, measured);
        return measured;
    }

    public static int width(Font renderer, FormattedCharSequence value) {
        return width(renderer, UiDraw.plain(value));
    }

    public static FormattedCharSequence trimmed(Font renderer, String value, int maxWidth) {
        if (maxWidth <= 0) return FormattedCharSequence.EMPTY;
        boolean vector = VectorUi.recording();
        TrimKey key = new TrimKey(fontId(), value, maxWidth, vector);
        FormattedCharSequence cached = TRIM_CACHE.get(key);
        if (cached != null) return cached;
        FormattedCharSequence result;
        if (width(renderer, value) <= maxWidth) {
            result = text(value).getVisualOrderText();
            TRIM_CACHE.put(key, result);
            return result;
        }
        int ellipsisWidth = width(renderer, "…");
        if (!UiTextLayout.fitsEllipsis(maxWidth, ellipsisWidth)) return FormattedCharSequence.EMPTY;
        int available = maxWidth - ellipsisWidth;
        String prefix;
        if (vector) {
            int end = value.length();
            while (end > 0 && width(renderer, value.substring(0, end)) > available) {
                end = value.offsetByCodePoints(end, -1);
            }
            prefix = value.substring(0, end);
        } else prefix = renderer.substrByWidth(text(value), available).getString();
        result = text(prefix + "…").getVisualOrderText();
        TRIM_CACHE.put(key, result);
        return result;
    }

    public static Style style() {
        Identifier id = fontId();
        return style(id);
    }

    private static Style style(Identifier id) {
        if (!id.equals(styledId) || cachedStyle == null) {
            styledId = id;
            cachedStyle = Style.EMPTY.withFont(new FontDescription.Resource(id));
        }
        return cachedStyle;
    }

    /** Re-selects the scale-matched bundled font after a window or resource change. */
    public static void invalidate() {
        cachedStyle = null;
        styledId = null;
        TEXT_CACHE.clear();
        WIDTH_CACHE.clear();
        TRIM_CACHE.clear();
    }

    private static Identifier fontId() {
        Minecraft client = Minecraft.getInstance();
        int scale = Math.clamp(client.getWindow().getGuiScale(), 1, MAX_UI_SCALE);
        return ArcaneClient.config() != null && ArcaneClient.config().uiFont == 1
            ? SORA_FONT_IDS[scale - 1] : SCALED_FONT_IDS[scale - 1];
    }

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_LIMIT;
            }
        };
    }

    private record TextKey(Identifier font, String value) { }
    private record WidthKey(Identifier font, String value, boolean vector) { }
    private record TrimKey(Identifier font, String value, int maxWidth, boolean vector) { }
}

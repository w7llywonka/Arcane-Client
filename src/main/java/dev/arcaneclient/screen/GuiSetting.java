package dev.arcaneclient.screen;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

/**
 * One row inside an expanded module. Every kind carries its own height so the window layout and the
 * click hit-testing can walk the same geometry.
 */
@Environment(EnvType.CLIENT)
public abstract sealed class GuiSetting {
    static final int COMPACT_HEIGHT = 15;
    static final int SLIDER_HEIGHT = 25;
    static final int MESSAGE_HEIGHT = 20;

    private final String label;
    private final String searchText;

    private GuiSetting(String label) {
        this(label, "");
    }

    private GuiSetting(String label, String searchTerms) {
        this.label = label;
        this.searchText = (label + " " + searchTerms).toLowerCase(Locale.ROOT);
    }

    public String label() {
        return this.label;
    }

    public String searchText() {
        return this.searchText;
    }

    public abstract int height();

    /** A compact, non-interactive label separating controls inside a grouped module. */
    @Environment(EnvType.CLIENT)
    public static final class Section extends GuiSetting {
        public Section(String label) {
            super(label);
        }

        @Override
        public int height() {
            return 12;
        }
    }

    /** A checkbox row. */
    @Environment(EnvType.CLIENT)
    public static final class Toggle extends GuiSetting {
        private final BooleanSupplier value;
        private final Consumer<Boolean> setter;
        private final boolean groupHeader;

        public Toggle(String label, BooleanSupplier value, Consumer<Boolean> setter) {
            this(label, "", value, setter, false);
        }

        /** A child module compressed into one named toggle row inside a module group. */
        public Toggle(String label, String searchTerms, BooleanSupplier value, Consumer<Boolean> setter) {
            this(label, searchTerms, value, setter, true);
        }

        private Toggle(String label, String searchTerms, BooleanSupplier value, Consumer<Boolean> setter, boolean groupHeader) {
            super(label, searchTerms);
            this.value = value;
            this.setter = setter;
            this.groupHeader = groupHeader;
        }

        public boolean value() {
            return this.value.getAsBoolean();
        }

        public void toggle() {
            this.setter.accept(!this.value.getAsBoolean());
        }

        public boolean groupHeader() {
            return this.groupHeader;
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A checkbox row that also carries a cycleable colour swatch. */
    @Environment(EnvType.CLIENT)
    public static final class ToggleSwatch extends GuiSetting {
        private final BooleanSupplier value;
        private final Consumer<Boolean> setter;
        private final IntSupplier color;
        private final IntConsumer colorSetter;

        public ToggleSwatch(
            String label,
            BooleanSupplier value,
            Consumer<Boolean> setter,
            IntSupplier color,
            IntConsumer colorSetter
        ) {
            super(label);
            this.value = value;
            this.setter = setter;
            this.color = color;
            this.colorSetter = colorSetter;
        }

        public boolean value() {
            return this.value.getAsBoolean();
        }

        public void toggle() {
            this.setter.accept(!this.value.getAsBoolean());
        }

        public int color() {
            return this.color.getAsInt();
        }

        public void cycleColor() {
            this.colorSetter.accept(ColorPalette.next(this.color.getAsInt()));
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A labelled numeric slider with its own track. */
    @Environment(EnvType.CLIENT)
    public static final class Slider extends GuiSetting {
        private final IntSupplier value;
        private final IntConsumer setter;
        private final int min;
        private final int max;
        private final String suffix;

        public Slider(String label, IntSupplier value, IntConsumer setter, int min, int max, String suffix) {
            super(label);
            this.value = value;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
        }

        public int value() {
            return Math.clamp(this.value.getAsInt(), this.min, this.max);
        }

        public String display() {
            return this.value() + this.suffix;
        }

        public float fraction() {
            return this.max == this.min ? 0.0f : (float) (this.value() - this.min) / (this.max - this.min);
        }

        public void setFraction(float fraction) {
            float clamped = Math.clamp(fraction, 0.0f, 1.0f);
            this.setter.accept(this.min + Math.round(clamped * (this.max - this.min)));
        }

        @Override
        public int height() {
            return SLIDER_HEIGHT;
        }
    }

    /** A colour swatch on its own. */
    @Environment(EnvType.CLIENT)
    public static final class Swatch extends GuiSetting {
        private final IntSupplier color;
        private final IntConsumer colorSetter;

        public Swatch(String label, IntSupplier color, IntConsumer colorSetter) {
            super(label);
            this.color = color;
            this.colorSetter = colorSetter;
        }

        public int color() {
            return this.color.getAsInt();
        }

        public void cycleColor() {
            this.colorSetter.accept(ColorPalette.next(this.color.getAsInt()));
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A value that steps through a fixed list, shown as {@code < VALUE >}. */
    @Environment(EnvType.CLIENT)
    public static final class Cycle extends GuiSetting {
        private final Supplier<String> value;
        private final Runnable next;

        public Cycle(String label, Supplier<String> value, Runnable next) {
            super(label);
            this.value = value;
            this.next = next;
        }

        public String value() {
            return this.value.get();
        }

        public void next() {
            this.next.run();
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A rebindable key. */
    @Environment(EnvType.CLIENT)
    public static final class Bind extends GuiSetting {
        private final KeyMapping mapping;

        public Bind(String label, KeyMapping mapping) {
            super(label);
            this.mapping = mapping;
        }

        public KeyMapping mapping() {
            return this.mapping;
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A read-only counter or status line. */
    @Environment(EnvType.CLIENT)
    public static final class Info extends GuiSetting {
        private final Supplier<String> value;

        public Info(String label, Supplier<String> value) {
            super(label);
            this.value = value;
        }

        public String value() {
            return this.value.get();
        }

        @Override
        public int height() {
            return COMPACT_HEIGHT;
        }
    }

    /** A chat macro: an editable message paired with the key that sends it. */
    @Environment(EnvType.CLIENT)
    public static final class Message extends GuiSetting {
        private final int slot;
        private final @Nullable KeyMapping mapping;

        public Message(String label, int slot, @Nullable KeyMapping mapping) {
            super(label);
            this.slot = slot;
            this.mapping = mapping;
        }

        public int slot() {
            return this.slot;
        }

        public @Nullable KeyMapping mapping() {
            return this.mapping;
        }

        @Override
        public int height() {
            return MESSAGE_HEIGHT;
        }
    }
}

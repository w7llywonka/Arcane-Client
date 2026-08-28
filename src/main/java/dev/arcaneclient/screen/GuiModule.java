package dev.arcaneclient.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

/**
 * A single entry in a category window. Most modules are a boolean the row toggles; a few are value
 * modules that show their current setting on the right and open on either click.
 */
@Environment(EnvType.CLIENT)
public final class GuiModule {
    private final String name;
    private final String description;
    private final String searchText;
    private final @Nullable BooleanSupplier value;
    private final @Nullable Consumer<Boolean> setter;
    private final @Nullable Supplier<String> valueLabel;
    private final List<GuiSetting> settings;

    private boolean expanded;

    private GuiModule(
        String name,
        String description,
        @Nullable BooleanSupplier value,
        @Nullable Consumer<Boolean> setter,
        @Nullable Supplier<String> valueLabel,
        List<GuiSetting> settings
    ) {
        this.name = name;
        this.description = description;
        this.value = value;
        this.setter = setter;
        this.valueLabel = valueLabel;
        this.settings = settings;

        StringBuilder search = new StringBuilder(name).append(' ').append(description);
        for (GuiSetting setting : settings) {
            search.append(' ').append(setting.label());
        }
        this.searchText = search.toString().toLowerCase(Locale.ROOT);
    }

    public static Builder toggle(String name, String description, BooleanSupplier value, Consumer<Boolean> setter) {
        return new Builder(name, description, value, setter, null);
    }

    public static Builder value(String name, String description, Supplier<String> valueLabel) {
        return new Builder(name, description, null, null, valueLabel);
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public List<GuiSetting> settings() {
        return this.settings;
    }

    public boolean hasSettings() {
        return !this.settings.isEmpty();
    }

    public boolean toggleable() {
        return this.value != null && this.setter != null;
    }

    public boolean enabled() {
        return this.value != null && this.value.getAsBoolean();
    }

    public void toggle() {
        if (this.value != null && this.setter != null) {
            this.setter.accept(!this.value.getAsBoolean());
        }
    }

    public @Nullable String valueLabel() {
        return this.valueLabel == null ? null : this.valueLabel.get();
    }

    public boolean expanded() {
        return this.expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean matches(String query) {
        return query.isEmpty() || this.searchText.contains(query);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder {
        private final String name;
        private final String description;
        private final @Nullable BooleanSupplier value;
        private final @Nullable Consumer<Boolean> setter;
        private final @Nullable Supplier<String> valueLabel;
        private final List<GuiSetting> settings = new ArrayList<>();

        private Builder(
            String name,
            String description,
            @Nullable BooleanSupplier value,
            @Nullable Consumer<Boolean> setter,
            @Nullable Supplier<String> valueLabel
        ) {
            this.name = name;
            this.description = description;
            this.value = value;
            this.setter = setter;
            this.valueLabel = valueLabel;
        }

        public Builder with(GuiSetting setting) {
            this.settings.add(setting);
            return this;
        }

        public GuiModule build() {
            return new GuiModule(this.name, this.description, this.value, this.setter, this.valueLabel, List.copyOf(this.settings));
        }
    }
}

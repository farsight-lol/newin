package lol.farsight.newin;

import org.jetbrains.annotations.NotNull;

public interface NewinManager {
    void applyToPackage(
            final @NotNull Object pluginObj,
            final @NotNull String pack
    );

    void applyToClasses(final @NotNull Class<?> @NotNull ... classes);
}

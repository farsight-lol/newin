package lol.farsight.newin.registrar;

import com.google.common.base.Preconditions;
import lol.farsight.newin.NewinManager;
import lol.farsight.newin.registrar.asm.NewinClassVisitor;
import lol.farsight.newin.registrar.transformer.BcTransformer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

public final class Newins implements NewinManager {
    public static final Newins INSTANCE = new Newins();

    private static final Logger LOGGER = LoggerFactory.getLogger(Newins.class);
    private static final Method GET_FILE_METHOD;
    static {
        try {
            GET_FILE_METHOD = JavaPlugin.class.getDeclaredMethod("getFile");
            GET_FILE_METHOD.setAccessible(true);
        } catch (final @NotNull NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private Newins() {}

    public void applyToPackage(
            final @NotNull Object pluginObj,
            final @NotNull String pack
    ) {
        Preconditions.checkNotNull(pluginObj, "plugin");
        if (!(pluginObj instanceof JavaPlugin plugin))
            throw new IllegalArgumentException("pluginObj is not JavaPlugin");

        Preconditions.checkNotNull(pack, "pack");

        final var packagePath = pack.replace('.', '/');
        try (final var jar = new JarFile((File) GET_FILE_METHOD.invoke(plugin))) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(packagePath))
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .forEach(entry -> {
                        final ClassReader reader;
                        try {
                            reader = new ClassReader(jar.getInputStream(entry));
                        } catch (final @NotNull IOException e) {
                            LOGGER.error("Couldn't register newin class, are you sure you own this package?", e);

                            return;
                        }

                        final Class<?> cl;
                        try {
                            cl = Class.forName(
                                    reader.getClassName().replace('/', '.'),
                                    true,
                                    plugin.getClass().getClassLoader()
                            );
                        } catch (final @NotNull ClassNotFoundException e) {
                            LOGGER.error("Couldn't find newin class. Weird.");

                            return;
                        }

                        apply(
                                cl,
                                reader
                        );
                    });
        } catch (final @NotNull InvocationTargetException | @NotNull IOException | @NotNull IllegalAccessException e) {
            LOGGER.error("couldn't register newin package, are you sure you own this package?", e);
        }
    }

    public void applyToClasses(final @NotNull Class<?> @NotNull ... classes) {
        Preconditions.checkNotNull(classes, "classes");

        for (int i = 0, classesLength = classes.length; i < classesLength; i++) {
            final var cl = classes[i];
            Preconditions.checkNotNull(cl, "element " + i + " of classes");

            BcTransformer.invoke(cl, buffer -> {
                apply(
                        cl,
                        new ClassReader(buffer)
                );

                return buffer;
            });
        }
    }

    private void apply(
            final @NotNull Class<?> cl,
            final @NotNull ClassReader reader
    ) {
        Preconditions.checkNotNull(reader, "reader");

        final var newinCv = new NewinClassVisitor(cl);
        reader.accept(newinCv, 0);

        newinCv.apply();
    }
}

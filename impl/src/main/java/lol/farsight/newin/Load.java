package lol.farsight.newin;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import lol.farsight.newin.agent.AgentEntrypoint;
import lol.farsight.newin.agent.InstrumentationHolder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

// earliest point a paper plugin can
// possibly run code at

@SuppressWarnings("UnstableApiUsage")
public final class Load implements PluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Load.class);

    @SuppressWarnings("removal")
    @Override
    public void classloader(final @NotNull PluginClasspathBuilder builder) {
        final Path agent;
        {
            final var dataFolder = builder.getContext().getDataDirectory()
                    .toFile();
            dataFolder.mkdirs();

            agent = dataFolder.toPath()
                    .resolve(Path.of("agent.jar"));

            final var manifest = new Manifest();
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            manifest.getMainAttributes().putValue("Agent-Class", AgentEntrypoint.class.getName());
            manifest.getMainAttributes().putValue("Can-Redefine-Classes", "true");
            manifest.getMainAttributes().putValue("Can-Retransform-Classes", "true");

            try (
                    final var jar = new JarFile(builder.getContext().getPluginSource().toFile());
                    final var jos = new JarOutputStream(
                            Files.newOutputStream(agent),
                            manifest
                    )
            ) {
                String name;
                JarEntry entry;

                {
                    name = AgentEntrypoint.class.getName().replace('.', '/') + ".class";
                    entry = jar.getJarEntry(name);

                    jos.putNextEntry(entry);
                    try (final var in = jar.getInputStream(entry)) {
                        in.transferTo(jos);
                    }

                    jos.closeEntry();
                }

                {
                    name = InstrumentationHolder.class.getName().replace('.', '/') + ".class";
                    entry = jar.getJarEntry(name);

                    jos.putNextEntry(entry);
                    try (final var in = jar.getInputStream(entry)) {
                        in.transferTo(jos);
                    }

                    jos.closeEntry();
                }
            } catch (final @NotNull IOException e) {
                LOGGER.error("couldn't generate agent JAR");

                return;
            }
        }

        final Unsafe unsafe;
        try {
            final var theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);

            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (final @NotNull NoSuchFieldException | @NotNull IllegalAccessException e) {
            LOGGER.error("couldn't get unsafe instance", e);

            return;
        }

        try {
            final var allowSelfAttach = Class.forName("sun.tools.attach.HotSpotVirtualMachine")
                    .getDeclaredField("ALLOW_ATTACH_SELF");

            // noinspection removal
            unsafe.putBooleanVolatile(
                    unsafe.staticFieldBase(allowSelfAttach),
                    unsafe.staticFieldOffset(allowSelfAttach),
                    true
            );
        } catch (final @NotNull NoSuchFieldException | @NotNull ClassNotFoundException e) {
            LOGGER.error("couldn't force ALLOW_ATTACH_SELF", e);

            return;
        }

        try {
            final String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

            final var virtualMachine = VirtualMachine.attach(pid);
            virtualMachine.loadAgent(agent.toString(), "");
            virtualMachine.detach();
        } catch (final @NotNull AttachNotSupportedException | @NotNull IOException e) {
            LOGGER.error("couldn't attach to running JVM", e);

            return;
        } catch (final @NotNull AgentLoadException | @NotNull AgentInitializationException e) {
            LOGGER.error("couldn't attach agent to self", e);

            return;
        }

        // we call get() but we dont actually use the return value
        // this is purely here to cache the instrumentation value
        InstrumentationHolder.get();
    }
}

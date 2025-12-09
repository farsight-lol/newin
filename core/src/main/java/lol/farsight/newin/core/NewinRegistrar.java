package lol.farsight.newin.core;

import com.google.common.base.Preconditions;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import lol.farsight.newin.agent.InstrumentationHolder;
import lol.farsight.newin.core.asm.NewinClassVisitor;
import lol.farsight.newin.core.transformer.BcTransformer;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NewinRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewinRegistrar.class);

    private NewinRegistrar() {
        throw new UnsupportedOperationException();
    }

    public static void acknowledge() {
        if (InstrumentationHolder.get() != null)
            // we avoid attaching the agent multiple times here.
            return;

        final Path agentJar;
        try {
            agentJar = Files.createTempFile("agent-", ".jar");
        } catch (final IOException e) {
            LOGGER.error("couldn't create temp file for agent", e);

            return;
        }

        var cl = NewinRegistrar.class.getClassLoader();
        if (cl == null)
            cl = ClassLoader.getSystemClassLoader();

        try (final var in = cl.getResourceAsStream("agent.jar")) {
            if (in == null) {
                LOGGER.error("couldn't find agent.jar in resources");

                return;
            }

            Files.copy(
                    in,
                    agentJar,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (final IOException e) {
            LOGGER.error("couldn't copy agent jar to temp file", e);

            return;
        }

        // we don't need the file after exiting
        agentJar.toFile().deleteOnExit();

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
        } catch (final NoSuchFieldException | ClassNotFoundException e) {
            LOGGER.error("couldn't force ALLOW_ATTACH_SELF", e);

            return;
        }

        try {
            final String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

            final var virtualMachine = VirtualMachine.attach(pid);
            virtualMachine.loadAgent(agentJar.toString(), "");
            virtualMachine.detach();
        } catch (final AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException e) {
            LOGGER.error("couldn't attach to running JVM", e);

            return;
        }

        // we call get() but we dont actually use the return value
        // this is purely here to cache the instrumentation value
        InstrumentationHolder.get();
    }

    public static void applyFromClasses(final @NotNull Class<?> @NotNull ... classes) {
        Preconditions.checkNotNull(classes, "classes");

        for (int i = 0, classesLength = classes.length; i < classesLength; i++) {
            final var cl = classes[i];
            Preconditions.checkNotNull(cl, "element " + i + " of classes");

            final var newinCv = new NewinClassVisitor(cl);
            new ClassReader(
                    BcTransformer.bytes(cl)
            ).accept(newinCv, 0);

            newinCv.apply();
        }
    }
}

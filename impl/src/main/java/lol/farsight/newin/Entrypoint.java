package lol.farsight.newin;

import lol.farsight.newin.registrar.Newins;
import org.bukkit.GameMode;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Entrypoint extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer()
                .getServicesManager()
                .register(
                        NewinManager.class,
                        Newins.INSTANCE,
                        this,
                        ServicePriority.Normal
                );
    }
}

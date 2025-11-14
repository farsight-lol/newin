package lol.farsight.newin;

import lol.farsight.newin.registrar.Newins;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

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

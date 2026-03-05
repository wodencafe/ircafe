@ApplicationModule(
    displayName = "Monitor Presence",
    allowedDependencies = {"app::api", "config", "irc", "model"})
package cafe.woden.ircclient.monitor;

import org.springframework.modulith.ApplicationModule;

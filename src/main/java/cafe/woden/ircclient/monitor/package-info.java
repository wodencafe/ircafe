@ApplicationModule(
    displayName = "Monitor Presence",
    allowedDependencies = {"app::api", "config", "config::api", "irc", "irc::presence", "model"})
package cafe.woden.ircclient.monitor;

import org.springframework.modulith.ApplicationModule;

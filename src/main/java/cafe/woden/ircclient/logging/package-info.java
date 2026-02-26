@ApplicationModule(
    displayName = "Logging",
    allowedDependencies = {"app::api", "config", "irc", "model", "util"})
package cafe.woden.ircclient.logging;

import org.springframework.modulith.ApplicationModule;

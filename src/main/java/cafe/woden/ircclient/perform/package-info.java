@ApplicationModule(
    displayName = "Perform Automation",
    allowedDependencies = {"app::api", "app::commands", "config", "irc", "irc::backend", "model"})
package cafe.woden.ircclient.perform;

import org.springframework.modulith.ApplicationModule;

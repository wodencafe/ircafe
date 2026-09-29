@ApplicationModule(
    displayName = "Monitor Presence",
    allowedDependencies = {
      "app::api",
      "config",
      "config::execution",
      "config::api",
      "irc",
      "irc::port",
      "irc::presence",
      "model"
    })
package cafe.woden.ircclient.monitor;

import org.springframework.modulith.ApplicationModule;

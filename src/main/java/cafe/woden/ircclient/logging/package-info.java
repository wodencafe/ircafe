@ApplicationModule(
    displayName = "Logging",
    allowedDependencies = {
      "app::api",
      "config",
      "config::execution",
      "config::properties",
      "config::api",
      "irc",
      "irc::playback",
      "irc::port",
      "model",
      "util"
    })
package cafe.woden.ircclient.logging;

import org.springframework.modulith.ApplicationModule;

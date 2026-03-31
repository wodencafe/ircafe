@ApplicationModule(
    displayName = "Logging",
    allowedDependencies = {
      "app::api",
      "config",
      "config::api",
      "irc",
      "irc::playback",
      "model",
      "util"
    })
package cafe.woden.ircclient.logging;

import org.springframework.modulith.ApplicationModule;

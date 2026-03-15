@ApplicationModule(
    displayName = "Application Services",
    allowedDependencies = {
      "config",
      "config::api",
      "dcc",
      "ignore::api",
      "irc",
      "irc::backend",
      "irc::enrichment",
      "irc::playback",
      "irc::port",
      "irc::quassel-control",
      "irc::roster",
      "model",
      "state::api",
      "util"
    })
package cafe.woden.ircclient.app;

import org.springframework.modulith.ApplicationModule;

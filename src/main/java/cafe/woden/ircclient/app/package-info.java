@ApplicationModule(
    displayName = "Application Services",
    allowedDependencies = {
      "config",
      "config::execution",
      "config::properties",
      "config::servers",
      "config::api",
      "dcc::api",
      "ignore::api",
      "irc",
      "irc::backend",
      "irc::enrichment",
      "irc::ircv3",
      "irc::ircv3-spi",
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

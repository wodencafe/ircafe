@ApplicationModule(
    displayName = "User Interface",
    allowedDependencies = {
      "app",
      "app::api",
      "app::commands",
      "app::outbound",
      "bouncer",
      "config",
      "config::api",
      "dcc",
      "diagnostics",
      "ignore",
      "ignore::api",
      "interceptors",
      "irc",
      "irc::backend",
      "irc::ircv3",
      "irc::playback",
      "irc::port",
      "irc::quassel-control",
      "irc::roster",
      "irc::runtime",
      "irc::soju",
      "irc::znc",
      "logging::history",
      "logging::viewer",
      "model",
      "monitor",
      "net",
      "notifications",
      "notify::pushy",
      "notify::sound",
      "state::api",
      "util"
    })
package cafe.woden.ircclient.ui;

import org.springframework.modulith.ApplicationModule;

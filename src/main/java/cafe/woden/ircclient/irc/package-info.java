@ApplicationModule(
    displayName = "IRC Transport",
    allowedDependencies = {
      "bouncer",
      "bouncer::spi",
      "config",
      "config::execution",
      "config::properties",
      "config::servers",
      "config::api",
      "net",
      "state::api",
      "util"
    })
package cafe.woden.ircclient.irc;

import org.springframework.modulith.ApplicationModule;

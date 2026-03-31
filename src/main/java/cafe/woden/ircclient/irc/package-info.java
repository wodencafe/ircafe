@ApplicationModule(
    displayName = "IRC Transport",
    allowedDependencies = {"bouncer", "config", "config::api", "net", "state::api", "util"})
package cafe.woden.ircclient.irc;

import org.springframework.modulith.ApplicationModule;

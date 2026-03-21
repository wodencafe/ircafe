@ApplicationModule(
    displayName = "Diagnostics Support",
    allowedDependencies = {"app::api", "config", "config::api", "model", "notify::sound", "util"})
package cafe.woden.ircclient.diagnostics;

import org.springframework.modulith.ApplicationModule;

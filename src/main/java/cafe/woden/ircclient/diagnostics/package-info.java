@ApplicationModule(
    displayName = "Diagnostics Support",
    allowedDependencies = {
      "app::api",
      "config",
      "config::execution",
      "config::properties",
      "config::api",
      "model",
      "notify::api",
      "plugin::spi",
      "util"
    })
package cafe.woden.ircclient.diagnostics;

import org.springframework.modulith.ApplicationModule;

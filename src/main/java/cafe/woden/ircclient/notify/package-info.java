@ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
      "config",
      "config::execution",
      "config::properties",
      "config::api",
      "model"
    })
package cafe.woden.ircclient.notify;

import org.springframework.modulith.ApplicationModule;

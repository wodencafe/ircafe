@ApplicationModule(
    displayName = "Event Notification Rules",
    allowedDependencies = {
      "app::api",
      "config",
      "config::execution",
      "config::properties",
      "config::api",
      "model",
      "notify::api",
      "notify::api-irc",
      "notify::api-store",
      "notify::api-text"
    })
package cafe.woden.ircclient.notifications;

import org.springframework.modulith.ApplicationModule;

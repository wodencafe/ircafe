@ApplicationModule(
    displayName = "Event Notification Rules",
    allowedDependencies = {"app", "app::api", "config", "model", "notify::pushy"})
package cafe.woden.ircclient.notifications;

import org.springframework.modulith.ApplicationModule;

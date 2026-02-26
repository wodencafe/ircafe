@ApplicationModule(
    displayName = "Event Notification Rules",
    allowedDependencies = {"app::api", "config", "model", "notify::pushy"})
package cafe.woden.ircclient.notifications;

import org.springframework.modulith.ApplicationModule;

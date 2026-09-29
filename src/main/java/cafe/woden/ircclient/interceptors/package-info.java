@ApplicationModule(
    displayName = "Interceptor Engine",
    allowedDependencies = {
      "app::api",
      "config",
      "config::execution",
      "config::api",
      "model",
      "notify::api",
      "notify::api-sound",
      "notify::spi",
      "util"
    })
package cafe.woden.ircclient.interceptors;

import org.springframework.modulith.ApplicationModule;

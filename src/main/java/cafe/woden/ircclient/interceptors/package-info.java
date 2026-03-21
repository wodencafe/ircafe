@ApplicationModule(
    displayName = "Interceptor Engine",
    allowedDependencies = {"app::api", "config", "config::api", "model", "notify::sound", "util"})
package cafe.woden.ircclient.interceptors;

import org.springframework.modulith.ApplicationModule;

package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.config.InstalledPluginServices;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class BackendExtensionCatalogStateConfiguration {
  @NonNull private final InstalledPluginServices installedPluginServices;
  @NonNull private final List<BackendExtension> builtInExtensions;

  @Bean
  BackendExtensionCatalogState backendExtensionCatalogState() {
    return BackendExtensionCatalogState.fromInstalledServices(
        builtInExtensions, installedPluginServices);
  }
}

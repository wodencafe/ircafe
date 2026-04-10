package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Catalog-backed IRCv3 capability alias resolver used by runtime config and UI actions. */
@Component
@SecondaryAdapter
@InfrastructureLayer
public final class Ircv3CapabilityNameResolverAdapter implements Ircv3CapabilityNameResolverPort {

  private final ObjectProvider<Ircv3ExtensionCatalog> ircv3ExtensionCatalogProvider;

  public Ircv3CapabilityNameResolverAdapter(
      ObjectProvider<Ircv3ExtensionCatalog> ircv3ExtensionCatalogProvider) {
    this.ircv3ExtensionCatalogProvider = ircv3ExtensionCatalogProvider;
  }

  @Override
  public String normalizePreferenceKey(String capability) {
    return ircv3ExtensionCatalog().normalizePreferenceKey(capability);
  }

  @Override
  public String normalizeRequestToken(String capability) {
    return ircv3ExtensionCatalog().normalizeRequestToken(capability);
  }

  private Ircv3ExtensionCatalog ircv3ExtensionCatalog() {
    if (ircv3ExtensionCatalogProvider == null) {
      return Ircv3ExtensionCatalog.builtInCatalog();
    }
    return ircv3ExtensionCatalogProvider.getIfAvailable(Ircv3ExtensionCatalog::builtInCatalog);
  }
}

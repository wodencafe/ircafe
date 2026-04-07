package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Catalog-backed IRCv3 capability alias resolver used by runtime config and UI actions. */
@Component
@SecondaryAdapter
@InfrastructureLayer
public final class Ircv3CapabilityNameResolverAdapter implements Ircv3CapabilityNameResolverPort {

  private final Ircv3ExtensionCatalog ircv3ExtensionCatalog;

  public Ircv3CapabilityNameResolverAdapter(Ircv3ExtensionCatalog ircv3ExtensionCatalog) {
    this.ircv3ExtensionCatalog =
        ircv3ExtensionCatalog == null
            ? Ircv3ExtensionCatalog.builtInCatalog()
            : ircv3ExtensionCatalog;
  }

  @Override
  public String normalizePreferenceKey(String capability) {
    return ircv3ExtensionCatalog.normalizePreferenceKey(capability);
  }

  @Override
  public String normalizeRequestToken(String capability) {
    return ircv3ExtensionCatalog.normalizeRequestToken(capability);
  }
}

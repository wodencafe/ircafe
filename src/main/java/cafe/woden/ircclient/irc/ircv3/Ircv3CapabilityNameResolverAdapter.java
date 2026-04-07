package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.util.Ircv3CapabilityNameSupport;
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
    String resolved = ircv3ExtensionCatalog.preferenceKeyFor(capability);
    if (resolved == null || resolved.isBlank()) {
      return Ircv3CapabilityNameSupport.normalizePreferenceKey(capability);
    }
    return resolved;
  }

  @Override
  public String normalizeRequestToken(String capability) {
    String resolved = ircv3ExtensionCatalog.requestTokenFor(capability);
    if (resolved == null || resolved.isBlank()) {
      return Ircv3CapabilityNameSupport.normalizeRequestToken(capability);
    }
    return resolved;
  }
}

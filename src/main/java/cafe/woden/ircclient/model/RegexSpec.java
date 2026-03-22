package cafe.woden.ircclient.model;

import java.util.EnumSet;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * User-specified regex configuration.
 *
 * <p>Compilation is handled elsewhere (e.g. a FilterEngine) so this stays as a simple DTO.
 */
@ValueObject
public record RegexSpec(String pattern, EnumSet<RegexFlag> flags) {

  public RegexSpec {
    pattern = Objects.toString(pattern, "");
    flags = (flags == null) ? EnumSet.noneOf(RegexFlag.class) : EnumSet.copyOf(flags);
  }

  public boolean isEmpty() {
    return pattern.isBlank();
  }

  public boolean caseInsensitive() {
    return flags.contains(RegexFlag.I);
  }
}

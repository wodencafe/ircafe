package cafe.woden.ircclient.model;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** User-defined slash-command alias. */
@ValueObject
public record UserCommandAlias(boolean enabled, String name, String template) {

  public UserCommandAlias {
    name = Objects.toString(name, "").trim();
    template = Objects.toString(template, "");
  }
}

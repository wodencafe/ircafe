package cafe.woden.ircclient.app.commands;

import java.util.Objects;

/** User-defined slash-command alias. */
public record UserCommandAlias(boolean enabled, String name, String template) {

  public UserCommandAlias {
    name = Objects.toString(name, "").trim();
    template = Objects.toString(template, "");
  }
}

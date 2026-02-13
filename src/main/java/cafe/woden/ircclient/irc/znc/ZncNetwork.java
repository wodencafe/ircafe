package cafe.woden.ircclient.irc.znc;

import java.util.Objects;

/** A network discovered from a ZNC bouncer via {@code *status ListNetworks}. */
public record ZncNetwork(String bouncerServerId, String name, Boolean onIrc) {

  public ZncNetwork {
    bouncerServerId = normalize(bouncerServerId);
    name = normalize(name);

    if (bouncerServerId == null) throw new IllegalArgumentException("bouncerServerId is required");
    if (name == null) throw new IllegalArgumentException("name is required");
  }

  private static String normalize(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

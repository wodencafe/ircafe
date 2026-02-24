package cafe.woden.ircclient.irc.soju;

import java.util.Map;
import java.util.Objects;

public record SojuNetwork(
    String bouncerServerId, String netId, String name, Map<String, String> attrs) {

  public SojuNetwork {
    bouncerServerId = normalize(bouncerServerId);
    netId = normalize(netId);
    name = normalize(name);
    attrs = (attrs == null) ? Map.of() : Map.copyOf(attrs);

    if (bouncerServerId == null) throw new IllegalArgumentException("bouncerServerId is required");
    if (netId == null) throw new IllegalArgumentException("netId is required");
    if (name == null) throw new IllegalArgumentException("name is required");
  }

  private static String normalize(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

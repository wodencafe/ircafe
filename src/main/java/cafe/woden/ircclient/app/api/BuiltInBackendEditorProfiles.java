package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;

/** Built-in server-editor metadata for the core IRC, Quassel, and Matrix backends. */
public final class BuiltInBackendEditorProfiles {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private BuiltInBackendEditorProfiles() {}

  public static List<BackendEditorProfileSpec> all() {
    return List.of(irc(), quasselCore(), matrix());
  }

  public static BackendEditorProfileSpec irc() {
    return new BackendEditorProfileSpec(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.IRC),
        6667,
        6697,
        true,
        false,
        true,
        true,
        false,
        "",
        "Host",
        "Server password",
        "Nick",
        "Login/Ident",
        "Real name",
        "Use TLS (SSL)",
        "Direct IRC connection using this profile.",
        "No authentication on connect. Use this for networks that don't require account auth.",
        "(optional)",
        "irc.example.net",
        "ircafe",
        "IRCafeUser",
        "IRCafe User");
  }

  public static BackendEditorProfileSpec quasselCore() {
    return new BackendEditorProfileSpec(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.QUASSEL_CORE),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.QUASSEL_CORE),
        4242,
        4243,
        false,
        false,
        false,
        true,
        true,
        "quassel-user",
        "Host",
        "Core password",
        "Default nick",
        "Core username",
        "Core real name",
        "Use TLS (SSL)",
        "Quassel backend logs into Quassel Core here (default ports: 4242 plain, 4243 TLS)."
            + " Core password can be blank before initial setup. SASL/NickServ below are ignored.",
        "Quassel backend does not run direct IRC SASL/NickServ auth from IRCafe."
            + " Configure upstream network auth inside Quassel Core.",
        "(optional until core is configured)",
        "quassel.example.net",
        "quassel-user",
        "display nick (optional)",
        "display name (optional)");
  }

  public static BackendEditorProfileSpec matrix() {
    return new BackendEditorProfileSpec(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.MATRIX),
        80,
        443,
        false,
        true,
        false,
        false,
        false,
        "",
        "Homeserver",
        "Credential",
        "Nick (optional)",
        "User ID (optional)",
        "Display name (optional)",
        "Use TLS (HTTPS)",
        "Matrix backend connects to this homeserver and authenticates with either access token"
            + " or username/password."
            + " Defaults: 443 TLS, 80 plain. SASL/NickServ below are ignored.",
        "Matrix backend authentication is configured here."
            + " IRC SASL/NickServ settings are ignored.",
        "matrix access token / password",
        "https://matrix.example.org",
        "@alice:matrix.example.org",
        "IRCafeUser (optional)",
        "IRCafe User (optional)");
  }
}

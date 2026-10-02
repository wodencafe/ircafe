package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.BackendDescriptorCatalog;
import java.util.List;

public final class IrcPropertiesTestFixtures {

  private IrcPropertiesTestFixtures() {}

  public static IrcProperties properties(IrcProperties.Server... servers) {
    return new IrcProperties(null, List.of(servers));
  }

  public static IrcProperties.Server server(String id) {
    return serverBuilder(id).build();
  }

  public static ServerBuilder serverBuilder(String id) {
    return new ServerBuilder(id);
  }

  public static final class ServerBuilder {
    private final String id;
    private String host = "irc.example.net";
    private int port = 6697;
    private boolean tls = true;
    private boolean trustAllCertificates;
    private String serverPassword = "";
    private String nick = "ircafe";
    private String login = "ircafe";
    private String realName = "IRCafe User";
    private IrcProperties.Server.Sasl sasl;
    private IrcProperties.Server.Nickserv nickserv;
    private List<String> autoJoin = List.of();
    private List<String> perform = List.of();
    private IrcProperties.Proxy proxy;
    private String backendId =
        BackendDescriptorCatalog.builtIns().idFor(IrcProperties.Server.Backend.IRC);

    private ServerBuilder(String id) {
      this.id = id;
    }

    public ServerBuilder host(String host) {
      this.host = host;
      return this;
    }

    public ServerBuilder port(int port) {
      this.port = port;
      return this;
    }

    public ServerBuilder tls(boolean tls) {
      this.tls = tls;
      return this;
    }

    public ServerBuilder trustAllCertificates(boolean trustAllCertificates) {
      this.trustAllCertificates = trustAllCertificates;
      return this;
    }

    public ServerBuilder serverPassword(String serverPassword) {
      this.serverPassword = serverPassword;
      return this;
    }

    public ServerBuilder nick(String nick) {
      this.nick = nick;
      return this;
    }

    public ServerBuilder login(String login) {
      this.login = login;
      return this;
    }

    public ServerBuilder realName(String realName) {
      this.realName = realName;
      return this;
    }

    public ServerBuilder sasl(IrcProperties.Server.Sasl sasl) {
      this.sasl = sasl;
      return this;
    }

    public ServerBuilder nickserv(IrcProperties.Server.Nickserv nickserv) {
      this.nickserv = nickserv;
      return this;
    }

    public ServerBuilder autoJoin(List<String> autoJoin) {
      this.autoJoin = autoJoin == null ? List.of() : List.copyOf(autoJoin);
      return this;
    }

    public ServerBuilder perform(List<String> perform) {
      this.perform = perform == null ? List.of() : List.copyOf(perform);
      return this;
    }

    public ServerBuilder proxy(IrcProperties.Proxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public ServerBuilder backend(IrcProperties.Server.Backend backend) {
      this.backendId = BackendDescriptorCatalog.builtIns().idFor(backend);
      return this;
    }

    public ServerBuilder backendId(String backendId) {
      this.backendId = backendId;
      return this;
    }

    public IrcProperties.Server build() {
      return new IrcProperties.Server(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          backendId,
          trustAllCertificates);
    }
  }
}

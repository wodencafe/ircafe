package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;
import org.springframework.stereotype.Service;

@Service
public class PircbotxIrcClientService implements IrcClientService {

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final Map<String, Connection> connections = new ConcurrentHashMap<>();
  private final Map<String, IrcProperties.Server> serversById;

  public PircbotxIrcClientService(IrcProperties props) {
    this.serversById = props.byId();
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    return bot == null ? Optional.empty() : Optional.ofNullable(bot.getNick());
  }

  @Override
  public Completable connect(String serverId) {
    return Completable.fromAction(() -> {
          Connection c = conn(serverId);
          if (c.botRef.get() != null) return;

          IrcProperties.Server s = c.server;

          SocketFactory socketFactory = s.tls()
              ? SSLSocketFactory.getDefault()
              : SocketFactory.getDefault();

          Configuration.Builder builder = new Configuration.Builder()
              .setName(s.nick())
              .setLogin(s.login())
              .setRealName(s.realName())
              .addServer(s.host(), s.port())
              .setSocketFactory(socketFactory)
              .setCapEnabled(false) // we may enable below
              .setAutoNickChange(true)
              .setAutoReconnect(true)
              .addListener(new BridgeListener(serverId));

          // Auto-join channels from config
          for (String chan : s.autoJoin()) {
            String ch = chan == null ? "" : chan.trim();
            if (!ch.isEmpty()) builder.addAutoJoinChannel(ch);
          }

          // SASL (PLAIN)
          if (s.sasl() != null && s.sasl().enabled()) {
            if (!"PLAIN".equalsIgnoreCase(s.sasl().mechanism())) {
              throw new IllegalStateException(
                  "Only SASL mechanism PLAIN is supported for now (got: " + s.sasl().mechanism() + ")"
              );
            }
            if (s.sasl().username().isBlank() || s.sasl().password().isBlank()) {
              throw new IllegalStateException("SASL enabled but username/password not set");
            }
            builder.setCapEnabled(true);
            builder.addCapHandler(new SASLCapHandler(s.sasl().username(), s.sasl().password()));
          }

          PircBotX bot = new PircBotX(builder.buildConfiguration());
          c.botRef.set(bot);

          // Run bot on IO thread; connect() completes immediately
          Schedulers.io().scheduleDirect(() -> {
            try {
              bot.startBot();
            } catch (Exception e) {
              bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), "Bot crashed", e)));
            } finally {
              c.botRef.set(null);
            }
          });
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return Completable.fromAction(() -> {
          Connection c = conn(serverId);
          PircBotX bot = c.botRef.getAndSet(null);
          if (bot == null) return;

          try {
            bot.stopBotReconnect();
            try {
              bot.sendIRC().quitServer("Client disconnect");
            } catch (Exception ignored) {}
            try {
              bot.close();
            } catch (Exception ignored) {}
          } finally {
            bus.onNext(new ServerIrcEvent(serverId,
                new IrcEvent.Disconnected(Instant.now(), "Client requested disconnect")));
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return Completable.fromAction(() -> {
          String nick = sanitizeNick(newNick);
          requireBot(serverId).sendIRC().changeNick(nick);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().joinChannel(channel))
        .subscribeOn(Schedulers.io());
  }

@Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return Completable.fromAction(() -> {
          String chan = sanitizeChannel(channel);
          String msg = reason == null ? "" : reason.trim();
          if (msg.isEmpty()) {
            requireBot(serverId).sendRaw().rawLine("PART " + chan);
          } else {
            requireBot(serverId).sendRaw().rawLine("PART " + chan + " :" + msg);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().message(channel, message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().message(sanitizeNick(nick), message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(() -> {
          String chan = sanitizeChannel(channel);
          requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(Schedulers.io());
  }

  private Connection conn(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();
    IrcProperties.Server server = serversById.get(id);
    if (server == null) {
      throw new IllegalArgumentException("Unknown server id: " + id);
    }
    return connections.computeIfAbsent(id, k -> new Connection(server));
  }

  private PircBotX requireBot(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    if (bot == null) throw new IllegalStateException("Not connected: " + serverId);
    return bot;
  }

  private static final class Connection {
    private final IrcProperties.Server server;
    private final AtomicReference<PircBotX> botRef = new AtomicReference<>();

    private Connection(IrcProperties.Server server) {
      this.server = server;
    }
  }

  private static String sanitizeNick(String nick) {
    String n = Objects.requireNonNull(nick, "nick").trim();
    if (n.isEmpty()) throw new IllegalArgumentException("nick is blank");
    if (n.contains("\r") || n.contains("\n"))
      throw new IllegalArgumentException("nick contains CR/LF");
    if (n.contains(" "))
      throw new IllegalArgumentException("nick contains spaces");
    return n;
  }

  private static String sanitizeChannel(String channel) {
    String c = Objects.requireNonNull(channel, "channel").trim();
    if (c.isEmpty()) throw new IllegalArgumentException("channel is blank");
    if (c.contains("\r") || c.contains("\n"))
      throw new IllegalArgumentException("channel contains CR/LF");
    if (c.contains(" "))
      throw new IllegalArgumentException("channel contains spaces");
    if (!(c.startsWith("#") || c.startsWith("&")))
      throw new IllegalArgumentException("channel must start with # or & (got: " + c + ")");
    return c;
  }

  private final class BridgeListener extends ListenerAdapter {
    private final String serverId;

    private BridgeListener(String serverId) {
      this.serverId = serverId;
    }

    @Override
    public void onConnect(ConnectEvent event) {
      PircBotX bot = event.getBot();
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Connected(
          Instant.now(),
          bot.getServerHostname(),
          bot.getServerPort(),
          bot.getNick()
      )));
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), "Disconnected")));
    }

    @Override
    public void onMessage(MessageEvent event) {
      String channel = event.getChannel().getName();
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelMessage(
          Instant.now(), channel, event.getUser().getNick(), event.getMessage()
      )));
    }

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) {
      String from = event.getUser().getNick();
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.PrivateMessage(Instant.now(), from, event.getMessage())));
    }

    @Override
    public void onNotice(NoticeEvent event) {
      String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Notice(Instant.now(), from, event.getNotice())));
    }

    @Override
    public void onUserList(UserListEvent event) {
      emitRoster(event.getChannel());
    }

    @Override
    public void onJoin(JoinEvent event) {
      Channel channel = event.getChannel();

      if (isSelf(event.getBot(), event.getUser().getNick())) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.JoinedChannel(Instant.now(), channel.getName())));
      }

      emitRoster(channel);
    }

    @Override
    public void onPart(PartEvent event) {
      refreshRosterByName(event.getBot(), event.getChannelName());
    }

    @Override
    public void onQuit(QuitEvent event) {
      PircBotX bot = event.getBot();

      boolean refreshedSome = false;
      try {
        UserChannelDaoSnapshot daoSnap = event.getUserChannelDaoSnapshot();
        UserSnapshot userSnap = event.getUser();

        if (daoSnap != null && userSnap != null) {
          for (ChannelSnapshot cs : daoSnap.getChannels(userSnap)) {
            refreshRosterByName(bot, cs.getName());
            refreshedSome = true;
          }
        }
      } catch (Exception ignored) {

      }

      if (!refreshedSome) {
        try {
          for (Channel ch : bot.getUserChannelDao().getAllChannels()) {
            emitRoster(ch);
          }
        } catch (Exception ignored) {}
      }
    }

    @Override
    public void onKick(KickEvent event) {
      emitRoster(event.getChannel());
    }

    @Override
    public void onNickChange(NickChangeEvent event) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.NickChanged(
          Instant.now(),
          event.getOldNick(),
          event.getNewNick()
      )));

      try {
        for (Channel ch : event.getBot().getUserChannelDao().getChannels(event.getUser())) {
          emitRoster(ch);
        }
      } catch (Exception ignored) {}
    }

    @Override
    public void onMode(ModeEvent event) {
      if (event.getChannel() != null) emitRoster(event.getChannel());
    }

    @Override public void onOp(OpEvent event) { emitRoster(event.getChannel()); }
    @Override public void onVoice(VoiceEvent event) { emitRoster(event.getChannel()); }
    @Override public void onHalfOp(HalfOpEvent event) { emitRoster(event.getChannel()); }
    @Override public void onOwner(OwnerEvent event) { emitRoster(event.getChannel()); }
    @Override public void onSuperOp(SuperOpEvent event) { emitRoster(event.getChannel()); }

    private boolean isSelf(PircBotX bot, String nick) {
      return nick != null && nick.equalsIgnoreCase(bot.getNick());
    }

    private void refreshRosterByName(PircBotX bot, String channelName) {
      if (channelName == null || channelName.isBlank()) return;
      try {
        if (!bot.getUserChannelDao().containsChannel(channelName)) return;
        Channel ch = bot.getUserChannelDao().getChannel(channelName);
        if (ch != null) emitRoster(ch);
      } catch (Exception ignored) {}
    }

    private void emitRoster(Channel channel) {
      if (channel == null) return;

      String channelName = channel.getName();
      var opsSet = channel.getOps();

      List<IrcEvent.NickInfo> nicks = channel.getUsers().stream()
          .map(u -> new IrcEvent.NickInfo(
              u.getNick(),
              opsSet.contains(u) ? "@" : ""
          ))
          .sorted(Comparator
              .comparing((IrcEvent.NickInfo n) -> n.prefix().equals("@") ? 0 : 1)
              .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
          .toList();

      int totalUsers = channel.getUsers().size();
      int operatorCount = opsSet.size();

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.NickListUpdated(
          Instant.now(),
          channelName,
          nicks,
          totalUsers,
          operatorCount
      )));
    }
  }
}

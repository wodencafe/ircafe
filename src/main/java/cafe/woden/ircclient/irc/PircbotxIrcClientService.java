package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcServerProperties;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  private final FlowableProcessor<IrcEvent> bus =
      PublishProcessor.<IrcEvent>create().toSerialized();

  private final AtomicReference<PircBotX> botRef = new AtomicReference<>();

  private final IrcServerProperties props;

  public PircbotxIrcClientService(IrcServerProperties props) {
    this.props = props;
  }

  @Override
  public Flowable<IrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Completable connect() {
    return Completable.fromAction(() -> {
          if (botRef.get() != null) return;

          SocketFactory socketFactory = props.tls()
              ? SSLSocketFactory.getDefault()
              : SocketFactory.getDefault();

          Configuration.Builder builder = new Configuration.Builder()
              .setName(props.nick())
              .setLogin(props.login())
              .setRealName(props.realName())
              .addServer(props.host(), props.port())
              .setSocketFactory(socketFactory)
              .setCapEnabled(false) // we may enable below
              .setAutoNickChange(true)
              .setAutoReconnect(true)
              // TODO: Put BridgeListener into its own class / Spring component.
              .addListener(new BridgeListener());

          // Auto-join channels from config
          for (String chan : props.autoJoin()) {
            String c = chan == null ? "" : chan.trim();
            if (!c.isEmpty()) builder.addAutoJoinChannel(c);
          }

          // SASL (PLAIN)
          if (props.sasl() != null && props.sasl().enabled()) {
            if (!"PLAIN".equalsIgnoreCase(props.sasl().mechanism())) {
              throw new IllegalStateException(
                  "Only SASL mechanism PLAIN is supported for now (got: " + props.sasl().mechanism() + ")"
              );
            }
            if (props.sasl().username().isBlank() || props.sasl().password().isBlank()) {
              throw new IllegalStateException("SASL enabled but username/password not set");
            }
            builder.setCapEnabled(true);
            builder.addCapHandler(new SASLCapHandler(props.sasl().username(), props.sasl().password()));
          }

          // IDEA: Should we persist the PircBotX instance
          // and re-use it instead of instantiating a new one
          // upon every connect?
          PircBotX bot = new PircBotX(builder.buildConfiguration());
          botRef.set(bot);

          // Run bot on IO thread; connect() completes immediately
          Schedulers.io().scheduleDirect(() -> {
            try {
              bot.startBot();
            } catch (Exception e) {
              bus.onNext(new IrcEvent.Error(Instant.now(), "Bot crashed", e));
            } finally {
              botRef.set(null);
            }
          });
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable disconnect() {
    return Completable.fromAction(() -> {
          PircBotX bot = botRef.getAndSet(null);
          if (bot == null) return;

          try {
            // Prevent reconnect loop
            bot.stopBotReconnect();
            // Attempt to disconnect gracefully
            try {
              // TODO: Customize quit message
              bot.sendIRC().quitServer("Client disconnect");
            } catch (Exception ignored) {}
            // Hard stop if needed
            try {
              bot.close();
            } catch (Exception ignored) {}
          } finally {
            bus.onNext(new IrcEvent.Disconnected(Instant.now(), "Client requested disconnect"));
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable changeNick(String newNick) {
    return Completable.fromAction(() -> {
          String nick = sanitizeNick(newNick);
          requireBot().sendIRC().changeNick(nick);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable joinChannel(String channel) {
    return Completable.fromAction(() -> requireBot().sendIRC().joinChannel(channel))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendToChannel(String channel, String message) {
    return Completable.fromAction(() -> requireBot().sendIRC().message(channel, message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable requestNames(String channel) {
    return Completable.fromAction(() -> {
          String chan = sanitizeChannel(channel);
          requireBot().sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Optional<String> currentNick() {
    PircBotX bot = botRef.get();
    return bot == null ? Optional.empty() : Optional.ofNullable(bot.getNick());
  }

  private PircBotX requireBot() {
    PircBotX bot = botRef.get();
    if (bot == null) throw new IllegalStateException("Not connected");
    return bot;
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

  // TODO: Move this to its own class.
  private final class BridgeListener extends ListenerAdapter {

    @Override
    public void onConnect(ConnectEvent event) {
      PircBotX bot = event.getBot();
      bus.onNext(new IrcEvent.Connected(
          Instant.now(),
          bot.getServerHostname(),
          bot.getServerPort(),
          bot.getNick()
      ));
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
      bus.onNext(new IrcEvent.Disconnected(Instant.now(), "Disconnected"));
    }

    @Override
    public void onMessage(MessageEvent event) {
      String channel = event.getChannel().getName();
      bus.onNext(new IrcEvent.ChannelMessage(
          Instant.now(), channel, event.getUser().getNick(), event.getMessage()
      ));
    }

    @Override
    public void onNotice(NoticeEvent event) {
      String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
      bus.onNext(new IrcEvent.Notice(Instant.now(), from, event.getNotice()));
    }

    @Override
    public void onUserList(UserListEvent event) {
      emitRoster(event.getChannel());
    }

    @Override
    public void onJoin(JoinEvent event) {
      Channel channel = event.getChannel();

      if (isSelf(event.getBot(), event.getUser().getNick())) {
        bus.onNext(new IrcEvent.JoinedChannel(Instant.now(), channel.getName()));
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
        // Fallback: refresh everything we’re currently tracking
        try {
          for (Channel ch : bot.getUserChannelDao().getAllChannels()) {
            emitRoster(ch);
          }
        } catch (Exception ignored) {}
      }
    }

    /**
     * Someone got kicked (or we got kicked). KickEvent provides Channel.
     */
    @Override
    public void onKick(KickEvent event) {
      emitRoster(event.getChannel());
    }

    /**
     * Nick changed. Update status + rosters for channels user is in.
     */
    @Override
    public void onNickChange(NickChangeEvent event) {
      bus.onNext(new IrcEvent.NickChanged(
          Instant.now(),
          event.getOldNick(),
          event.getNewNick()
      ));

      // Update rosters for channels this user is in (no NAMES)
      try {
        for (Channel ch : event.getBot().getUserChannelDao().getChannels(event.getUser())) {
          emitRoster(ch);
        }
      } catch (Exception ignored) {}
    }

    /**
     * Mode changes can grant/revoke ops. Refresh roster for that channel.
     */
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
      var opsSet = channel.getOps(); // '@' users

      List<IrcEvent.NickInfo> nicks = channel.getUsers().stream()
          .map(u -> new IrcEvent.NickInfo(
              u.getNick(),
              opsSet.contains(u) ? "@" : ""
          ))
          // optional: show ops first, then alpha
          .sorted(Comparator
              .comparing((IrcEvent.NickInfo n) -> n.prefix().equals("@") ? 0 : 1)
              .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
          .toList();

      int totalUsers = channel.getUsers().size();
      int operatorCount = opsSet.size();

      bus.onNext(new IrcEvent.NickListUpdated(
          Instant.now(),
          channelName,
          nicks,
          totalUsers,
          operatorCount
      ));
    }
  }
}

package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxRosterEmitter;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.HalfOpEvent;
import org.pircbotx.hooks.events.ModeEvent;
import org.pircbotx.hooks.events.OpEvent;
import org.pircbotx.hooks.events.OwnerEvent;
import org.pircbotx.hooks.events.SuperOpEvent;
import org.pircbotx.hooks.events.VoiceEvent;

/** Emits live channel-mode observations and refreshes roster snapshots on privilege changes. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxChannelModeEventEmitter {
  @NonNull private final String serverId;
  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final Function<Object, String> nickFromEvent;
  @NonNull private final BiFunction<Object, String, String> modeDetailsFromEvent;

  public void onMode(ModeEvent event) {
    if (event == null || event.getChannel() == null) return;

    rosterEmitter.emitRoster(event.getChannel());

    String channel = event.getChannel().getName();
    String by = nickFromEvent.apply(event);
    String details = modeDetailsFromEvent.apply(event, channel);

    if (details != null && !details.isBlank()) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              ChannelModeObservationFactory.fromLiveMode(Instant.now(), channel, by, details)));
    }
  }

  public void onOp(OpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  public void onVoice(VoiceEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  public void onHalfOp(HalfOpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  public void onOwner(OwnerEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  public void onSuperOp(SuperOpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }
}

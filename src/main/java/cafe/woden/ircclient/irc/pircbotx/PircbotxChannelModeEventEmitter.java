package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pircbotx.hooks.events.HalfOpEvent;
import org.pircbotx.hooks.events.ModeEvent;
import org.pircbotx.hooks.events.OpEvent;
import org.pircbotx.hooks.events.OwnerEvent;
import org.pircbotx.hooks.events.SuperOpEvent;
import org.pircbotx.hooks.events.VoiceEvent;

/** Emits live channel-mode observations and refreshes roster snapshots on privilege changes. */
final class PircbotxChannelModeEventEmitter {
  private final String serverId;
  private final PircbotxRosterEmitter rosterEmitter;
  private final Consumer<ServerIrcEvent> emit;
  private final Function<Object, String> nickFromEvent;
  private final BiFunction<Object, String, String> modeDetailsFromEvent;

  PircbotxChannelModeEventEmitter(
      String serverId,
      PircbotxRosterEmitter rosterEmitter,
      Consumer<ServerIrcEvent> emit,
      Function<Object, String> nickFromEvent,
      BiFunction<Object, String, String> modeDetailsFromEvent) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.rosterEmitter = Objects.requireNonNull(rosterEmitter, "rosterEmitter");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.nickFromEvent = Objects.requireNonNull(nickFromEvent, "nickFromEvent");
    this.modeDetailsFromEvent =
        Objects.requireNonNull(modeDetailsFromEvent, "modeDetailsFromEvent");
  }

  void onMode(ModeEvent event) {
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

  void onOp(OpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  void onVoice(VoiceEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  void onHalfOp(HalfOpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  void onOwner(OwnerEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  void onSuperOp(SuperOpEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }
}

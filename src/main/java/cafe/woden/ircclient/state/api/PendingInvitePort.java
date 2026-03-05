package cafe.woden.ircclient.state.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for pending invite tracking and collapse policy. */
@ApplicationLayer
public interface PendingInvitePort {

  record PendingInvite(
      long id,
      Instant firstSeenAt,
      Instant lastSeenAt,
      String serverId,
      String channel,
      String inviterNick,
      String inviteeNick,
      String reason,
      boolean inviteNotify,
      int repeatCount) {
    public PendingInvite {
      firstSeenAt = firstSeenAt == null ? Instant.now() : firstSeenAt;
      lastSeenAt = lastSeenAt == null ? firstSeenAt : lastSeenAt;
      serverId = PendingInvitePort.normalizeToken(serverId);
      channel = PendingInvitePort.normalizeToken(channel);
      inviterNick = PendingInvitePort.normalizeToken(inviterNick);
      inviteeNick = PendingInvitePort.normalizeToken(inviteeNick);
      reason = Objects.toString(reason, "").trim();
      if (repeatCount <= 0) repeatCount = 1;
    }
  }

  record RecordResult(PendingInvite invite, boolean collapsed) {}

  RecordResult record(
      Instant at,
      String serverId,
      String channel,
      String inviterNick,
      String inviteeNick,
      String reason,
      boolean inviteNotify);

  List<PendingInvite> listForServer(String serverId);

  List<PendingInvite> listAll();

  PendingInvite get(long inviteId);

  PendingInvite latestForServer(String serverId);

  PendingInvite latestAnyServer();

  PendingInvite remove(long inviteId);

  void clearServer(String serverId);

  void clearAll();

  boolean inviteAutoJoinEnabled();

  void setInviteAutoJoinEnabled(boolean enabled);

  static String normalizeToken(String value) {
    return Objects.toString(value, "").trim();
  }
}

package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditRecord;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.Optional;

public final class DbChatRedactionAuditService implements ChatRedactionAuditService {

  private final ChatRedactionAuditRepository repo;
  private final LogProperties props;

  public DbChatRedactionAuditService(ChatRedactionAuditRepository repo, LogProperties props) {
    this.repo = Objects.requireNonNull(repo, "repo");
    this.props = Objects.requireNonNull(props, "props");
  }

  @Override
  public boolean enabled() {
    return Boolean.TRUE.equals(props.enabled())
        && Boolean.TRUE.equals(props.redactionAuditEnabled());
  }

  @Override
  public void record(ChatRedactionAuditRecord record) {
    if (!enabled() || record == null) return;
    repo.insert(record);
  }

  @Override
  public Optional<ChatRedactionAuditRecord> findLatest(TargetRef target, String messageId) {
    if (!enabled() || target == null) return Optional.empty();
    return repo.findLatest(target.serverId(), target.target(), messageId);
  }
}

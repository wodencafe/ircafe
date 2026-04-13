package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditRecord;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Optional;

public final class NoOpChatRedactionAuditService implements ChatRedactionAuditService {

  @Override
  public boolean enabled() {
    return false;
  }

  @Override
  public void record(ChatRedactionAuditRecord record) {}

  @Override
  public Optional<ChatRedactionAuditRecord> findLatest(TargetRef target, String messageId) {
    return Optional.empty();
  }
}

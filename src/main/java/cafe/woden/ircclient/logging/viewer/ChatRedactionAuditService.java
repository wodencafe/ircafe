package cafe.woden.ircclient.logging.viewer;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Optional;

public interface ChatRedactionAuditService {

  boolean enabled();

  void record(ChatRedactionAuditRecord record);

  Optional<ChatRedactionAuditRecord> findLatest(TargetRef target, String messageId);
}

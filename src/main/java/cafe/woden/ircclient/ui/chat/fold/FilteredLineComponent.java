package cafe.woden.ircclient.ui.chat.fold;

import java.util.Collection;

/**
 * Common interface for filtered-line UI components ({@link FilteredFoldComponent}, {@link
 * FilteredHintComponent}, {@link FilteredOverflowComponent}). Allows shared update logic in {@code
 * ChatTranscriptStore} without duplicating method bodies per component type.
 */
public interface FilteredLineComponent {
  void setFilterDetails(String ruleLabel, boolean multiple, Collection<String> tags);
}

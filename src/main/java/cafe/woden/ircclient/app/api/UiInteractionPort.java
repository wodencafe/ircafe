package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Legacy aggregate for UI event streams and interactive prompts. */
@ApplicationLayer
public interface UiInteractionPort extends UiEventPort, UiPromptPort {}

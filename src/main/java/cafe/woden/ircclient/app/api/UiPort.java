package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Aggregate UI boundary used by the application layer. */
@SecondaryPort
@ApplicationLayer
public interface UiPort
    extends UiPromptPort, UiViewStatePort, UiChannelListPort, UiTranscriptPort {}

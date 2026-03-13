package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Aggregate UI boundary used by the application layer. */
@ApplicationLayer
public interface UiPort
    extends UiInteractionPort, UiViewStatePort, UiChannelListPort, UiTranscriptPort {}

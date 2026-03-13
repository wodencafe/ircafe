package cafe.woden.ircclient.state.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Argument policy for one IRC channel mode. */
@ApplicationLayer
public enum ModeArgPolicy {
  LIST,
  ALWAYS,
  SET_ONLY,
  NEVER
}

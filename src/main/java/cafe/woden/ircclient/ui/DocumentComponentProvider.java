package cafe.woden.ircclient.ui;

import java.awt.Component;

/** Creates a separate Swing component on the EDT for each view of a shared document element. */
public interface DocumentComponentProvider {
  /** Returns a new component that can be mounted independently of other views. */
  Component createComponent();
}

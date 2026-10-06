package cafe.woden.ircclient.ui;

import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A document component descriptor that creates a separate Swing component for each transcript view.
 * Store this in {@link javax.swing.text.StyleConstants#ComponentAttribute}; the editor kit mounts
 * the created component rather than this descriptor. Factories must return a new component on each
 * call.
 */
public final class DocumentComponentFactory extends Component {
  private final Supplier<? extends Component> factory;

  public DocumentComponentFactory(Supplier<? extends Component> factory) {
    this.factory = Objects.requireNonNull(factory);
  }

  public Component createComponent() {
    return Objects.requireNonNull(factory.get());
  }
}

package cafe.woden.ircclient.ui.settings.theme;

import javax.swing.JComponent;
import javax.swing.UIDefaults;
import javax.swing.plaf.synth.Region;
import javax.swing.plaf.synth.SynthLookAndFeel;
import javax.swing.plaf.synth.SynthStyle;
import javax.swing.plaf.synth.SynthStyleFactory;

/** Keeps new Nimbus controls from reusing a shared style with a stale UI font. */
final class NimbusFontStyleFactory extends SynthStyleFactory {
  private final SynthStyleFactory delegate;

  private NimbusFontStyleFactory(SynthStyleFactory delegate) {
    this.delegate = delegate;
  }

  static void install() {
    SynthStyleFactory factory = SynthLookAndFeel.getStyleFactory();
    if (!(factory instanceof NimbusFontStyleFactory)) {
      SynthLookAndFeel.setStyleFactory(new NimbusFontStyleFactory(factory));
    }
  }

  static void restore() {
    if (SynthLookAndFeel.getStyleFactory() instanceof NimbusFontStyleFactory factory) {
      SynthLookAndFeel.setStyleFactory(factory.delegate);
    }
  }

  @Override
  public SynthStyle getStyle(JComponent component, Region region) {
    // Nimbus creates a component-owned style for controls with overrides. An empty table
    // inherits the current theme while bypassing the shared style's cached font.
    if (component.getClientProperty("Nimbus.Overrides") == null) {
      component.putClientProperty("Nimbus.Overrides", new UIDefaults());
    }
    return delegate.getStyle(component, region);
  }
}

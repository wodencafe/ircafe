package cafe.woden.ircclient.app.outbound;

/** Base decorator for {@link OutboundCommandDispatcher}. */
public abstract class OutboundCommandDispatcherDecorator implements OutboundCommandDispatcher {

  protected final OutboundCommandDispatcher delegate;

  protected OutboundCommandDispatcherDecorator(OutboundCommandDispatcher delegate) {
    this.delegate = delegate;
  }
}

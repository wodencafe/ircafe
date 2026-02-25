package cafe.woden.ircclient.irc;

/** Provides runtime settings to IRC services without depending on UI modules. */
public interface IrcRuntimeSettingsProvider {

  IrcRuntimeSettings current();
}

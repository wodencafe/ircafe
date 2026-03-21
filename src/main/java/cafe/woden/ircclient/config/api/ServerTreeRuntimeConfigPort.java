package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Aggregated runtime-config contract used by the server-tree dockable assembly. */
@ApplicationLayer
public interface ServerTreeRuntimeConfigPort
    extends IrcSessionRuntimeConfigPort,
        ServerAutoConnectRuntimeConfigPort,
        ServerTreeBuiltInVisibilityConfigPort,
        ServerTreeChannelStateConfigPort,
        ServerTreeLayoutConfigPort,
        UiSettingsRuntimeConfigPort,
        UiShellRuntimeConfigPort {}

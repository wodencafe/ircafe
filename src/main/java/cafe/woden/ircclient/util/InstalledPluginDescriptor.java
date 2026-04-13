package cafe.woden.ircclient.util;

import java.nio.file.Path;

/** Manifest-backed descriptor for a declared external plugin jar. */
public record InstalledPluginDescriptor(
    String pluginId, String pluginVersion, int pluginApiVersion, Path sourceJar) {}

package cafe.woden.ircclient.config.runtime;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.runtime.commands.RuntimeConfigUserCommandStore;
import cafe.woden.ircclient.config.runtime.connection.RuntimeConfigConnectionStoreDelegates;
import cafe.woden.ircclient.config.runtime.ignore.RuntimeConfigIgnoreRulesStore;
import cafe.woden.ircclient.config.runtime.interceptors.RuntimeConfigInterceptorStore;
import cafe.woden.ircclient.config.runtime.ircv3.RuntimeConfigIrcv3StoreDelegates;
import cafe.woden.ircclient.config.runtime.launch.RuntimeConfigLaunchJvmStore;
import cafe.woden.ircclient.config.runtime.logging.RuntimeConfigChatLoggingStore;
import cafe.woden.ircclient.config.runtime.notifications.RuntimeConfigPushyStore;
import cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerStoreDelegates;
import cafe.woden.ircclient.config.runtime.ui.RuntimeConfigUiStoreDelegates;
import cafe.woden.ircclient.config.yaml.RuntimeConfigDocumentStore;
import java.nio.file.Path;

/** Wires the focused runtime-config stores used by {@link RuntimeConfigStore}. */
public final class RuntimeConfigStoreDelegates {

  public final RuntimeConfigDocumentStore documentStore;
  public final RuntimeConfigUiStoreDelegates uiStores;
  public final RuntimeConfigServerStoreDelegates serverStores;
  public final RuntimeConfigLaunchJvmStore launchJvmStore;
  public final RuntimeConfigUserCommandStore userCommandStore;
  public final RuntimeConfigInterceptorStore interceptorStore;
  public final RuntimeConfigIgnoreRulesStore ignoreRulesStore;
  public final RuntimeConfigChatLoggingStore chatLoggingStore;
  public final RuntimeConfigPushyStore pushyStore;
  public final RuntimeConfigIrcv3StoreDelegates ircv3Stores;
  public final RuntimeConfigConnectionStoreDelegates connectionStores;

  public RuntimeConfigStoreDelegates(Path file, IrcProperties defaults) {
    this.documentStore = new RuntimeConfigDocumentStore(file);
    this.uiStores = new RuntimeConfigUiStoreDelegates(file, documentStore);
    this.serverStores = new RuntimeConfigServerStoreDelegates(file, documentStore, defaults);
    this.launchJvmStore = new RuntimeConfigLaunchJvmStore(file, documentStore);
    this.userCommandStore = new RuntimeConfigUserCommandStore(file, documentStore);
    this.interceptorStore = new RuntimeConfigInterceptorStore(file, documentStore);
    this.ignoreRulesStore = new RuntimeConfigIgnoreRulesStore(file, documentStore);
    this.chatLoggingStore = new RuntimeConfigChatLoggingStore(file, documentStore);
    this.pushyStore = new RuntimeConfigPushyStore(file, documentStore);
    this.ircv3Stores = new RuntimeConfigIrcv3StoreDelegates(file, documentStore);
    this.connectionStores = new RuntimeConfigConnectionStoreDelegates(file, documentStore);
  }
}

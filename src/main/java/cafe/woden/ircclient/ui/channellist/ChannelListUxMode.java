package cafe.woden.ircclient.ui.channellist;

import java.awt.Window;

interface ChannelListUxMode {
  record ActionPresentation(
      String primaryTooltip,
      String primaryAccessibleName,
      String secondaryTooltip,
      String secondaryAccessibleName,
      boolean pagingVisible,
      String pagingTooltip,
      String pagingAccessibleName) {}

  interface Context {
    Window ownerWindow();

    boolean confirmFullListRequest();

    void clearFilterText();

    void rememberRequestType(String serverId, ChannelListRequestType requestType);

    void beginList(String serverId, String banner);

    void emitRunListRequest();

    void emitRunCommand(String command);

    void updateListButtons();
  }

  String defaultHint();

  ActionPresentation actionPresentation();

  void runPrimaryAction(Context context, String serverId);

  void runSecondaryAction(Context context, String serverId);

  void runPagingAction(Context context, String serverId);

  void onBeginList(String serverId, String banner);

  void onEndList(String serverId, String summary);

  boolean isPagingActionEnabled(String serverId);

  ChannelListRequestType inferRequestTypeFromBanner(String banner);
}

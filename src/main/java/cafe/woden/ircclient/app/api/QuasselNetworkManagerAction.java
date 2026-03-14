package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import java.util.Objects;

/** User-selected operation from the Quassel network manager dialog. */
public record QuasselNetworkManagerAction(
    Operation operation,
    String networkIdOrName,
    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest,
    QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest updateRequest) {

  public enum Operation {
    REFRESH,
    CONNECT,
    DISCONNECT,
    REMOVE,
    ADD,
    EDIT
  }

  public QuasselNetworkManagerAction {
    Objects.requireNonNull(operation, "operation");
    networkIdOrName = Objects.toString(networkIdOrName, "").trim();
  }

  public static QuasselNetworkManagerAction refresh() {
    return new QuasselNetworkManagerAction(Operation.REFRESH, "", null, null);
  }

  public static QuasselNetworkManagerAction connect(String networkIdOrName) {
    return new QuasselNetworkManagerAction(Operation.CONNECT, networkIdOrName, null, null);
  }

  public static QuasselNetworkManagerAction disconnect(String networkIdOrName) {
    return new QuasselNetworkManagerAction(Operation.DISCONNECT, networkIdOrName, null, null);
  }

  public static QuasselNetworkManagerAction remove(String networkIdOrName) {
    return new QuasselNetworkManagerAction(Operation.REMOVE, networkIdOrName, null, null);
  }

  public static QuasselNetworkManagerAction add(
      QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest) {
    return new QuasselNetworkManagerAction(Operation.ADD, "", createRequest, null);
  }

  public static QuasselNetworkManagerAction edit(
      String networkIdOrName,
      QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest updateRequest) {
    return new QuasselNetworkManagerAction(Operation.EDIT, networkIdOrName, null, updateRequest);
  }
}

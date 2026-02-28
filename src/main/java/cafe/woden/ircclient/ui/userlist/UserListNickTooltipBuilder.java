package cafe.woden.ircclient.ui.userlist;

import cafe.woden.ircclient.ignore.IgnoreMaskMatcher;
import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.util.Objects;

public class UserListNickTooltipBuilder {

  public String build(NickInfo nickInfo, boolean ignored, boolean softIgnored) {
    if (nickInfo == null) return null;

    String nick = Objects.toString(nickInfo.nick(), "").trim();
    if (nick.isEmpty()) return null;

    String hostmask = Objects.toString(nickInfo.hostmask(), "").trim();
    String realName = Objects.toString(nickInfo.realName(), "").trim();
    AwayState away = (nickInfo.awayState() == null) ? AwayState.UNKNOWN : nickInfo.awayState();
    AccountState account =
        (nickInfo.accountState() == null) ? AccountState.UNKNOWN : nickInfo.accountState();

    boolean hasHostmask = IgnoreMaskMatcher.isUsefulHostmask(hostmask);

    StringBuilder sb = new StringBuilder(128);
    sb.append("<html>");
    sb.append("<b>").append(escapeHtml(nick)).append("</b>");

    if (!realName.isEmpty()) {
      sb.append("<br>").append("<i>Name</i>: ").append(escapeHtml(realName));
    }

    if (hasHostmask) {
      sb.append("<br>")
          .append("<span style='font-family:monospace'>")
          .append(escapeHtml(hostmask))
          .append("</span>");
    } else {
      sb.append("<br>").append("<i>Hostmask pending</i>");
    }

    if (away == AwayState.AWAY) {
      String reason = nickInfo.awayMessage();
      if (reason != null && !reason.isBlank()) {
        sb.append("<br>").append("<i>Away</i>: ").append(escapeHtml(reason));
      } else {
        sb.append("<br>").append("<i>Away</i>");
      }
    }

    if (account == AccountState.LOGGED_IN) {
      String accountName = nickInfo.accountName();
      if (accountName != null && !accountName.isBlank()) {
        sb.append("<br>").append("<i>Account</i>: ").append(escapeHtml(accountName.trim()));
      } else {
        sb.append("<br>").append("<i>Account</i>: ").append("<i>logged in</i>");
      }
    } else if (account == AccountState.LOGGED_OUT) {
      sb.append("<br>").append("<i>Account</i>: ").append("<i>logged out</i>");
    } else {
      sb.append("<br>").append("<i>Account</i>: ").append("<i>unknown</i>");
    }

    if (ignored && softIgnored) {
      sb.append("<br>").append("Ignored + soft ignored");
    } else if (ignored) {
      sb.append("<br>").append("Ignored (messages hidden)");
    } else if (softIgnored) {
      sb.append("<br>").append("Soft ignored (messages shown as spoilers)");
    }

    sb.append("</html>");
    return sb.toString();
  }

  private static String escapeHtml(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&':
          sb.append("&amp;");
          break;
        case '<':
          sb.append("&lt;");
          break;
        case '>':
          sb.append("&gt;");
          break;
        case '"':
          sb.append("&quot;");
          break;
        case '\'':
          sb.append("&#39;");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }
}

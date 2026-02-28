package cafe.woden.ircclient.ui.userlist;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import org.junit.jupiter.api.Test;

class UserListNickTooltipBuilderTest {

  private final UserListNickTooltipBuilder builder = new UserListNickTooltipBuilder();

  @Test
  void buildReturnsNullWhenNickIsMissing() {
    String tooltip = builder.build(new NickInfo(" ", "", "nick!u@h"), false, false);
    assertNull(tooltip);
  }

  @Test
  void buildIncludesHostmaskAccountAwayAndIgnoreStatus() {
    NickInfo nickInfo =
        new NickInfo(
            "alice",
            "@",
            "alice!u@h",
            AwayState.AWAY,
            "out",
            AccountState.LOGGED_IN,
            "acct",
            "Alice Example");

    String tooltip = builder.build(nickInfo, true, true);

    assertTrue(tooltip.contains("<b>alice</b>"));
    assertTrue(tooltip.contains("<i>Name</i>: Alice Example"));
    assertTrue(tooltip.contains("alice!u@h"));
    assertTrue(tooltip.contains("<i>Away</i>: out"));
    assertTrue(tooltip.contains("<i>Account</i>: acct"));
    assertTrue(tooltip.contains("Ignored + soft ignored"));
  }

  @Test
  void buildEscapesHtmlAndShowsPendingHostmask() {
    NickInfo nickInfo =
        new NickInfo(
            "a<b>&\"'",
            "",
            "",
            AwayState.UNKNOWN,
            null,
            AccountState.LOGGED_OUT,
            null,
            "Real <Name>");

    String tooltip = builder.build(nickInfo, false, true);

    assertTrue(tooltip.contains("<b>a&lt;b&gt;&amp;&quot;&#39;</b>"));
    assertTrue(tooltip.contains("<i>Name</i>: Real &lt;Name&gt;"));
    assertTrue(tooltip.contains("<i>Hostmask pending</i>"));
    assertTrue(tooltip.contains("<i>Account</i>: <i>logged out</i>"));
    assertTrue(tooltip.contains("Soft ignored (messages shown as spoilers)"));
  }
}

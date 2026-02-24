package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilterCommandParserTest {

  private final FilterCommandParser parser = new FilterCommandParser();

  @Test
  void parsesWeeChatPositionalAddWhenRegexContainsEquals() {
    FilterCommand cmd = parser.parse("/filter add eqrule irc.libera.#chan irc_privmsg foo=bar");
    FilterCommand.Add add = assertInstanceOf(FilterCommand.Add.class, cmd);

    assertEquals("eqrule", add.name());
    assertTrue(add.patch().scopeSpecified());
    assertEquals("libera/#chan", add.patch().scope());
    assertTrue(add.patch().textSpecified());
    assertEquals("foo=bar", add.patch().textRegex().pattern());
  }

  @Test
  void stillParsesKeyValueAddForm() {
    FilterCommand cmd =
        parser.parse("/filter add named scope=libera/#chan tags=irc_privmsg text=foo");
    FilterCommand.Add add = assertInstanceOf(FilterCommand.Add.class, cmd);

    assertEquals("named", add.name());
    assertTrue(add.patch().scopeSpecified());
    assertEquals("libera/#chan", add.patch().scope());
    assertTrue(add.patch().tagsSpecified());
    assertEquals("irc_privmsg", add.patch().tagsExpr());
    assertTrue(add.patch().textSpecified());
    assertEquals("foo", add.patch().textRegex().pattern());
  }
}

package cafe.woden.ircclient.irc.pircbotx.parse;

import java.util.List;

public record ParsedIrcLine(String prefix, String command, List<String> params, String trailing) {}

package cafe.woden.ircclient.app.notifications;

/**
 * A single rule match against a message.
 *
 * @param ruleLabel display label of the rule that matched
 * @param matchedText the matched substring (group 0 for regex rules)
 * @param start start index in the message (inclusive)
 * @param end end index in the message (exclusive)
 * @param highlightColor optional per-rule highlight color (hex, e.g. "#FFCC66")
 */
public record NotificationRuleMatch(
    String ruleLabel, String matchedText, int start, int end, String highlightColor) {}

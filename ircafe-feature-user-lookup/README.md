# User lookup feature

Owns `UserInfoEnrichmentPlanner` and its tests: probe queues, USERHOST batching,
WHO/WHOIS rate limits, per-target cooldown and backoff, periodic roster refresh,
and the settings/command values exchanged with the application.

This is an application feature spanning several IRC capabilities. It stays in the
existing `cafe.woden.ircclient.irc.enrichment` package and `irc::enrichment`
Spring Modulith named interface. Its Gradle dependencies enforce isolation from
root application types.

`UserInfoEnrichmentService` remains in the root application and owns event
subscriptions, scheduling, settings adaptation, connection lifecycle, and command
execution. It owns calls to the planner's per-server cleanup API. The feature
creates no workers or subscriptions.

WHO/WHOX/WHOIS/USERHOST response parsing remains in
`ircafe-feature-ircv3-user-identity`. This module has no ServiceLoader provider or
plugin SPI.

Run the planner tests with:

```sh
GRADLE_USER_HOME=.gradle-local ./gradlew :ircafe-feature-user-lookup:test
```

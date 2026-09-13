# IRCv3 labeled-response

This feature owns command label generation/rendering, response signals, and
server-scoped request tracking in `LabeledResponseRequestStore<T>`.

The store treats routing context as an opaque value. It handles freshness checks,
outcome transitions, timeout collection, stale retention, and server cleanup,
using only JDK types. Reads retain requests for multi-line replies. Failure may
override success or timeout; repeated terminal outcomes do not emit transitions.
The existing ten-minute stale retention and caller-driven pruning are preserved.

The root `LabeledResponseRoutingState` Spring bean owns `TargetRef` normalization,
request-preview formatting, and conversion to the application routing port. Its
callers continue to drive timeout checks and disconnect cleanup. The store adds
no worker, timer, subscription, or Spring bean.

The store stays in the `cafe.woden.ircclient.state` Java package to preserve the
Spring Modulith state boundary. Gradle source ownership belongs to this feature;
the feature does not depend on root application types or IRC transports.

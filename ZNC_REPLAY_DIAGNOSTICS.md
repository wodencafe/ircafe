# ZNC replay diagnostics

Start IRCafe with the `znc-debug` Spring profile. From the repository:

```sh
./gradlew bootRun --args='--spring.profiles.active=znc-debug'
```

In IntelliJ, add `--spring.profiles.active=znc-debug` to the app run configuration's
**Program arguments**. If other profiles are needed, include them in the comma-separated list.
Restart the app to apply the profile.

The capture is written to `${java.io.tmpdir}/ircafe-znc.log` (normally
`/tmp/ircafe-znc.log` on Linux), with rotation at 5 MB and a 15 MB archive cap.
It includes raw incoming IRC lines and therefore chat message text. The new replay and
routing diagnostics record metadata only. Raw outbound logging remains disabled.

Disconnect IRCafe, allow new activity in an affected channel, and reconnect. Record
the channel, the disconnect/reconnect times, and one expected message's time. In IRCafe,
these commands provide the live retention setting and request any retained buffer:

```text
/msg *controlpanel GetChan AutoClearChanBuffer $me $net ##Llamas
/msg *status PlayBuffer ##Llamas
```

Replace `##Llamas` with the affected channel. `GetChan` requires ZNC's `controlpanel`
module; `PlayBuffer` is a built-in ZNC command.

Read the capture in this order:

1. `replay bootstrap: mode=server-buffer-if-available` means the optional
   `znc.in/playback` capability was not negotiated. Native ZNC buffer replay comes
   from the server automatically when retained messages are available.
   `explicit-playback-request` means IRCafe sends a `*playback` request; the next log
   records its cutoff in UTC and epoch seconds.
   `zncDetected=false` does not rule out ZNC: it can forward the upstream IRC server's
   version reply without identifying itself there. The replay policy is logged either way.
2. Raw incoming `BATCH +... znc.in/playback` and `PRIVMSG` lines establish what the
   server sent. Older `@time` tags are the original message times. Clients without
   negotiated `batch` support can receive plain replayed messages instead.
3. `ZNC playback batch started/ended` records the batch target, observed message count,
   and earliest/latest original timestamps. These counts cover messages seen by the
   message handlers, including lines subsequently captured for history.
   Without a usable `server-time` tag, message timestamps fall back to receipt time.
4. `outcome=event-emitted` means the protocol bridge published a channel message;
   `history-captured` means an active history capture took it instead.
5. The mediator logs `chat-ui-submitted`, `spoiler-ui-submitted`, `ignored`,
   `duplicate-msgid`, `message-edit-applied`, or `pending-echo-resolved`.
   UI submission confirms the append was requested; it does not confirm a Swing paint
   or visibility in the selected transcript. Action/private-message routing is not
   covered by these channel-text routing logs.

An empty buffer reply establishes that ZNC currently has nothing retained for that
channel. Missing batches alone do not prove an empty buffer: check raw message lines
and whether `batch` was negotiated. A replay received on the wire but missing from
later stages narrows the investigation to IRCafe.

Stop the capture by removing the `znc-debug` profile and restarting. The log file can
be copied directly for analysis and deleted when no longer needed.

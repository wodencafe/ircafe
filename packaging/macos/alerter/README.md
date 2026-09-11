# Bundled Alerter helper

IRCafe bundles [Alerter](https://github.com/vjeantet/alerter) v26.5 for native macOS desktop
notifications. It replaces the legacy x86-only `terminal-notifier` helper.

The executable was built from the upstream `v26.5` source tag with:

```sh
swift build -c release --arch arm64 --arch x86_64
```

It is a universal Mach-O executable. Its SHA-256 is
`5a4f2e951a404cfa9bbef36f6dc39f3d53a866b8b74a016f21b5fcb7e1b7f11e`.

Alerter requires macOS 13 or later and is distributed under the MIT License; see `LICENSE`.

The packaging task re-signs the final app image after copying the helper. Local builds use an ad
hoc signature; release builds can pass `-PmacCodeSignIdentity="Developer ID Application: ..."`.

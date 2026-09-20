# Yet Another Widget

**Yet Another Widget** is the working identity for an independent, pre-release restoration of the MIT-licensed Android project **Another Widget**.

The goal is a simple, reliable home-screen glance widget for date/time, calendar events, weather, battery, notifications, and media context on current Android versions. This repository is not an official continuation of the historical app and is not affiliated with or endorsed by its original developer.

## Release status

This project is still in release-integration and certification work. No production Google Play release is authorized from this branch. The approved public identity is **Yet Another Widget**, with independent Android package `com.ramybaheeg.yetanotherwidget`, first-release version `1.0.0` (`versionCode 1`), and original restoration-owned launcher/splash artwork. Signing identity, Play account actions, store activation, and the public privacy-policy route remain external release gates.

The restoration intentionally minimizes permissions and inherited services. Current work includes API-36 compatibility, supported CalendarContract and WorkManager paths, foreground-only coarse location for optional weather geolocation, removal of obsolete Google Fit/Billing/remote-font paths, and publication hardening.

## Open-source provenance

This restoration is based on **Another Widget** by Tommaso Berlose, released under the MIT License. The original copyright and license terms remain preserved in [LICENSE](LICENSE).

Additional third-party provenance and notices are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Contributions and feedback

Use this repository's issue tracker for restoration bugs and feature discussion. Translation contributions can be prepared against the current English strings in `app/src/main/res/values/strings.xml`.

## Coordination

Interactive and scheduled restoration workers must follow [docs/INTERACTIVE_WORKER_COORDINATION.md](docs/INTERACTIVE_WORKER_COORDINATION.md) before taking a source slice, so concurrent work is reconciled rather than duplicated.

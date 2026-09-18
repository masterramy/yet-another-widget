# Third-Party Notices

This file accompanies the pre-release Yet Another Widget restoration and identifies
license material deliberately bundled into the Android package. It is a
redistribution/provenance record, not a claim that every SDK dependency is open source.

## Original application

This restoration is based on **Another Widget** by Tommaso Berlose.

- Original copyright: Copyright (c) 2020 Tommaso Berlose
- License: MIT
- Full license: `assets/licenses/ORIGINAL_APP_LICENSE.txt`

## Apache License 2.0 family

The release uses the following direct/runtime libraries whose upstream projects or
published metadata identify them under the Apache License 2.0. A full copy of that
license is bundled at `assets/licenses/APACHE-2.0.txt`.

- AndroidX runtime libraries used by this app (AppCompat, ConstraintLayout, Browser,
  Lifecycle, WorkManager, Room, Navigation, Multidex, Core KTX, Palette, Preference)
- Google Material Components for Android
- Google Flexbox for Android
- SwitchButton 2.1.0 (kyleduo)
- EventBus 3.2.0 (greenrobot)
- android-joda 2.10.9 (dlew)
- IndicatorSeekBar 2.1.1 (warkiz)
- Retrofit 2.9.0 and converter-gson 2.9.0 (Square)
- Gson 2.14.0 (Google)
- OkHttp logging-interceptor 4.9.2 (Square)
- NetworkResponseAdapter 4.0.1 (haroldadmin)
- kotlinx-coroutines-android 1.4.1 (JetBrains)
- Kotpref 2.13.1 and livedata-support 2.13.1 (chibatching)
- Dexter 6.2.1 (Karumi)
- Kotlin runtime components pulled by the Kotlin Android build

## Glide

Glide 4.12.0 contains its own BSD-style license plus separately licensed third-party
components. The complete upstream Glide license file is bundled without abridgment at:

`assets/licenses/GLIDE_LICENSE.txt`

## SlimAdapter

Yet Another Widget carries a compatibility-patched, vendored source snapshot of
SlimAdapter in shipping source.

- Upstream project: `linisme/SlimAdapter`
- Pinned upstream commit: `3e00f876906019ac224e29159ff1a777c4a47d4b`
- License: MIT
- Upstream copyright: Copyright (c) 2017 IDIK

The vendored compatibility patch removes the upstream `package-info.java`
default-nullness annotation and narrows `SlimInjector`'s injector callback to the
concrete `DefaultViewInjector` self type required by the modern Kotlin compiler.
These restoration changes do not alter SlimAdapter's MIT license.

The complete SlimAdapter MIT license text follows.

MIT License

Copyright (c) 2017 IDIK

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Google Play services

The app also depends on `com.google.android.gms:play-services-location:21.0.1` for
Android fused-location APIs. This notice does **not** characterize that Google SDK
dependency as Apache-2.0; its use/distribution is governed by the applicable Google
SDK/Google Play services terms.

## OkHttp Public Suffix List data

OkHttp ships a compiled Public Suffix List used for domain-boundary handling. Its packaged
`okhttp3/internal/publicsuffix/NOTICE` identifies the Mozilla Public Suffix List data
under the Mozilla Public License 2.0. That upstream NOTICE and data remain packaged in
the APK/AAB and are not removed by this project.

## Build/test-only dependencies

Test runners, JUnit, Android test libraries, annotation processors, and Android build
tooling are not listed here as shipping runtime features merely because they participate
in the build. Any license material embedded by transitive libraries remains in addition
to this deliberate bundle.

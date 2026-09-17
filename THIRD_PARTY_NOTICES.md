# Third-Party Notices

## SlimAdapter

Yet Another Widget carries a compatibility-patched, vendored source snapshot of SlimAdapter in shipping source.

- Upstream project: `linisme/SlimAdapter`
- Pinned upstream commit: `3e00f876906019ac224e29159ff1a777c4a47d4b`
- License: MIT
- Upstream copyright: Copyright (c) 2017 IDIK

The vendored compatibility patch removes the upstream `package-info.java` default-nullness annotation and narrows `SlimInjector`'s injector callback to the concrete `DefaultViewInjector` self type required by the modern Kotlin compiler. These restoration changes do not alter SlimAdapter's MIT license.

The complete MIT license text for this dependency is preserved below.

> MIT License
>
> Copyright (c) 2017 IDIK
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

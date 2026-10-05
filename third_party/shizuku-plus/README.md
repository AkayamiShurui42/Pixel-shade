# Vendored Shizuku Plus client

This directory contains the MIT-licensed client-only source used to generate Pixel Shade’s Shizuku Plus AARs in GitHub Actions. It is pinned to upstream commit `523816ca7c34a6f8090a04880e36195951849cca` and retains the accompanying `LICENSE`.

Only the `aidl`, `shared`, `api`, and `provider` modules are included. The Shizuku server, demo applications, and unrelated modules are deliberately omitted. Pixel Shade's app build compiles these four modules when their AARs are missing and stages them into `app/libs`, then compiles against the matching `af.shizuku` client/provider namespace.

# Vendored Shizuku Plus client

This directory contains the MIT-licensed client-only source used to generate Pixel Shade’s Shizuku Plus AARs in GitHub Actions. It is pinned to upstream commit `523816ca7c34a6f8090a04880e36195951849cca` and retains the accompanying `LICENSE`.

Only the `aidl`, `shared`, `api`, and `provider` modules are included. The Shizuku server, demo applications, and unrelated modules are deliberately omitted. CI builds these four modules, stages their AARs into `app/libs`, then compiles Pixel Shade against the matching `af.shizuku` client/provider namespace.

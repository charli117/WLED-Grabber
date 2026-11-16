# FND-2 lint baseline ownership (2026-11-16)

Recorded by `./gradlew :common:lintDebug :mobile:lintDebug :tv:lintDebug --no-daemon --continue`.
Raw logs and reports: `lint-baseline.txt`, `lint-summary.txt`, and each module's
`build/reports/lint-results-debug.{txt,xml,html}`.

The lint gate is intentionally not zero at FND-2. QA-1 remains the zero-error gate.
No global lint baseline or ignore was added.

| Lint issue | Module(s) | Owner / disposition |
| --- | --- | --- |
| MissingSuperCall | common, mobile, tv | CAP-1/CAP-2 lifecycle and cleanup; host wiring by MOB-2/TV-2 |
| MissingPermission | mobile | MOB-2 (tile/entry permission flow) |
| NewApi | mobile | CAP-2/MOB-2 API matrix; no minSdk increase |
| MissingTranslation | common, mobile, tv | MOB-1/TV-1/REL-1 localization parity; not a behavior fix |
| NonConstantResourceId | mobile, tv | FND-2 follow-up warning; replace remaining R-id switch checks in MOB/TV UI tasks |
| ObsoleteSdkInt | common, mobile, tv | CAP/MOB minSdk 21 cleanup |
| GradleDependency / AndroidGradlePluginVersion | build | FND-1 accepted warning: versions are intentionally pinned; no dynamic upgrade |
| ExportedService | common | CAP-1 manifest/export policy |
| UnusedResources | tv | REL-1 cleanup after UI switch |
| UsingOnClickInXml / Overdraw / SetTextI18n | tv | TV-1/TV-2 UI tasks |
| TypographyEllipsis / VectorPath / VectorRaster / Icon* | common, tv | TV-1/TV-2 and resource cleanup |
| DiscouragedApi / DrawAllocation / DefaultLocale | common, mobile | CAP-2/MOB-2 UI/lifecycle tasks |

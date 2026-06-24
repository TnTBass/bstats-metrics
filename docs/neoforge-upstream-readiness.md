# NeoForge Upstream Readiness Checklist

This checklist tracks the remaining NeoForge work before any upstream submission is
considered. It is not an upstream-submission plan, release plan, deployment plan, or runtime
smoke-test approval.

## Current Local Checkpoint

- Branch: `codex/neoforge-metrics`
- Holding fork draft PR: `TnTBass/bstats-metrics#2`
- Current scope: keep the fork PR as a local review checkpoint while NeoForge catches up to the
  expected metrics workflow.
- Platform rollout target: match the Fabric fork PR shape across metrics, backend software/global
  rollup wiring, and data-processor submit-data coverage before deciding whether upstream
  submission is appropriate.
- Upstream status: do not reopen, replace, or submit a new upstream PR until the remaining
  NeoForge work is complete and explicitly approved.

## Satisfied Before Runtime Testing

- `settings.gradle.kts` includes the `neoforge` module.
- `generateMetrics` emits a NeoForge generated single-file metrics class.
- The NeoForge module publishes with the standard `bstats-neoforge` artifact naming.
- The NeoForge source compiles against NeoForge and FML dependencies.
- The NeoForge metrics class uses NeoForge lifecycle events to capture server state.
- The local runtime smoke-test plan is documented in
  [`neoforge-runtime-smoke-test.md`](neoforge-runtime-smoke-test.md).

## Still Required Before Upstream Submission

### Backend Software And Global Rollup

- Add NeoForge as a backend software entry, analogous to the Fabric software seed/backfill work.
- Create the NeoForge global plugin and global charts needed for platform-level rollups.
- Backfill NeoForge records into already-populated stores without overwriting existing IDs.
- Add focused backend coverage for the NeoForge seed/backfill behavior.
- Verify a local or private backend stack accepts the NeoForge software/global rollup path before
  treating the backend work as ready.

### Data Processor Submit Path

- Add `/submitData/neoforge` fixtures and routing coverage, analogous to the Fabric submit-data
  fixtures.
- Include NeoForge software, service, and chart fixture data for rollup tests.
- Prove a NeoForge submit payload writes the expected global server and chart data.
- Keep payload examples scrubbed and synthetic unless explicit approval is given to capture
  operator-specific runtime evidence.

### Integrated Metrics And Runtime Smoke Gate

- Defer metrics regeneration, NeoForge compile freshness, and runtime smoke testing until backend
  and data-processor parity are ready enough for an integrated verification pass.
- Regenerate `neoforge/build/generated/Metrics.java` from the current source checkpoint.
- Compile `:neoforge:compileJava` from the current source checkpoint.
- Prepare a disposable NeoForge test mod that relocates the generated class away from
  `org.bstats.neoforge`.
- Prepare a local or private receiver that accepts the NeoForge platform path and decompresses
  gzip request bodies before inspecting payload JSON.
- Run the deferred runtime smoke test only once, after explicit approval for the local/private
  receiver and disposable runtime scope.
- Capture scrubbed runtime evidence using the template in
  [`neoforge-runtime-smoke-test.md`](neoforge-runtime-smoke-test.md).
- Reconcile any new code, docs, or runtime findings before treating the branch as ready for an
  upstream-submission decision.

### Cross-Repository Checkpoint

- Keep metrics, backend, and data-processor work in separate fork PRs until each repo has focused
  verification.
- Do not promote the NeoForge metrics fork PR as an upstream-submission candidate until backend and
  data-processor parity are complete or deliberately scoped out with explicit approval.
- Summarize the three-repo NeoForge state together before any upstream-submission decision.

## Fabric Parity Reference

The Fabric fork rollout used three holding PRs:

- `TnTBass/bstats-metrics#1`: Fabric metrics module, generation wiring, and runtime smoke evidence.
- `TnTBass/bstats-backend#1`: Fabric software seed/backfill plus global plugin/charts.
- `TnTBass/bstats-data-processor#1`: `/submitData/fabric` fixtures and global rollup coverage.

NeoForge should follow the same project shape, replacing Fabric-specific APIs, fixtures, and
software identifiers with NeoForge-native equivalents.

## Explicit Non-Goals

- Do not send smoke-test telemetry to upstream `https://bStats.org`.
- Do not mutate production services, deploy, publish, release, or submit upstream from this
  checklist.
- Do not modify Fabric worktrees or Fabric branches while completing this NeoForge checklist.
- Do not commit private receiver URLs, payload bodies, UUIDs, server names, or operator-specific
  paths unless they are intentionally scrubbed.

## Next Recommended Slice

The next NeoForge work should start with the backend and data-processor parity slices. Leave metrics
generated-output freshness and runtime smoke testing for a later integrated gate so the project
smoke-tests the full NeoForge path once.

# NeoForge Runtime Smoke Test Plan

This plan is for local or private validation of the NeoForge metrics class before release. It must
not deploy, publish, release, or send smoke-test telemetry to upstream `https://bStats.org`.

## Scope

Validate that a disposable NeoForge test mod can initialize the generated bStats Metrics class,
create the local bStats config, capture server lifecycle data, and submit one metrics payload to a
local or private receiver.

This plan does not verify Maven Central publishing, GitHub releases, production dashboards, or
upstream bStats ingestion.

## Inputs

- Source module: `neoforge/src/main/java/org/bstats/neoforge/Metrics.java`
- Generated single-file output: `neoforge/build/generated/Metrics.java`
- Gradle generation task: `generateMetrics`
- NeoForge compile check: `:neoforge:compileJava`
- Local/private receiver: an operator-controlled endpoint that accepts the NeoForge platform path.
  The committed default template is `https://bStats.org/api/v2/data/%s`, which resolves to
  `/api/v2/data/neoforge`. If the disposable copy is patched to a private template such as
  `/submitData/%s`, configure the receiver for the resulting `/submitData/neoforge` path.
- JDK for generation: JDK 17
- JDK for NeoForge compile/runtime work: JDK 25

Use local installation paths for those JDK versions when setting `JAVA_HOME`.

## Safety Rules

- Use a disposable NeoForge test mod or disposable runtime copy only.
- Do not modify committed bStats source to point at a private endpoint.
- Do not point any smoke-test payload at upstream `https://bStats.org`.
- Patch only the copied generated `Metrics.java` used by the disposable test mod.
- Change the copied generated package away from `org.bstats.neoforge`; the generated relocation guard
  intentionally rejects bStats-owned packages at runtime.
- Confirm the private receiver is reachable before enabling metrics in the test runtime.
- Keep `config/bStats/config.txt` under the disposable runtime directory, not a shared server.

## Preparation

From the active source worktree, generate and compile the NeoForge metrics artifact:

```powershell
$env:JAVA_HOME = '<path-to-jdk17>'
.\gradlew.bat generateMetrics

$env:JAVA_HOME = '<path-to-jdk25>'
.\gradlew.bat :neoforge:compileJava
```

Copy `neoforge/build/generated/Metrics.java` into a disposable NeoForge test mod. In that copy only:

- Change the package to the test mod's package, for example `dev.example.smoke.bstats`.
- Replace the generated bStats report URL with the private receiver URL pattern, keeping the `%s`
  platform placeholder so NeoForge resolves to the NeoForge path.
- If the private receiver is HTTP-only, patch the disposable copy's connection type from
  `HttpsURLConnection` to `HttpURLConnection`; do not make that transport change in committed
  bStats source.
- Ensure the private receiver decompresses gzip-encoded request bodies before logging or inspecting
  payload JSON. A receiver that only logs raw bytes can prove a POST arrived, but cannot prove the
  submitted JSON body.
- Instantiate the class from server-side mod initialization with the disposable test mod id and a
  dummy service id such as `99999`. Do not use a real production bStats service id in the
  disposable smoke test.

The private receiver must be able to log enough request data to prove a POST reached the NeoForge
metrics path. If the receiver only supports another platform path, add a local-only NeoForge handler
before running the smoke test.

## Pre-Runtime Readiness Checklist

Complete this checklist before starting any disposable NeoForge server. These steps prepare local or
private inputs only; they do not require a server run, deployment, publication, or upstream bStats
traffic.

- Confirm the private receiver target is local or private and is not `https://bStats.org`.
- Confirm the receiver accepts `POST` requests for the exact NeoForge path produced by the
  disposable URL pattern, for example `/submitData/neoforge`.
- Confirm the receiver decompresses gzip request bodies before logging payload JSON.
- Confirm the disposable test mod package name is not `org.bstats.neoforge`.
- Confirm the disposable generated copy keeps the platform placeholder in the report URL pattern,
  for example `http://private-receiver:18080/submitData/%s`.
- If using an HTTP-only receiver, confirm only the disposable generated copy changes
  `HttpsURLConnection` to `HttpURLConnection`.
- Confirm the disposable test mod uses a dummy service id, such as `99999`.
- Confirm the disposable runtime directory is isolated and has no shared
  `config/bStats/config.txt` from another server.
- Confirm `generateMetrics` passed with JDK 17 and produced
  `neoforge/build/generated/Metrics.java`.
- Confirm `:neoforge:compileJava` passed with JDK 25.

Do not proceed to runtime testing when any readiness item is missing. Fix the disposable test mod or
private receiver first, then rerun the readiness checklist.

## Runtime Steps

1. Start the private receiver and verify its health endpoint or equivalent local readiness check.
2. Start the disposable NeoForge server with the disposable test mod installed.
3. Verify the server log reports the NeoForge mod initialized and no bStats relocation error occurred.
4. Verify `config/bStats/config.txt` was created under the disposable runtime directory.
5. Keep `enabled=true` only while the private receiver endpoint is active and confirmed local/private.
6. Wait for the first metrics submission window, or use the smallest safe disposable runtime setup
   that reaches the scheduler path without changing committed bStats code.
7. Stop the server cleanly and confirm the bStats shutdown path does not log a warning.

## Evidence To Capture

- The generated source exists at `neoforge/build/generated/Metrics.java` after `generateMetrics`.
- `:neoforge:compileJava` passes with JDK 25.
- The disposable server log shows NeoForge server startup completed.
- The disposable server log shows no bStats relocation error, config creation failure, or lifecycle
  listener failure.
- The disposable runtime contains `config/bStats/config.txt` with `enabled=true` during the test.
- The private receiver log shows one POST to the NeoForge metrics path with a non-empty JSON body.
  Spot-check at least the `platform` and `service.id` fields to confirm the payload shape matches
  the disposable NeoForge test.
- The private receiver log shows the response status returned for the POST.
- The disposable server stops without bStats shutdown warnings.

## Still Unverified Until Runtime Approval

- Actual payload shape from a live NeoForge server.
- Whether the private receiver accepts the NeoForge path, HTTPS or disposable HTTP transport, and
  compressed request body used by the generated metrics class.
- Whether server lifecycle event registration captures player count and online mode during a real
  NeoForge run.
- Whether a shaded or copied Metrics class in a downstream mod behaves correctly after relocation.

## Evidence Template

Copy this template into a local-only note for the approved runtime run. Do not commit private server
names, payload bodies, UUIDs, or operator-specific paths unless they are intentionally scrubbed.

```text
NeoForge bStats Runtime Smoke Evidence

Source worktree:
Commit:
Disposable test mod package:
Disposable service id:
Private receiver base URL: <scrub before committing>
Resolved metrics path: <scrub before committing if it includes private host details>
Disposable runtime directory: <scrub before committing>

Pre-runtime checks:
- JDK 17 generateMetrics:
- Generated source exists:
- JDK 25 :neoforge:compileJava:
- Receiver health/readiness:
- Receiver accepts NeoForge metrics path:
- Receiver decompresses gzip body:
- Disposable copy package changed away from org.bstats.neoforge:
- Disposable copy report URL points only at local/private receiver:
- Disposable copy transport patch, if any:

Runtime evidence:
- NeoForge server startup completed:
- Disposable test mod initialized:
- No bStats relocation error:
- No config creation failure:
- No lifecycle listener failure:
- config/bStats/config.txt created under disposable runtime:
- enabled=true only while private receiver was active:
- Receiver POST path:
- Receiver response status:
- Payload platform field:
- Payload service.id field:
- Server stopped cleanly:
- No bStats shutdown warning:

Notes and follow-up:
```

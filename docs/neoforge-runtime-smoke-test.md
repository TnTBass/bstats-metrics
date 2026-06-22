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
- JDK for generation: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`
- JDK for NeoForge compile/runtime work: `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`

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
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat generateMetrics

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
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

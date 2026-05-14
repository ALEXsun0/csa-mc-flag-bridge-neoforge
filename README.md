# CSA Minecraft Flag Bridge

NeoForge 1.21.1 mods for delivering Ret2Shell dynamic flags through a shared Minecraft server.

This repository contains two separated modules:

- `server`: server-only Ret2Shell bridge. It exposes a small HTTP registration endpoint, binds `/csa bind <token>` to Minecraft players, enforces the bind gate, and returns a registered flag when the challenge condition is triggered.
- `terminal`: content-only terminal block mod. It contains the block/item assets and does not contain Ret2Shell server addresses, tokens, secrets, or deployment configuration.

## Build

Requirements:

- JDK 21
- Gradle 8.x

```bash
gradle :server:build :terminal:build
```

Artifacts:

```text
server/build/libs/csa_flag_bridge_server-<version>.jar
terminal/build/libs/csa_flag_terminal-<version>.jar
```

## Server Configuration

On first startup the server mod creates:

```text
config/csa_flag_bridge/config.json
```

Important fields:

```json
{
  "enableHttpServer": true,
  "httpHost": "127.0.0.1",
  "httpPort": 18080,
  "allowedRegistrationSourceCidrs": [],
  "registrationSecret": "<generated>",
  "maxPlayersPerToken": 5,
  "allowTokenRebind": false,
  "claimOncePerPlayer": true,
  "consumeTokenOnFirstClaim": false,
  "enableClaimCallback": true
}
```

Do not commit the generated `registrationSecret`. Pass the same secret to the Ret2Shell target/checker environment.

## Ret2Shell Registration API

The Ret2Shell target container registers a token-to-flag mapping by sending:

```http
POST /ret2shell/register
X-CSA-Secret: <registrationSecret>
Content-Type: application/json
```

```json
{
  "token": "team-token",
  "flag": "flag{...}",
  "team_id": "optional-team-id",
  "ttl_seconds": 86400,
  "callback_url": "http://target-pod:8080/claim",
  "callback_secret": "per-target-callback-secret"
}
```

If `enableClaimCallback` is enabled and callback fields are provided, the server posts the flag back to the target container page instead of requiring players to copy it from Minecraft chat.

## Player Flow

Players must bind the Ret2Shell token before playing:

```text
/csa bind <token>
```

Unbound players are held by the bind gate. `/csa unbind` removes the binding and keeps the player fixed at the current location until a new token is bound.

## Notes

- Keep `server` off client packs.
- The `terminal` module has no deployment-specific Ret2Shell configuration.
- This repo intentionally excludes generated builds, runtime state, local server configs, and packaged mod jars.

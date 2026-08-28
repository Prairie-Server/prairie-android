# Code-review kit (Silo / Prairie)

Portable Cursor **skill**, **subagent**, and **Bugbot** files for every Silo
ecosystem repository. Live copies for this Android checkout also sit at the
repo root (`.cursor/`, `.agents/skills/code-review/`, `.claude/…`).

## Layout

```text
shared/REVIEW_PROTOCOL.md          # severity + report format
scripts/apply-code-review-kit.sh   # copy a template into a repo checkout
templates/
  android/           # Silo-Server/silo-android
  apple/             # silo-apple
  server/            # silo-server
  plugin/            # first-party metadata/markers/autoscan/watchprovider plugins
  plugin-sdk/        # silo-plugin-sdk
  plugins-catalog/   # silo-plugins
  push-relay/        # silo-push-relay
  themes/            # silo-themes
  website/           # siloserver.org
  unraid/            # unraid-templates
  roku/              # Prairie-Server/prairie-roku
  smarttv/           # prairie-smarttv
```

Each template installs:

| Path | Purpose |
| --- | --- |
| `.cursor/agents/code-reviewer.md` | Readonly review subagent |
| `.claude/agents/code-reviewer.md` | Claude-compat copy |
| `.agents/skills/code-review/SKILL.md` | In-session / slash skill |
| `.claude/skills/code-review/SKILL.md` | Claude-compat copy |
| `.agents/skills/code-review/agents/openai.yaml` | Codex sidecar labels |
| `.cursor/BUGBOT.md` | Bugbot / Agent Review project rules |

## Apply to another checkout

```bash
.agents/code-review-kit/scripts/apply-code-review-kit.sh server /path/to/silo-server
.agents/code-review-kit/scripts/apply-code-review-kit.sh plugin /path/to/silo-plugin-metadata-tmdb
```

`prairie-android` already uses the Prairie-branded live files at the repo root;
use the `android` template for upstream `silo-android` (Silo package IDs /
exposure rules).

## Mapping

| Template | Repositories |
| --- | --- |
| `android` | `Silo-Server/silo-android` |
| `apple` | `Silo-Server/silo-apple` |
| `server` | `Silo-Server/silo-server` |
| `plugin` | `silo-plugin-metadata-*`, `silo-plugin-markers-theintrodb`, `silo-plugin-autoscan-arr`, `silo-plugin-watchprovider-floppy` |
| `plugin-sdk` | `silo-plugin-sdk` |
| `plugins-catalog` | `silo-plugins` |
| `push-relay` | `silo-push-relay` |
| `themes` | `silo-themes` |
| `website` | `siloserver.org` |
| `unraid` | `unraid-templates` |
| `roku` | `Prairie-Server/prairie-roku` |
| `smarttv` | `Prairie-Server/prairie-smarttv` |

Prairie forks that keep Prairie branding should adapt the Android/Apple/Server
templates’ package IDs and product-exposure notes (see this repo’s root
`.cursor/BUGBOT.md` for the Prairie Android variant).

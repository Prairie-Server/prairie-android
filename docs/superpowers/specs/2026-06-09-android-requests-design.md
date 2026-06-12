# Android Requests Design

## Goal

Bring the server media-request system to Android clients with a shared Kotlin contract, a full mobile user flow, mobile admin moderation, and a lightweight Android TV affordance. This is the first feature group in Approach A; subtitle acquisition, richer recommendations, calendar, watch providers/imports, and Watch Together follow after this request work is stable.

## Source Contract

The server source of truth is the current `/opt/silo-server` working tree on `root@silo-new`, especially:

- `internal/api/router.go`
- `internal/api/handlers/requests.go`
- `internal/requests/types.go`

User/profile-scoped routes:

- `GET /api/v1/requests/status`
- `GET /api/v1/requests/search?q=&media_type=&page=`
- `GET /api/v1/requests/discover`
- `GET /api/v1/requests/discover/{section}?page=`
- `GET /api/v1/requests/discover/studios`
- `GET /api/v1/requests/discover/networks`
- `GET /api/v1/requests/discover/genres`
- `GET /api/v1/requests/discover/browse/studio/{slug}?sort=&page=`
- `GET /api/v1/requests/discover/browse/network/{slug}?sort=&page=`
- `GET /api/v1/requests/discover/browse/genre/{slug}?media_type=&sort=&page=`
- `GET /api/v1/requests/detail/{media_type}/{tmdb_id}`
- `POST /api/v1/requests/`
- `GET /api/v1/requests/mine?status=&outcome=&limit=&offset=`
- `GET /api/v1/requests/{id}`
- `POST /api/v1/requests/{id}/cancel`

Admin routes:

- `GET /api/v1/admin/requests?status=&outcome=&limit=&offset=`
- `POST /api/v1/admin/requests/{id}/approve`
- `POST /api/v1/admin/requests/{id}/decline`
- `POST /api/v1/admin/requests/{id}/cancel`
- `POST /api/v1/admin/requests/{id}/retry`
- `GET /api/v1/admin/request-settings`
- `PUT /api/v1/admin/request-settings`
- `GET /api/v1/admin/request-users/{user_id}/limit`
- `PUT /api/v1/admin/request-users/{user_id}/limit`
- `GET /api/v1/admin/request-integrations`
- `POST /api/v1/admin/request-integrations`
- `PUT /api/v1/admin/request-integrations/{id}`
- `DELETE /api/v1/admin/request-integrations/{id}`
- `POST /api/v1/admin/request-integrations/{id}/options`

Core wire concepts:

- Media types: `movie`, `series`, `all`.
- Request statuses: `pending`, `approved`, `queued`, `downloading`, `completed`.
- Target-only status: `failed`.
- Outcomes: `active`, `declined`, `cancelled`, `failed`.
- Availability: `missing`, `available`.
- Search/discover/detail responses include a `request` state with `requestable`, `reason`, `status`, and `request_id`.
- Requests can include fulfillment `targets` for quality, integration, external status, and per-target errors.

## Product Shape

### Mobile User Flow

Mobile gets the full request experience:

1. A Requests entry reachable from the main app. It should be a first-class screen but not necessarily a permanent bottom-tab item on day one. To avoid crowding the existing Home/Libraries/For You/Downloads pattern, add it to the main header/personal/actions area first, then promote to bottom nav only if usage proves it deserves a slot.
2. A discovery page using `/requests/discover`, showing server-provided sections and request-state badges.
3. Search with movie/series/all filtering via `/requests/search`.
4. Detail page via `/requests/detail/{media_type}/{tmdb_id}` with poster/backdrop, metadata, cast, recommendations, availability, and request CTA.
5. Create request from the detail/search card only when `request.requestable` is true.
6. Mine queue via `/requests/mine`, with status/outcome chips, target details, and cancel for pending user-owned requests that the server still allows.
7. Existing library availability should route available items to the existing item detail screen using `library_content_id`.

### Mobile Admin Flow

Mobile admin gets moderation first, configuration second:

1. Admin request queue listing active, pending, failed, declined, cancelled, and completed requests.
2. Admin request detail with targets, requester, integration status, and errors.
3. Actions: approve, decline with optional reason, cancel with optional reason, retry failed.
4. Admin settings and integration management are planned after moderation. They are powerful but more form-heavy; shipping them after the core queue avoids blocking the user-facing feature.

### Android TV Flow

TV gets request affordances, not admin-heavy management:

1. If search/discover is added to TV, show requestable titles as browse cards.
2. In existing TV item/detail surfaces, if a title is missing but discover/detail data exists, offer `Request`.
3. Include a simple `My Requests` screen/list with status.
4. Hide admin request configuration on TV. Admin moderation can stay mobile-only for the first pass.

## Architecture

### Shared Layer

Create a request feature slice in `shared/commonMain`:

- `model/request/RequestModels.kt` for serializable wire models.
- `network/api/RequestsApi.kt` for user request routes.
- `network/api/AdminRequestsApi.kt` or admin request methods grouped separately from the existing broad `AdminApi`; prefer a new class if the method count gets large.
- `repository/RequestsRepository.kt` for user flows and local StateFlows.
- `repository/AdminRequestsRepository.kt` for admin moderation.
- `viewmodel/RequestsViewModel.kt`, `RequestSearchViewModel.kt`, `RequestDetailViewModel.kt`, `MyRequestsViewModel.kt`, and `AdminRequestsViewModel.kt` as shared state holders where practical.

Keep models close to server JSON names using `@SerialName`. Use nullable fields for optional server data and default empty lists for arrays. Do not create local enum deserializers that crash on unknown server values; string constants or tolerant enum wrappers are safer while the server request system is still moving.

### Mobile UI

Create mobile screens under `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests`:

- `RequestsScreen.kt` for discover/search tabs and navigation into detail/mine.
- `RequestDetailScreen.kt` for TMDB detail and CTA.
- `MyRequestsScreen.kt` for the user's queue.
- `AdminRequestsScreen.kt` and `AdminRequestDetailScreen.kt` for moderation.
- Small repeated components: `RequestPosterCard`, `RequestStatusBadge`, `RequestActionButton`, `RequestFilterChips`.

Navigation adds routes for requests root, request detail by `mediaType/tmdbId`, mine queue, admin queue, and admin request detail by request id. UI should reuse existing dark Material 3 styling and avoid introducing a new visual system.

### TV UI

Reuse shared models/repositories. TV-specific screens live under `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests`. The TV implementation should be D-pad first: large cards, simple actions, no complex text-entry workflows beyond search if already supported locally.

## Error Handling

- `/requests/status` returning disabled should hide request CTAs and show a quiet disabled state on the Requests screen.
- `401` or missing profile should route through the existing login/profile gates.
- `403` on admin routes means hide admin actions and show a permissions message.
- `409` and `400` from create/action routes should surface the server message, then refresh detail/list state.
- `404` on request detail should remove the stale local row if present and return to the queue.
- Network errors should leave current cached state visible when available and show retry affordances.

## Testing

Use TDD per slice:

- Shared model serialization tests for representative server payloads.
- API/repository tests with fake APIs for discover/search/detail/create/mine/cancel and admin actions.
- ViewModel tests for state transitions, especially create success, already requested, disabled requests, admin approve/decline/retry, and refresh-after-action.
- Compile checks for mobile and TV.

Primary verification command:

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

## Rollout

1. Shared request contract and repository.
2. Mobile user request flow.
3. Mobile admin moderation queue.
4. TV request affordance and My Requests.
5. Admin settings/integrations.
6. Next Approach A feature group: subtitle acquisition.

Each rollout stage should end in a commit and pass the verification command before moving on.

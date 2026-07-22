# Audiobook Coherent Variant Fallback Design

## Goal

Complete Android PR #75 so audiobook timelines never stitch alternate encodings as sequential or mixed logical parts, while preserving correct offline resume mapping for a downloaded file.

## Variant Selection

Group playable, indexed audiobook files by `presentationPartIndex`. A presentation variant is complete only when its `presentationGroupKey` is represented in every indexed logical part.

Selection follows this order:

1. Use the preferred file's presentation variant when that variant is complete.
2. Otherwise use the first complete presentation variant in deterministic candidate order.
3. If no complete variant exists, retain the existing deterministic per-part fallback so malformed or legacy catalog data remains playable.

This preserves the selected variant when it can represent the entire book and prevents an incomplete preferred variant from causing an avoidable mixture when a complete alternative exists.

## Offline Resume Mapping

When rebuilding a cached timeline for offline-only playback, pass the downloaded media file ID as `preferredFileId`. This lets the timeline include the downloaded variant when it is complete and allows the existing whole-book-to-part-local resume conversion to find the matching track.

No changes are made to persistence, download selection, server APIs, or online playback session behavior.

## Tests

Add focused regression coverage for:

- an incomplete preferred variant falling back to another variant that covers every part;
- the offline cached-timeline call passing the downloaded file ID as the preference.

The existing complete-preferred-variant and no-preference coherent-fallback tests remain authoritative for normal selection.

## Error Handling

Incomplete catalog metadata does not become a new playback error. If no presentation variant spans every logical part, selection falls back deterministically to one playable file per part, matching the current tolerance for legacy data.

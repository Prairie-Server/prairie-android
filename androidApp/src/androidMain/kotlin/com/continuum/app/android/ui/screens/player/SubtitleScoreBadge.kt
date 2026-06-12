package com.continuum.app.android.ui.screens.player

/**
 * Web-parity score buckets for subtitle search results
 * (`web/src/player/components/SubtitleSearchModal.tsx` scoreColor):
 * ≥70 High (green #22c55e), ≥40 Medium (amber #eab308), else Low (red #ef4444).
 * Pure so it's unit-testable; the composable maps buckets to colors.
 */
enum class ScoreBadgeBucket { High, Medium, Low }

internal fun scoreBadgeBucket(score: Int): ScoreBadgeBucket = when {
    score >= 70 -> ScoreBadgeBucket.High
    score >= 40 -> ScoreBadgeBucket.Medium
    else -> ScoreBadgeBucket.Low
}

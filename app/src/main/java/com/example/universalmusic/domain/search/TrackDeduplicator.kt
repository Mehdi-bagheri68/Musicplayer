package com.example.universalmusic.domain.search

import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.TrackSourceGroup
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * Conservative deduplicator:
 * 1. Exact ISRC match if both tracks have a valid ISRC.
 * 2. Otherwise normalized title + artist + duration (<= 3s), never across remix/live/cover/etc.
 *    or differing explicit flags.
 * All provider variants are preserved inside TrackSourceGroup.
 */
class TrackDeduplicator @Inject constructor() {

    private val variantKeywords = listOf(
        "remix", "live", "cover", "instrumental", "karaoke",
        "acoustic", "radio edit", "extended", "club mix", "demo",
        "ریمیکس", "زنده", "کاور", "بی کلام", "بی‌کلام"
    )

    fun groupDuplicates(tracks: List<MusicTrack>): List<TrackSourceGroup> {
        if (tracks.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<MusicTrack>>()
        for (candidate in tracks) {
            val match = groups.firstOrNull { isConservativeMatch(it.first(), candidate) }
            if (match != null) {
                if (match.none { it.id == candidate.id }) match.add(candidate)
            } else {
                groups.add(mutableListOf(candidate))
            }
        }
        return groups.map { members ->
            val sorted = members.sortedByDescending { availabilityPriority(it.playbackAvailability) }
            TrackSourceGroup(primaryTrack = sorted.first(), alternatives = sorted.drop(1))
        }
    }

    internal fun isConservativeMatch(a: MusicTrack, b: MusicTrack): Boolean {
        if (a.id == b.id) return true

        val isrcA = a.isrc?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length >= 10 }
        val isrcB = b.isrc?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length >= 10 }
        if (isrcA != null && isrcB != null) return isrcA == isrcB

        if (a.explicit != null && b.explicit != null && a.explicit != b.explicit) return false
        if (extractVariantMarkers(a.title) != extractVariantMarkers(b.title)) return false

        val titleA = normalizeText(a.title)
        if (titleA.isEmpty() || titleA != normalizeText(b.title)) return false

        val artistA = normalizeText(a.artistName.orEmpty())
        if (artistA.isEmpty() || artistA != normalizeText(b.artistName.orEmpty())) return false

        val durationA = a.durationMs
        val durationB = b.durationMs
        if (durationA == null || durationB == null || durationA <= 0L || durationB <= 0L) return false
        return abs(durationA - durationB) <= DURATION_TOLERANCE_MS
    }

    private fun extractVariantMarkers(title: String): Set<String> {
        val lower = title.lowercase(Locale.ROOT)
        return variantKeywords.filter { lower.contains(it) }.toSet()
    }

    private fun normalizeText(input: String): String = input
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200C', ' ')
        .replace(NON_ALPHANUMERIC_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun availabilityPriority(a: PlaybackAvailability): Int = when (a) {
        PlaybackAvailability.FULL_STREAM -> 5
        PlaybackAvailability.PREVIEW -> 4
        PlaybackAvailability.OFFICIAL_PLAYER -> 3
        PlaybackAvailability.EXTERNAL_ONLY -> 2
        PlaybackAvailability.UNAVAILABLE -> 1
    }

    companion object {
        private const val DURATION_TOLERANCE_MS = 3_000L
        private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

package org.prairieserver.prairie.common.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DolbyVisionColorInfoExtractorsFactoryTest {
    @Test
    fun transformTagCarriesExpectedColorRange() {
        val tag = PrairieMediaTransformTag(
            dolbyVisionMode = DolbyVisionTransformMode.DISABLED,
            expectedDynamicRange = "hlg",
            expectedColorRange = "pc",
        )

        assertEquals("pc", tag.expectedColorRange)
    }

    @Test
    fun extractorAppliesValidatedRangeFallbackToVideoOutput() {
        val repaired = extractFormat(
            transformMode = DolbyVisionTransformMode.DISABLED,
            expectedColorRange = "pc",
            source = Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .build(),
        )

        assertEquals(C.COLOR_RANGE_FULL, repaired.colorInfo?.colorRange)
    }

    @Test
    fun extractorPreservesFullRangeWhileRepairingMissingHlgColorInfo() {
        val repaired = extractFormat(
            transformMode = DolbyVisionTransformMode.DISABLED,
            expectedDynamicRange = "hlg",
            expectedColorRange = "pc",
            source = Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .build(),
        )

        assertEquals(C.COLOR_SPACE_BT2020, repaired.colorInfo?.colorSpace)
        assertEquals(C.COLOR_TRANSFER_HLG, repaired.colorInfo?.colorTransfer)
        assertEquals(C.COLOR_RANGE_FULL, repaired.colorInfo?.colorRange)
    }

    @Test
    fun transformedDolbyVisionOutputRangeOverridesConflictingSourceFallback() {
        val repaired = extractFormat(
            transformMode = DolbyVisionTransformMode.PROFILE7_TO_PROFILE81,
            expectedColorRange = "pc",
            source = Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvhe.07.06")
                .build(),
        )

        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
    }

    @Test
    fun suppliesLimitedRangeFromValidatedTvFallback() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build()

        val repaired = source.withValidatedColorRange("tv")

        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
    }

    @Test
    fun suppliesFullRangeFromValidatedPcFallback() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build()

        val repaired = source.withValidatedColorRange("pc")

        assertEquals(C.COLOR_RANGE_FULL, repaired.colorInfo?.colorRange)
    }

    @Test
    fun ignoresUnknownColorRangeFallback() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build()

        assertSame(source, source.withValidatedColorRange(null))
        assertSame(source, source.withValidatedColorRange("unknown"))
    }

    @Test
    fun preservesExplicitContainerColorRange() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .build(),
            )
            .build()

        assertSame(source, source.withValidatedColorRange("tv"))
    }

    @Test
    fun doesNotApplyColorRangeFallbackToNonVideoFormats() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .build()

        assertSame(source, source.withValidatedColorRange("pc"))
    }

    @Test
    fun suppliesBt2020PqColorInfoBeforeDolbyVisionDecoderInitialization() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.08.06")
            .build()

        val repaired = source.withDolbyVisionHdrColorInfo()

        assertEquals(C.COLOR_SPACE_BT2020, repaired.colorInfo?.colorSpace)
        assertEquals(C.COLOR_TRANSFER_ST2084, repaired.colorInfo?.colorTransfer)
        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
        assertEquals(25, repaired.colorInfo?.hdrStaticInfo?.size)
        assertEquals(0, repaired.colorInfo?.hdrStaticInfo?.first()?.toInt())
    }

    @Test
    fun leavesNonDolbyVisionFormatsUntouched() {
        val source = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()

        val result = source.withDolbyVisionHdrColorInfo()

        assertSame(source, result)
        assertNull(result.colorInfo)
    }

    @Test
    fun completesMissingRangeForBt2020PqDolbyVision() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build(),
            )
            .build()

        val repaired = source.withDolbyVisionHdrColorInfo()

        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
    }

    @Test
    fun preservesKnownHlgBaseLayerSignaling() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .build(),
            )
            .build()

        assertSame(source, source.withDolbyVisionHdrColorInfo())
    }

    @Test
    fun suppliesHlgColorInfoFromValidatedRecipeWhenContainerMetadataIsMissing() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build()

        val repaired = source.withValidatedDynamicRangeColorInfo("hlg")

        assertEquals(C.COLOR_SPACE_BT2020, repaired.colorInfo?.colorSpace)
        assertEquals(C.COLOR_TRANSFER_HLG, repaired.colorInfo?.colorTransfer)
        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
        assertNull(repaired.colorInfo?.hdrStaticInfo)
    }

    @Test
    fun suppliesMissingHlgFieldsWithoutOverwritingExplicitFullRange() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .build(),
            )
            .build()

        val repaired = source.withValidatedDynamicRangeColorInfo("hlg")

        assertEquals(C.COLOR_SPACE_BT2020, repaired.colorInfo?.colorSpace)
        assertEquals(C.COLOR_TRANSFER_HLG, repaired.colorInfo?.colorTransfer)
        assertEquals(C.COLOR_RANGE_FULL, repaired.colorInfo?.colorRange)
    }

    @Test
    fun doesNotOverrideConflictingContainerColorInfoWithHlgRecipe() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .build(),
            )
            .build()

        assertSame(source, source.withValidatedDynamicRangeColorInfo("hlg"))
    }

    @Test
    fun doesNotInventHlgWithoutValidatedRecipe() {
        val source = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()

        assertSame(source, source.withValidatedDynamicRangeColorInfo(null))
        assertSame(source, source.withValidatedDynamicRangeColorInfo("hdr10"))
    }

    @Test
    fun keepsDolbyVisionOutputRangeAuthoritativeOverSourceFallback() {
        val source = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .build()

        val repaired = source
            .withDolbyVisionHdrColorInfo()
            .withValidatedColorRange("pc")

        assertEquals(C.COLOR_RANGE_LIMITED, repaired.colorInfo?.colorRange)
    }

    private fun extractFormat(
        transformMode: DolbyVisionTransformMode,
        expectedDynamicRange: String? = null,
        expectedColorRange: String,
        source: Format,
    ): Format {
        val sourceExtractor = RecordingExtractor()
        val extractor = DolbyVisionColorInfoExtractorsFactory(
            delegate = ExtractorsFactory { arrayOf(sourceExtractor) },
            transformMode = transformMode,
            converter = DolbyVisionRpuConverter { it },
            expectedDynamicRange = expectedDynamicRange,
            expectedColorRange = expectedColorRange,
        ).createExtractors().single()
        val output = RecordingExtractorOutput()
        extractor.init(output)

        sourceExtractor.emitVideoFormat(source)

        return checkNotNull(output.trackOutput.format)
    }

    private class RecordingExtractor : Extractor {
        private lateinit var output: ExtractorOutput

        override fun sniff(input: ExtractorInput): Boolean = true

        override fun init(output: ExtractorOutput) {
            this.output = output
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int =
            Extractor.RESULT_END_OF_INPUT

        override fun seek(position: Long, timeUs: Long) = Unit

        override fun release() = Unit

        fun emitVideoFormat(format: Format) {
            output.track(0, C.TRACK_TYPE_VIDEO).format(format)
        }
    }

    private class RecordingExtractorOutput : ExtractorOutput {
        val trackOutput = RecordingTrackOutput()

        override fun track(id: Int, type: Int): TrackOutput = trackOutput

        override fun endTracks() = Unit

        override fun seekMap(seekMap: SeekMap) = Unit
    }

    private class RecordingTrackOutput : TrackOutput {
        var format: Format? = null

        override fun format(format: Format) {
            this.format = format
        }

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int,
        ): Int = error("Unexpected sample data")

        override fun sampleData(
            data: ParsableByteArray,
            length: Int,
            sampleDataPart: Int,
        ) = error("Unexpected sample data")

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?,
        ) = error("Unexpected sample metadata")
    }
}

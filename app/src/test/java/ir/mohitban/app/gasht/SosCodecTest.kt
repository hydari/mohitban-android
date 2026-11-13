package ir.mohitban.app.gasht

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosCodecTest {
    private val sentAt = 1_700_000_000_000L
    private val fix = Fix(GeoPoint(37.50000, 47.10000), 12f, null, null, sentAt)
    private val code = "tabriz-team-2026"

    private fun signedLine(team: String = code, note: String = "کمک") =
        SosCodec.line(SosKind.HELP, "علی رضایی", "محیط‌بان سوم", "پاسگاه مرکزی", fix, null, note, team)

    @Test fun unsignedMessageIsAcceptedWhenTheReceiverHasNoTeamCode() {
        val line = SosCodec.line(SosKind.HELP, "علی", "محیط‌بان", "پاسگاه", fix, null, "")
        val a = SosCodec.decode("متن\n$line", "09120000000", sentAt + 1000)
        assertNotNull(a)
        assertTrue(a!!.verified)
        assertEquals(37.5, a.point.lat, 1e-9)
        assertEquals(47.1, a.point.lon, 1e-9)
    }

    @Test fun signedMessageIsVerifiedWithTheSameCode() {
        val a = SosCodec.decode("متن\n" + signedLine(), "0912", sentAt + 60_000, code)
        assertNotNull(a)
        assertTrue(a!!.verified)
        assertEquals("علی رضایی", a.name)
        assertEquals("کمک", a.note)
        assertEquals(SosKind.HELP, a.kind)
    }

    @Test fun wrongTeamCodeIsNotVerified() {
        val a = SosCodec.decode(signedLine(), "0912", sentAt + 1000, "another-code-123")
        assertNotNull(a)
        assertFalse(a!!.verified)
    }

    @Test fun messageWithoutSignatureIsNotVerifiedWhenTheReceiverHasACode() {
        val line = SosCodec.line(SosKind.HELP, "علی", "محیط‌بان", "پاسگاه", fix, null, "")
        assertFalse(SosCodec.decode(line, "0912", sentAt + 1000, code)!!.verified)
    }

    @Test fun changedCoordinatesBreakTheSignature() {
        val forged = signedLine().replace("37.50000", "37.60000")
        assertFalse(SosCodec.decode(forged, "0912", sentAt + 1000, code)!!.verified)
    }

    @Test fun changedNoteBreaksTheSignature() {
        val forged = signedLine(note = "کمک").replace("کمک", "همه‌چیز خوب است")
        assertFalse(SosCodec.decode(forged, "0912", sentAt + 1000, code)!!.verified)
    }

    @Test fun oldSignedMessageIsRefusedAsAReplay() {
        val threeDays = 3 * 24 * 3_600_000L
        assertFalse(SosCodec.decode(signedLine(), "0912", sentAt + threeDays, code)!!.verified)
    }

    @Test fun signatureCaseDoesNotMatter() {
        val line = signedLine()
        val sig = line.substringAfterLast('|')
        val upper = line.removeSuffix(sig) + sig.uppercase()
        assertTrue(SosCodec.decode(upper, "0912", sentAt + 1000, code)!!.verified)
    }

    @Test fun signAddsTwelveHexCharactersAndIsStable() {
        val s1 = SosCodec.sign("MHY2|0|37.5|47.1", code)
        val s2 = SosCodec.sign("MHY2|0|37.5|47.1", code)
        assertEquals(12, s1.length)
        assertTrue(s1.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(s1, s2)
        assertTrue(s1 != SosCodec.sign("MHY2|0|37.5|47.2", code))
    }

    @Test fun oldFormatStillReadsWithoutACode() {
        val a = SosCodec.decode("MHY1|37.5|47.1|10|1700000000|علی|محیط‌بان|پاسگاه", "0912", sentAt)
        assertNotNull(a)
        assertEquals(SosKind.HELP, a!!.kind)
        assertEquals("علی", a.name)
    }

    @Test fun oldFormatIsNotVerifiedWhenTheReceiverHasACode() {
        val a = SosCodec.decode("MHY1|37.5|47.1|10|1700000000|علی", "0912", sentAt, code)
        assertFalse(a!!.verified)
    }

    @Test fun badCoordinatesAreRefused() {
        assertNull(SosCodec.decode("MHY2|0|95.0|47.1|10|1700000000|علی|g|s|||", "0912", sentAt))
        assertNull(SosCodec.decode("hello", "0912", sentAt))
    }

    @Test fun pipeInATextFieldCannotInjectFields() {
        val line = SosCodec.line(SosKind.POACHER, "علی|حمله", "g", "s", fix, GeoPoint(37.6, 47.2), "a|b", code)
        val a = SosCodec.decode(line, "0912", sentAt + 1000, code)!!
        assertTrue(a.verified)
        assertFalse(a.name.contains('|'))
        assertEquals(SosKind.POACHER, a.kind)
        assertEquals(37.6, a.target!!.lat, 1e-9)
    }
}

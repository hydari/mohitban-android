package ir.mohitban.app.core

import ir.mohitban.app.core.text.Validators
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test fun nationalId_valid() {
        assertNull(Validators.nationalId("0499370899"))
    }

    @Test fun nationalId_acceptsPersianDigits() {
        assertNull(Validators.nationalId("۰۴۹۹۳۷۰۸۹۹"))
    }

    @Test fun nationalId_wrongCheckDigit() {
        assertNotNull(Validators.nationalId("0499370898"))
    }

    @Test fun nationalId_sameDigitsRefused() {
        assertNotNull(Validators.nationalId("1111111111"))
    }

    @Test fun nationalId_wrongLengthOrEmpty() {
        assertNotNull(Validators.nationalId("12345"))
        assertNotNull(Validators.nationalId(""))
    }

    @Test fun mobile() {
        assertNull(Validators.mobile("09123456789"))
        assertNull(Validators.mobile("۰۹۱۲۳۴۵۶۷۸۹"))
        assertNotNull(Validators.mobile("9123456789"))
        assertNotNull(Validators.mobile("0812345678"))
        assertNotNull(Validators.mobile(""))
    }

    @Test fun personName() {
        assertNull(Validators.personName("علی رضایی", "نام"))
        assertNotNull(Validators.personName("", "نام"))
        assertNotNull(Validators.personName("ع", "نام"))
        assertNotNull(Validators.personName("Ali123", "نام"))
    }
}

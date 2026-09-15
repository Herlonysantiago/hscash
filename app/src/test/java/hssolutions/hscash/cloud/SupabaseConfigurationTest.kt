package hssolutions.hscash.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigurationTest {
    @Test
    fun emptyConfigurationKeepsCloudDisabled() {
        assertFalse(SupabaseConfiguration("", "").isValid)
    }

    @Test
    fun placeholderKeyIsRejected() {
        assertFalse(
            SupabaseConfiguration(
                "https://project.supabase.co",
                "YOUR_SUPABASE_PUBLISHABLE_KEY"
            ).isValid
        )
    }

    @Test
    fun validProjectConfigurationEnablesCloud() {
        assertTrue(
            SupabaseConfiguration(
                "https://project.supabase.co",
                "sb_publishable_example"
            ).isValid
        )
    }
}

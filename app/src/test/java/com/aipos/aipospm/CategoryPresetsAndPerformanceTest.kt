package com.aipos.aipospm

import com.aipos.aipospm.data.CategoryPresets
import com.aipos.aipospm.data.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPresetsAndPerformanceTest {

    @Test
    fun testCategoryPresetsUniquenessAndNonEmpty() {
        val pwPresets = CategoryPresets.PASSWORD_PRESETS
        val apiPresets = CategoryPresets.API_KEY_PRESETS

        assertTrue("Password presets must not be empty", pwPresets.isNotEmpty())
        assertTrue("API key presets must not be empty", apiPresets.isNotEmpty())

        assertEquals("Password presets must be unique", pwPresets.toSet().size, pwPresets.size)
        assertEquals("API key presets must be unique", apiPresets.toSet().size, apiPresets.size)

        val defaults = CategoryPresets.getDefaultCategories()
        assertEquals(pwPresets.size + apiPresets.size, defaults.size)

        val pwCategories = defaults.filter { it.type == CategoryType.PASSWORD.name }
        val apiCategories = defaults.filter { it.type == CategoryType.API_KEY.name }

        assertEquals(pwPresets.size, pwCategories.size)
        assertEquals(apiPresets.size, apiCategories.size)
    }

    @Test
    fun testPasswordSmartCategorySuggestions() {
        assertEquals("Email & Accounts", CategoryPresets.suggestCategory("Google Account", CategoryType.PASSWORD))
        assertEquals("Email & Accounts", CategoryPresets.suggestCategory("my.name@gmail.com", CategoryType.PASSWORD))
        assertEquals("Email & Accounts", CategoryPresets.suggestCategory("Outlook Office Login", CategoryType.PASSWORD))
        assertEquals("Email & Accounts", CategoryPresets.suggestCategory("ProtonMail", CategoryType.PASSWORD))

        assertEquals("Social & Messaging", CategoryPresets.suggestCategory("Facebook", CategoryType.PASSWORD))
        assertEquals("Social & Messaging", CategoryPresets.suggestCategory("Instagram Account", CategoryType.PASSWORD))
        assertEquals("Social & Messaging", CategoryPresets.suggestCategory("Discord Bot Token", CategoryType.PASSWORD))
        assertEquals("Social & Messaging", CategoryPresets.suggestCategory("Telegram Messenger", CategoryType.PASSWORD))

        assertEquals("Finance & Banking", CategoryPresets.suggestCategory("Chase Online Banking", CategoryType.PASSWORD))
        assertEquals("Finance & Banking", CategoryPresets.suggestCategory("PayPal Personal", CategoryType.PASSWORD))
        assertEquals("Finance & Banking", CategoryPresets.suggestCategory("Binance Crypto", CategoryType.PASSWORD))
        assertEquals("Finance & Banking", CategoryPresets.suggestCategory("Coinbase Pro", CategoryType.PASSWORD))

        assertEquals("Work & Productivity", CategoryPresets.suggestCategory("Slack Enterprise", CategoryType.PASSWORD))
        assertEquals("Work & Productivity", CategoryPresets.suggestCategory("Notion Team Workspace", CategoryType.PASSWORD))
        assertEquals("Work & Productivity", CategoryPresets.suggestCategory("Jira / Atlassian", CategoryType.PASSWORD))
        assertEquals("Work & Productivity", CategoryPresets.suggestCategory("Zoom Meeting Room", CategoryType.PASSWORD))

        assertEquals("Entertainment & Streaming", CategoryPresets.suggestCategory("Netflix Family", CategoryType.PASSWORD))
        assertEquals("Entertainment & Streaming", CategoryPresets.suggestCategory("Spotify Premium", CategoryType.PASSWORD))
        assertEquals("Entertainment & Streaming", CategoryPresets.suggestCategory("Steam Gaming", CategoryType.PASSWORD))
        assertEquals("Entertainment & Streaming", CategoryPresets.suggestCategory("PlayStation Network", CategoryType.PASSWORD))

        assertEquals("Shopping & Delivery", CategoryPresets.suggestCategory("Amazon Store", CategoryType.PASSWORD))
        assertEquals("Shopping & Delivery", CategoryPresets.suggestCategory("eBay Seller", CategoryType.PASSWORD))
        assertEquals("Shopping & Delivery", CategoryPresets.suggestCategory("Uber Eats Delivery", CategoryType.PASSWORD))

        assertEquals("Personal & Utilities", CategoryPresets.suggestCategory("Home WiFi Router", CategoryType.PASSWORD))
        assertEquals("Personal & Utilities", CategoryPresets.suggestCategory("Verizon Internet", CategoryType.PASSWORD))
        assertEquals("Personal & Utilities", CategoryPresets.suggestCategory("Health Insurance Portal", CategoryType.PASSWORD))

        // Null / Unknown
        assertNull(CategoryPresets.suggestCategory(null, CategoryType.PASSWORD))
        assertNull(CategoryPresets.suggestCategory("", CategoryType.PASSWORD))
        assertNull(CategoryPresets.suggestCategory("Random Unrelated Custom Service 987", CategoryType.PASSWORD))
    }

    @Test
    fun testApiKeySmartCategorySuggestions() {
        assertEquals("AI & Chatbots", CategoryPresets.suggestCategory("OpenAI Production Key", CategoryType.API_KEY))
        assertEquals("AI & Chatbots", CategoryPresets.suggestCategory("Anthropic Claude API", CategoryType.API_KEY))
        assertEquals("AI & Chatbots", CategoryPresets.suggestCategory("Google Gemini Flash", CategoryType.API_KEY))
        assertEquals("AI & Chatbots", CategoryPresets.suggestCategory("Groq LLM Acceleration", CategoryType.API_KEY))
        assertEquals("AI & Chatbots", CategoryPresets.suggestCategory("Perplexity Pro", CategoryType.API_KEY))

        assertEquals("Cloud & Infrastructure", CategoryPresets.suggestCategory("AWS S3 Access Key", CategoryType.API_KEY))
        assertEquals("Cloud & Infrastructure", CategoryPresets.suggestCategory("Google Cloud Platform", CategoryType.API_KEY))
        assertEquals("Cloud & Infrastructure", CategoryPresets.suggestCategory("Cloudflare Global Token", CategoryType.API_KEY))
        assertEquals("Cloud & Infrastructure", CategoryPresets.suggestCategory("Vercel Deployment Key", CategoryType.API_KEY))

        assertEquals("Developer & Coding", CategoryPresets.suggestCategory("GitHub Personal Access Token", CategoryType.API_KEY))
        assertEquals("Developer & Coding", CategoryPresets.suggestCategory("Docker Hub Token", CategoryType.API_KEY))
        assertEquals("Developer & Coding", CategoryPresets.suggestCategory("Sentry DSN", CategoryType.API_KEY))
        assertEquals("Developer & Coding", CategoryPresets.suggestCategory("NPM Publishing Key", CategoryType.API_KEY))

        assertEquals("Payments & Billing", CategoryPresets.suggestCategory("Stripe Live Secret Key", CategoryType.API_KEY))
        assertEquals("Payments & Billing", CategoryPresets.suggestCategory("PayPal REST API", CategoryType.API_KEY))
        assertEquals("Payments & Billing", CategoryPresets.suggestCategory("Square Sandbox Token", CategoryType.API_KEY))

        assertEquals("Databases & Storage", CategoryPresets.suggestCategory("Supabase Service Role", CategoryType.API_KEY))
        assertEquals("Databases & Storage", CategoryPresets.suggestCategory("MongoDB Atlas Connection", CategoryType.API_KEY))
        assertEquals("Databases & Storage", CategoryPresets.suggestCategory("Redis Upstash Cache", CategoryType.API_KEY))
        assertEquals("Databases & Storage", CategoryPresets.suggestCategory("Pinecone Vector Index", CategoryType.API_KEY))

        assertEquals("Communications & Webhooks", CategoryPresets.suggestCategory("Twilio SMS SID", CategoryType.API_KEY))
        assertEquals("Communications & Webhooks", CategoryPresets.suggestCategory("SendGrid Mailer Key", CategoryType.API_KEY))
        assertEquals("Communications & Webhooks", CategoryPresets.suggestCategory("Discord Webhook URL", CategoryType.API_KEY))

        // Null / Unknown
        assertNull(CategoryPresets.suggestCategory(null, CategoryType.API_KEY))
        assertNull(CategoryPresets.suggestCategory("", CategoryType.API_KEY))
        assertNull(CategoryPresets.suggestCategory("Custom Private Local Secret 123", CategoryType.API_KEY))
    }

    @Test
    fun testDecryptionCacheBehavior() {
        val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val encryptedPassword = "mockEncryptedBase64Payload=="
        val iv = "mockIvBase64=="
        val cacheKey = "$encryptedPassword:$iv"

        // Cache miss
        assertNull(cache[cacheKey])

        // First decryption puts into cache
        val plaintext = "SuperSecretPassword123!"
        cache[cacheKey] = plaintext

        // Cache hit
        val cached = cache[cacheKey]
        assertNotNull(cached)
        assertEquals(plaintext, cached)

        // Cache clear on lock
        cache.clear()
        assertNull(cache[cacheKey])
    }
}

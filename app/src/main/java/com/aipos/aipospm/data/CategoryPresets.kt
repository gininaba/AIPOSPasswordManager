package com.aipos.aipospm.data

/**
 * Standard category folder presets and intelligent suggestion engine for AIPOS Password Manager.
 */
object CategoryPresets {

    val PASSWORD_PRESETS: List<String> = listOf(
        "Email & Accounts",
        "Social & Messaging",
        "Finance & Banking",
        "Work & Productivity",
        "Entertainment & Streaming",
        "Shopping & Delivery",
        "Personal & Utilities"
    )

    val API_KEY_PRESETS: List<String> = listOf(
        "AI & Chatbots",
        "Cloud & Infrastructure",
        "Developer & Coding",
        "Payments & Billing",
        "Databases & Storage",
        "Communications & Webhooks"
    )

    /**
     * Returns the full list of default categories to seed into the database.
     */
    fun getDefaultCategories(): List<Category> {
        val list = mutableListOf<Category>()
        PASSWORD_PRESETS.forEach {
            list.add(Category(name = it, type = CategoryType.PASSWORD.name))
        }
        API_KEY_PRESETS.forEach {
            list.add(Category(name = it, type = CategoryType.API_KEY.name))
        }
        return list
    }

    /**
     * Suggests a curated preset category name based on entry title, service name, username, or URL.
     * Returns null if no strong keyword match is found.
     */
    fun suggestCategory(query: String?, type: CategoryType): String? {
        if (query.isNullOrBlank()) return null
        val q = query.trim().lowercase()

        return when (type) {
            CategoryType.PASSWORD -> suggestPasswordCategory(q)
            CategoryType.API_KEY -> suggestApiKeyCategory(q)
        }
    }

    private fun suggestPasswordCategory(q: String): String? {
        // Social & Messaging (Check before generic email accounts)
        if (containsAny(q, "facebook", "meta", "instagram", "insta", "twitter", "x.com", "threads", "discord", "telegram", "whatsapp", "signal", "reddit", "tiktok", "snapchat", "linkedin", "pinterest", "mastodon", "messenger", "social")) {
            return "Social & Messaging"
        }

        // Entertainment & Streaming
        if (containsAny(q, "netflix", "spotify", "youtube", "disney", "hulu", "hbo", "max", "amazon prime", "prime video", "apple tv", "twitch", "steam", "playstation", "psn", "xbox", "nintendo", "epic games", "crunchyroll", "paramount", "deezer", "soundcloud", "streaming", "music", "game", "gaming")) {
            return "Entertainment & Streaming"
        }

        // Finance & Banking
        if (containsAny(q, "bank", "chase", "wells fargo", "citi", "bank of america", "bofa", "capital one", "paypal", "venmo", "cashapp", "cash app", "zelle", "revolut", "wise", "crypto", "binance", "coinbase", "kraken", "fidelity", "vanguard", "schwab", "robinhood", "mint", "invest", "finance")) {
            return "Finance & Banking"
        }

        // Work & Productivity
        if (containsAny(q, "slack", "notion", "jira", "atlassian", "confluence", "trello", "zoom", "teams", "asana", "clickup", "monday", "figma", "github", "gitlab", "bitbucket", "office 365", "dropbox", "box.com", "google drive", "salesforce")) {
            return "Work & Productivity"
        }

        // Shopping & Delivery
        if (containsAny(q, "amazon", "ebay", "walmart", "target", "aliexpress", "etsy", "best buy", "shopify", "uber", "ubereats", "doordash", "grubhub", "instacart", "deliveroo", "shopee", "lazada", "shopping")) {
            return "Shopping & Delivery"
        }

        // Email & Accounts
        if (containsAny(q, "google", "gmail", "outlook", "hotmail", "yahoo", "icloud", "microsoft", "proton", "protonmail", "zoho", "email", "mail", "apple id", "sso")) {
            return "Email & Accounts"
        }

        // Personal & Utilities
        if (containsAny(q, "wifi", "router", "internet", "att", "verizon", "t-mobile", "comcast", "xfinity", "health", "hospital", "doctor", "gov", "passport", "tax", "insurance", "school", "university", "canvas", "blackboard")) {
            return "Personal & Utilities"
        }

        return null
    }

    private fun suggestApiKeyCategory(q: String): String? {
        // Communications & Webhooks (Check before AI to avoid 'ai' in mailer)
        if (containsAny(q, "twilio", "sendgrid", "resend", "mailgun", "postmark", "pusher", "webhook", "discord webhook", "slack webhook", "sms", "email api")) {
            return "Communications & Webhooks"
        }

        // AI & Chatbots
        if (containsAny(q, "openai", "chatgpt", "gpt", "anthropic", "claude", "gemini", "groq", "mistral", "perplexity", "cohere", "huggingface", "deepseek", "ollama", "midjourney", "replicate", "langchain", "elevenlabs", "ai", "bot", "llm")) {
            return "AI & Chatbots"
        }

        // Cloud & Infrastructure
        if (containsAny(q, "aws", "amazon web", "google cloud", "gcp", "azure", "cloudflare", "vercel", "netlify", "digitalocean", "linode", "heroku", "render", "railway", "fly.io", "firebase", "cloud", "host", "server", "cdn")) {
            return "Cloud & Infrastructure"
        }

        // Developer & Coding
        if (containsAny(q, "github", "gitlab", "bitbucket", "docker", "sentry", "postman", "npm", "pypi", "datadog", "sonar", "circleci", "travis", "travisci", "terminal", "code", "dev", "git", "ci", "cd")) {
            return "Developer & Coding"
        }

        // Payments & Billing
        if (containsAny(q, "stripe", "paypal", "square", "braintree", "lemonsqueezy", "paddle", "plaid", "adyen", "pay", "billing", "payment", "checkout")) {
            return "Payments & Billing"
        }

        // Databases & Storage
        if (containsAny(q, "supabase", "mongodb", "mongo", "neon", "pinecone", "redis", "upstash", "planetscale", "fauna", "weaviate", "qdrant", "s3", "couchbase", "database", "sql", "db")) {
            return "Databases & Storage"
        }

        return null
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        val tokens = text.split(Regex("[^a-zA-Z0-9]+")).map { it.lowercase() }.toSet()
        for (kw in keywords) {
            val lowerKw = kw.lowercase()
            if (lowerKw.length <= 3) {
                if (tokens.contains(lowerKw)) {
                    return true
                }
            } else {
                if (text.contains(lowerKw)) {
                    return true
                }
            }
        }
        return false
    }
}

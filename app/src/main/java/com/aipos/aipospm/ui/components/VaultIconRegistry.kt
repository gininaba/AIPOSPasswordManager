package com.aipos.aipospm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

enum class VaultIconCategory(val displayName: String) {
    ALL("All"),
    DEVELOPER("Developer & Cloud"),
    SERVICES("Services & Web"),
    MEDIA("Media & Entertainment"),
    SECURITY("Security & Devices"),
    GENERAL("General")
}

data class VaultIconItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val category: VaultIconCategory
)

object VaultIconRegistry {

    val ALL_ICONS: List<VaultIconItem> = listOf(
        // Developer & Cloud — Ideal for API Keys & developer services
        VaultIconItem("api", "API", Icons.Default.Api, VaultIconCategory.DEVELOPER),
        VaultIconItem("key", "Key", Icons.Default.VpnKey, VaultIconCategory.DEVELOPER),
        VaultIconItem("code", "Code", Icons.Default.Code, VaultIconCategory.DEVELOPER),
        VaultIconItem("terminal", "Terminal", Icons.Default.Terminal, VaultIconCategory.DEVELOPER),
        VaultIconItem("ai", "AI / LLM", Icons.Default.AutoAwesome, VaultIconCategory.DEVELOPER),
        VaultIconItem("robot", "Bot", Icons.Default.SmartToy, VaultIconCategory.DEVELOPER),
        VaultIconItem("cloud", "Cloud", Icons.Default.Cloud, VaultIconCategory.DEVELOPER),
        VaultIconItem("database", "Database", Icons.Default.Storage, VaultIconCategory.DEVELOPER),
        VaultIconItem("server", "Server", Icons.Default.Dns, VaultIconCategory.DEVELOPER),
        VaultIconItem("webhook", "Webhook", Icons.Default.Webhook, VaultIconCategory.DEVELOPER),
        VaultIconItem("rocket", "Deploy / CI", Icons.Default.RocketLaunch, VaultIconCategory.DEVELOPER),
        VaultIconItem("chip", "Hardware", Icons.Default.Memory, VaultIconCategory.DEVELOPER),
        VaultIconItem("bug", "Debug / Bug", Icons.Default.BugReport, VaultIconCategory.DEVELOPER),
        VaultIconItem("data", "Data / JSON", Icons.Default.DataObject, VaultIconCategory.DEVELOPER),
        VaultIconItem("analytics", "Analytics", Icons.Default.Analytics, VaultIconCategory.DEVELOPER),
        VaultIconItem("brain", "Neural / AI", Icons.Default.Psychology, VaultIconCategory.DEVELOPER),
        VaultIconItem("folder", "Folder / Repo", Icons.Default.Folder, VaultIconCategory.DEVELOPER),
        VaultIconItem("network", "Network Hub", Icons.Default.Hub, VaultIconCategory.DEVELOPER),
        VaultIconItem("router", "Router", Icons.Default.Router, VaultIconCategory.DEVELOPER),
        VaultIconItem("cable", "Cable / IoT", Icons.Default.Cable, VaultIconCategory.DEVELOPER),

        // Services & Web — Great for passwords & web credentials
        VaultIconItem("globe", "Website", Icons.Default.Language, VaultIconCategory.SERVICES),
        VaultIconItem("email", "Email", Icons.Default.Email, VaultIconCategory.SERVICES),
        VaultIconItem("bank", "Bank / Finance", Icons.Default.AccountBalance, VaultIconCategory.SERVICES),
        VaultIconItem("card", "Credit Card", Icons.Default.CreditCard, VaultIconCategory.SERVICES),
        VaultIconItem("crypto", "Crypto / BTC", Icons.Default.CurrencyBitcoin, VaultIconCategory.SERVICES),
        VaultIconItem("wallet", "Wallet", Icons.Default.AccountBalanceWallet, VaultIconCategory.SERVICES),
        VaultIconItem("paid", "Payment / Cash", Icons.Default.Paid, VaultIconCategory.SERVICES),
        VaultIconItem("savings", "Savings", Icons.Default.Savings, VaultIconCategory.SERVICES),
        VaultIconItem("shopping", "Shopping", Icons.Default.ShoppingCart, VaultIconCategory.SERVICES),
        VaultIconItem("store", "Storefront", Icons.Default.Storefront, VaultIconCategory.SERVICES),
        VaultIconItem("receipt", "Bill / Invoice", Icons.AutoMirrored.Filled.ReceiptLong, VaultIconCategory.SERVICES),
        VaultIconItem("work", "Work", Icons.Default.Work, VaultIconCategory.SERVICES),
        VaultIconItem("school", "Education", Icons.Default.School, VaultIconCategory.SERVICES),
        VaultIconItem("chat", "Chat / Social", Icons.Default.Forum, VaultIconCategory.SERVICES),
        VaultIconItem("flight", "Travel / Flight", Icons.Default.Flight, VaultIconCategory.SERVICES),
        VaultIconItem("hotel", "Hotel / Stay", Icons.Default.Hotel, VaultIconCategory.SERVICES),
        VaultIconItem("car", "Transport / Auto", Icons.Default.DirectionsCar, VaultIconCategory.SERVICES),
        VaultIconItem("restaurant", "Food / Dining", Icons.Default.Restaurant, VaultIconCategory.SERVICES),

        // Media & Entertainment
        VaultIconItem("gaming", "Gaming", Icons.Default.SportsEsports, VaultIconCategory.MEDIA),
        VaultIconItem("movie", "Cinema / Movies", Icons.Default.Movie, VaultIconCategory.MEDIA),
        VaultIconItem("tv", "TV / Streaming", Icons.Default.Tv, VaultIconCategory.MEDIA),
        VaultIconItem("music", "Music / Audio", Icons.Default.MusicNote, VaultIconCategory.MEDIA),
        VaultIconItem("camera", "Photos / Camera", Icons.Default.PhotoCamera, VaultIconCategory.MEDIA),
        VaultIconItem("video", "Video", Icons.Default.Videocam, VaultIconCategory.MEDIA),
        VaultIconItem("headset", "Headphones", Icons.Default.Headphones, VaultIconCategory.MEDIA),
        VaultIconItem("book", "Books / Reading", Icons.AutoMirrored.Filled.MenuBook, VaultIconCategory.MEDIA),
        VaultIconItem("podcast", "Podcasts", Icons.Default.Podcasts, VaultIconCategory.MEDIA),

        // Security & Devices
        VaultIconItem("lock", "Lock", Icons.Default.Lock, VaultIconCategory.SECURITY),
        VaultIconItem("shield", "Shield", Icons.Default.Shield, VaultIconCategory.SECURITY),
        VaultIconItem("fingerprint", "Biometric", Icons.Default.Fingerprint, VaultIconCategory.SECURITY),
        VaultIconItem("vpn", "VPN / Proxy", Icons.Default.VpnLock, VaultIconCategory.SECURITY),
        VaultIconItem("key_off", "Revoked Key", Icons.Default.KeyOff, VaultIconCategory.SECURITY),
        VaultIconItem("privacy", "Privacy", Icons.Default.VisibilityOff, VaultIconCategory.SECURITY),
        VaultIconItem("verified", "Verified", Icons.Default.VerifiedUser, VaultIconCategory.SECURITY),
        VaultIconItem("password", "Password", Icons.Default.Password, VaultIconCategory.SECURITY),
        VaultIconItem("pin", "PIN / Code", Icons.Default.Pin, VaultIconCategory.SECURITY),
        VaultIconItem("phone", "Mobile", Icons.Default.Smartphone, VaultIconCategory.SECURITY),
        VaultIconItem("tablet", "Tablet", Icons.Default.Tablet, VaultIconCategory.SECURITY),
        VaultIconItem("computer", "Computer", Icons.Default.Computer, VaultIconCategory.SECURITY),
        VaultIconItem("laptop", "Laptop", Icons.Default.Laptop, VaultIconCategory.SECURITY),
        VaultIconItem("watch", "Smartwatch", Icons.Default.Watch, VaultIconCategory.SECURITY),
        VaultIconItem("usb", "Hardware Key", Icons.Default.Usb, VaultIconCategory.SECURITY),
        VaultIconItem("wifi", "Network / Wi-Fi", Icons.Default.Wifi, VaultIconCategory.SECURITY),

        // General & Lifestyle
        VaultIconItem("star", "Favorite", Icons.Default.Star, VaultIconCategory.GENERAL),
        VaultIconItem("person", "Personal", Icons.Default.Person, VaultIconCategory.GENERAL),
        VaultIconItem("home", "Home", Icons.Default.Home, VaultIconCategory.GENERAL),
        VaultIconItem("health", "Medical / Health", Icons.Default.MedicalServices, VaultIconCategory.GENERAL),
        VaultIconItem("fitness", "Fitness / Gym", Icons.Default.FitnessCenter, VaultIconCategory.GENERAL),
        VaultIconItem("palette", "Creative / Design", Icons.Default.Palette, VaultIconCategory.GENERAL),
        VaultIconItem("favorite", "Heart", Icons.Default.Favorite, VaultIconCategory.GENERAL),
        VaultIconItem("notifications", "Alert / News", Icons.Default.Notifications, VaultIconCategory.GENERAL),
        VaultIconItem("flag", "Flag / Priority", Icons.Default.Flag, VaultIconCategory.GENERAL),
        VaultIconItem("lightbulb", "Idea / Notes", Icons.Default.Lightbulb, VaultIconCategory.GENERAL)
    )

    private val iconMap: Map<String, VaultIconItem> = ALL_ICONS.associateBy { it.id }

    fun getIcon(id: String?): ImageVector? {
        if (id == null) return null
        return iconMap[id]?.icon
    }

    fun getIconItem(id: String?): VaultIconItem? {
        if (id == null) return null
        return iconMap[id]
    }
}

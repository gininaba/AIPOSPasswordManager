package com.aipos.aipospm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

enum class VaultIconCategory(val displayName: String) {
    ALL("All"),
    DEVELOPER("Developer & Cloud"),
    SERVICES("Services & Web"),
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

        // Services & Web — Great for passwords & web credentials
        VaultIconItem("globe", "Website", Icons.Default.Language, VaultIconCategory.SERVICES),
        VaultIconItem("email", "Email", Icons.Default.Email, VaultIconCategory.SERVICES),
        VaultIconItem("bank", "Bank / Finance", Icons.Default.AccountBalance, VaultIconCategory.SERVICES),
        VaultIconItem("card", "Credit Card", Icons.Default.CreditCard, VaultIconCategory.SERVICES),
        VaultIconItem("shopping", "Shopping", Icons.Default.ShoppingCart, VaultIconCategory.SERVICES),
        VaultIconItem("work", "Work", Icons.Default.Work, VaultIconCategory.SERVICES),
        VaultIconItem("school", "Education", Icons.Default.School, VaultIconCategory.SERVICES),
        VaultIconItem("chat", "Chat / Social", Icons.Default.Forum, VaultIconCategory.SERVICES),

        // Security & Devices
        VaultIconItem("lock", "Lock", Icons.Default.Lock, VaultIconCategory.SECURITY),
        VaultIconItem("shield", "Shield", Icons.Default.Shield, VaultIconCategory.SECURITY),
        VaultIconItem("fingerprint", "Biometric", Icons.Default.Fingerprint, VaultIconCategory.SECURITY),
        VaultIconItem("phone", "Mobile", Icons.Default.Smartphone, VaultIconCategory.SECURITY),
        VaultIconItem("computer", "Computer", Icons.Default.Computer, VaultIconCategory.SECURITY),
        VaultIconItem("wifi", "Network / Wi-Fi", Icons.Default.Wifi, VaultIconCategory.SECURITY),

        // General
        VaultIconItem("star", "Favorite", Icons.Default.Star, VaultIconCategory.GENERAL),
        VaultIconItem("person", "Personal", Icons.Default.Person, VaultIconCategory.GENERAL)
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

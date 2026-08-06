package com.safeguard.blocker

/**
 * Catalog of known VPN applications and (optionally) their expected signing
 * certificate SHA-256 fingerprints.
 *
 * Signature fingerprints are the real tamper check: a malicious clone that
 * copies the package name will NOT match the fingerprint. To populate
 * [KNOWN_SIGNATURES], obtain the hash of an installed build with:
 *
 *   apksigner verify --print-certs app.apk
 *   (then SHA-256 of the certificate bytes — [VpnPackageScanner] prints them
 *    to logcat with the "VPNScan" tag during a scan, so you can copy them in.)
 */
object VpnPackageDatabase {

    /** Package names of known VPN clients. Keep this list sorted for diffing. */
    val KNOWN_VPN_PACKAGES: Set<String> = setOf(
        // OpenVPN family
        "net.openvpn.openvpnconnect",
        "net.openvpn.vpn",
        "de.blinkt.openvpn",
        "com.openvpn.vpn",
        // Commercial providers
        "com.nordvpn.android",
        "ch.protonvpn.android",
        "com.expressvpn.vpn",
        "com.surfshark.vpnclient.android",
        "com.pia.android",
        "net.mullvad.mullvadvpn",
        "com.windscribe.vpn",
        "com.tunnelbear.android",
        "com.cyberghostvpn.samsung",
        "com.cyberghost.vpn",
        "com.anchorfree.hotspotshield",
        "com.anchorfree.partner.ipvanish",
        "com.torguard.vpn.android",
        "com.goldenfrog.vyprvpn",
        "com.zenmate.vpn",
        "com.purevpn.purevpnapp",
        "com.keepsolid.vpnunlimited",
        "net.ivpn.client",
        "com.hide.me",
        "com.atlasvpn.atlasvpn",
        "com.fsecure.freedome.vpn",
        "com.avast.android.vpn",
        "com.kaspersky.secure.connection",
        "com.norton.svpn",
        "com.cloudflare.onedotonedotonedotone", // 1.1.1.1 / WARP
        "com.tailscale.ipn",
        "com.zerotier.one",
        // Free / ad-supported clients
        "free.vpn.unblock.proxy.turbovpn",
        "com.free.vpn.unblock.proxy.thunder",
        "com.free.vpn.unblock.proxy.securevpn",
        "com.vpn.master",
        "com.xvpn.xvpn",
        "com.hola.vpn",
        "com.freevpnintouch",
        "com.psafe.snapvpn",
        "com.touchvpn.touchvpn",
        "com.vpnhub.vpn",
        "com.rocketvpn.rocketvpn",
        "com.pandavpn.android",
        // Open-source / technical tools
        "com.wireguard.android",
        "org.torproject.android", // Orbot (Tor proxy)
        "com.github.shadowsocks",
        "com.v2ray.ang",
        "fun.kitsunebi.kitsunebi4android",
        "com.nekohasekai.sagernet",
        "com.nekobox.android",
        "org.anonix.vpn",
        "com.psiphon3.subscription"
    )

    /**
     * Optional strict signature enforcement: package name -> SHA-256 hex of
     * the signing certificate. Populate from logcat "VPNScan" output to only
     * trust legit builds. Empty set = name-based blocking only.
     */
    val KNOWN_SIGNATURES: Map<String, String> = emptyMap()

    /** Everything we treat as a VPN-related trigger term in UI text. */
    val VPN_KEYWORDS: List<String> = listOf(
        "vpn",
        "vpn client",
        "openvpn",
        "nordvpn",
        "protonvpn",
        "expressvpn",
        "surfshark",
        "mullvad",
        "windscribe",
        "wireguard",
        "tunnelbear",
        "1.1.1.1",
        "warp"
    )
}

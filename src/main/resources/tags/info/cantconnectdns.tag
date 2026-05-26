type: text
aliases: helpdns, ccd

---

**🔧 Can't Connect? DNS Troubleshooting Guide**

If the server isn't appearing on your console, try these steps:

**✅ Basic Checks:**
1. **Same WiFi Network** - Your phone/tablet and console MUST be on the same WiFi while sending the DNS config
2. **Correct DNS** - Make sure your console DNS settings are correct
3. **Online Multiplayer Subscription** - Xbox requires an online multiplayer subscription, PlayStation requires PlayStation Plus, and Nintendo Switch requires Nintendo Switch Online, to connect to online multiplayer servers
4. **Correct Server Address** - Double-check the IP and port (default: 19132)
5. **Config Sent** - Verify the DNS config was sent properly in the MCCompanion app
6. **Accepts Bedrock Players** - Make sure the server you are trying to connect to allows Bedrock players to connect, we don't support Java-only servers yet

**🔄 Quick Fixes:**
• **Restart the app** - Close MCCompanion completely, reopen and try again
• **Restart your console** - Sometimes the console needs a reboot to clear DNS cache
• **Wait 10-15 seconds** - Give it time to appear
• **Disable VPN** - VPNs can alter your DNS servers

**⚠️ Common Issues:**

**"Joining the Featured Servers still directs to the actual servers and not to MCCompanion Relay Server"**
→ Make sure the server you select has the MOTD "MCCompanion RelayServer".
→ Check if your currently connected network still has the DNS settings configured to your preferred MCCompanion server.
→ Try reloading the featured server list a couple of times, as Minecraft itself can glitch when trying to load the featured server list. In Minecraft, hit B until you're back to the main screen, then go back to servers. This will reload the featured servers tab.
→ Restart your game console to clear DNS cache.
→ You might have IPv6 enabled on your network, which can cause issues on Xbox and possibly other game consoles, as they don't support custom IPv6 DNS settings to be set. Disable IPv6 on your router, if possible.
→ Your network might be blocking the DNS. In which case, you can try setting the DNS directly in your router's settings. Where to set this would depend on what brand of router you have. (e.g. Netgear, TP-Link, etc) You can try googling "[your router brand here] router how to set dns in router settings".
  

**"It shows "Coming Soon" in the servers tab"**
→ This is a Minecraft game bug, usually on the Switch version. You can try the following to fix it:
→ Signing out of your Microsoft account ingame, and signing back in.
→ You can also try reloading the "Servers" tab by hitting B (when in the "Servers" tab) until you're back to the main screen, then go back to servers, and try that a couple of times until it hopefully works.
→ Restarting the game.

Thank you, BedrockConnect, for providing the troubleshooting steps:  
https://github.com/Pugmatt/BedrockConnect/wiki/Troubleshooting

**📱 Still Having Issues?**
• Enable **Debug Mode** in MCCompanion to see detailed logs
• Check the console logs for error messages
• Make sure your device isn't in low power mode (it stops background tasks)
• Try a different server to test if MCCompanion is working

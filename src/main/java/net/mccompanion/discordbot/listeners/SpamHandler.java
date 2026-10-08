/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserDiscordBot
 */

package net.mccompanion.discordbot.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.mccompanion.discordbot.util.ModerationHelper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class SpamHandler extends ListenerAdapter {
    private static final long WINDOW_MILLIS = TimeUnit.SECONDS.toMillis(5);

    private record SpamWindow(int count, long channelId, long windowStart) {
    }

    private final Cache<String, SpamWindow> messageCache;

    public SpamHandler() {
        this.messageCache = CacheBuilder.newBuilder()
                .expireAfterWrite(WINDOW_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String key = event.getGuild().getId() + ":" + event.getAuthor().getId();
        long channelId = event.getChannel().getIdLong();
        long now = System.currentTimeMillis();

        SpamWindow window = this.messageCache.getIfPresent(key);

        if (window == null || now - window.windowStart() >= WINDOW_MILLIS) {
            window = new SpamWindow(0, channelId, now);
            this.messageCache.put(key, window);
        } else if (channelId != window.channelId()) { // Only increment if in a different channel
            // Keep the original window start so the window is fixed and not refreshed by later messages
            window = new SpamWindow(window.count() + 1, channelId, window.windowStart());
            this.messageCache.put(key, window);
        }

        if (window.count() >= 5) {
            // 5 or more messages, in different channels, really really fast... we'll quarantine
            messageCache.invalidate(key);
            ModerationHelper.quarantineMember(event.getMember(), event.getGuild(), "Suspected account compromise (Message spamming)", true, null, event.getMessage(), false);
        }
    }
}

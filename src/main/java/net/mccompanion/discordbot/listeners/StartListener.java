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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.mccompanion.discordbot.MCCBot;
import net.mccompanion.discordbot.util.BotColors;
import net.mccompanion.discordbot.util.FeaturedServers;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import pw.chew.chewbotcca.util.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StartListener extends ListenerAdapter {
    public static final String FEATURED_SERVERS_URL =
            "https://api.mccompanion.net/api/featured-servers";
    public static final String CHANNEL_ID = "1484678070030827560";

    private static final AtomicBoolean SCHEDULE_STARTED = new AtomicBoolean(false);

    private List<FeaturedServers> servers = new ArrayList<>();
    private volatile String cachedMessageId = null;
    private final AtomicInteger currentServer = new AtomicInteger(0);

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        if (!SCHEDULE_STARTED.compareAndSet(false, true)) {
            return;
        }

        var channel = event.getJDA().getTextChannelById(CHANNEL_ID);
        if (channel == null) {
            System.err.println("Channel not found!");
            return;
        }

        ScheduledExecutorService executor = MCCBot.getGeneralThreadPool();
        if (executor == null) {
            System.err.println("General thread pool is null!");
            return;
        }

        try {
            List<FeaturedServers> fetched = fetchFeaturedServers();
            if (fetched.isEmpty()) {
                System.err.println("No servers found in json");
                return;
            }
            this.servers = fetched;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            List<Message> history = channel.getHistory().retrievePast(50).complete();
            Message botMsg = history.stream()
                    .filter(m -> m.getAuthor().isBot() && m.getEmbeds().size() > 0)
                    .findFirst()
                    .orElse(null);
            if (botMsg != null) {
                cachedMessageId = botMsg.getId();
            }
        } catch (Exception ignored) {
        }

        executor.scheduleAtFixedRate(() -> {
            try {
                List<FeaturedServers> local = this.servers;
                if (local == null || local.isEmpty()) return;

                int idx = Math.floorMod(currentServer.getAndIncrement(), local.size());
                FeaturedServers s = local.get(idx);

                var eb = new EmbedBuilder()
                        .setTitle("🌟 Server of the Day: " + s.name(), s.websiteUrl())
                        .setDescription(String.format(
                                """
                                        **%s**  
                                        %s
                                        
                                        🌐 **[Website](%s)**  
                                        """,
                                s.name(),
                                s.description(),
                                s.websiteUrl() != null ? s.websiteUrl() : " "
                        ))
                        .addField("IP / Port", String.format("```%s:%s```", s.address(), s.port()), false)
                        .setColor(BotColors.SUCCESS.getColor())
                        .setTimestamp(OffsetDateTime.now())
                        .setFooter("Featured daily • NetherDev", null);

                if (s.iconUrl() != null && !s.iconUrl().isBlank()) {
                    eb.setThumbnail(s.iconUrl());
                }

                if (cachedMessageId != null) {
                    channel.retrieveMessageById(cachedMessageId).queue(
                            msg -> msg.editMessageEmbeds(eb.build()).queue(null, Throwable::printStackTrace),
                            failure -> {
                                failure.printStackTrace();
                                channel.sendMessageEmbeds(eb.build()).queue(msg -> cachedMessageId = msg.getId(), Throwable::printStackTrace);
                            }
                    );
                } else {
                    channel.sendMessageEmbeds(eb.build()).queue(msg -> cachedMessageId = msg.getId(), Throwable::printStackTrace);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 24, TimeUnit.HOURS);
    }

    @NotNull
    public static List<FeaturedServers> fetchFeaturedServers() {
        JSONArray arr = RestClient.get(FEATURED_SERVERS_URL).asJSONObject().getJSONArray("servers");
        List<FeaturedServers> servers = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            servers.add(new FeaturedServers(
                    obj.getString("name"),
                    obj.getString("description"),
                    obj.getString("address"),
                    obj.getInt("port"),
                    obj.optString("iconUrl", null),
                    obj.optString("websiteUrl", null)
            ));
        }
        return servers;
    }
}
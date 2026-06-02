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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.mccompanion.discordbot.storage.ServerSettings;
import net.mccompanion.discordbot.tags.TagsManager;
import net.mccompanion.discordbot.util.BotColors;
import net.mccompanion.discordbot.util.BotHelpers;
import net.mccompanion.discordbot.util.MessageHelper;
import org.jetbrains.annotations.NotNull;
import pw.chew.chewbotcca.util.RestClient;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrorAnalyzer extends ListenerAdapter {
    private final Map<Pattern, String> logUrlPatterns;
    public ErrorAnalyzer() {

        logUrlPatterns = new HashMap<>();

        // Log url patterns
        logUrlPatterns.put(Pattern.compile("hastebin\\.com/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://hastebin.com/raw/%s");
        logUrlPatterns.put(Pattern.compile("hasteb\\.in/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://hasteb.in/raw/%s");
        logUrlPatterns.put(Pattern.compile("mclo\\.gs/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://api.mclo.gs/1/raw/%s");
        logUrlPatterns.put(Pattern.compile("pastebin\\.com/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://pastebin.com/raw/%s");
        logUrlPatterns.put(Pattern.compile("gist\\.github\\.com/([0-9a-zA-Z]+)/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://gist.githubusercontent.com/%1$s/%2$s/raw/");
        logUrlPatterns.put(Pattern.compile("paste\\.shockbyte\\.com/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://paste.shockbyte.com/raw/%s");
        logUrlPatterns.put(Pattern.compile("pastie\\.io/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://pastie.io/raw/%s");
        logUrlPatterns.put(Pattern.compile("rentry\\.co/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://rentry.co/%s/raw");
        logUrlPatterns.put(Pattern.compile("pastebin.pl/view/([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE), "https://pastebin.pl/view/raw/%s");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        // exclude certain channels.
        if (ServerSettings.shouldNotCheckError(event.getChannel())) {
            return;
        }

        // Check attachments
        for (Message.Attachment attachment : event.getMessage().getAttachments()) {
            List<String> extensions;
            // Get the guild extensions and if not in a guild just use some defaults
            if (event.isFromGuild()) {
                extensions = ServerSettings.getList(event.getGuild().getIdLong(), "convert-extensions");
            } else {
                extensions = new ArrayList<>();
                extensions.add("txt");
                extensions.add("log");
                extensions.add("yml");
                extensions.add("0");
            }
            if (extensions.contains(attachment.getFileExtension())) {
                handleLog(event, RestClient.get(attachment.getUrl()).asString(), false);
            }
        }

        // Check the message for urls
        String rawContent = event.getMessage().getContentRaw();
        String url = null;
        for (Pattern regex : logUrlPatterns.keySet()) {
            Matcher matcher = regex.matcher(rawContent);

            if (!matcher.find()) {
                continue;
            }

            String[] groups = new String[matcher.groupCount()];
            for (int i = 0; i < matcher.groupCount(); i++) {
                groups[i] = matcher.group(i + 1);
                groups[i] = groups[i] == null ? "" : groups[i]; // Replace nulls with empty strings
            }

            url = String.format(logUrlPatterns.get(regex), (Object[]) groups);
            break;
        }

        String content;
        if (url == null) {
            content = rawContent;
        } else {
            // We didn't find a url so use the message content
            content = RestClient.get(url).asString();
        }

        handleLog(event, content, false);
    }

    /**
     * Handle the log content and output any errors
     *
     * @param event Message to respond to
     * @param content The log to check
     */
    private void handleLog(MessageReceivedEvent event, String content, boolean error) {
        // Create the embed and format it
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (error) {
            embedBuilder.setColor(BotColors.FAILURE.getColor());
            embedBuilder.addField("Error","Something went wrong while reading the image.", false);
            embedBuilder.setDescription(content);
            event.getMessage().replyEmbeds(embedBuilder.build()).queue();
        } else {
            embedBuilder.setTitle("Issue found!");
            embedBuilder.setDescription("See below for details and possible fixes");
            embedBuilder.setColor(BotColors.FAILURE.getColor());
            errorHandler(content, embedBuilder, event);
        }
    }

    private void errorHandler(String error, @NotNull EmbedBuilder embedBuilder, MessageReceivedEvent event) {
        int embedLength = embedBuilder.length();
        for (String issue : TagsManager.getIssueResponses().keySet()) {
            if (embedLength >= MessageEmbed.EMBED_MAX_LENGTH_BOT || embedBuilder.getFields().size() >= 25) {
                break;
            }

            if (error.toLowerCase().contains(issue.toLowerCase())) {
                String title = BotHelpers.trim(issue, MessageEmbed.TITLE_MAX_LENGTH);

                if (MessageHelper.similarFieldExists(embedBuilder.getFields(), title)) {
                    continue;
                }

                String fix = BotHelpers.trim(TagsManager.getIssueResponses().get(issue), MessageEmbed.VALUE_MAX_LENGTH);
                embedBuilder.addField(title, fix, false);
                embedLength += title.length() + fix.length();
            }
        }

        if (!embedBuilder.getFields().isEmpty()) {
            MessageHelper.truncateFields(embedBuilder);
            event.getMessage().replyEmbeds(embedBuilder.build()).queue();
        }
    }
}
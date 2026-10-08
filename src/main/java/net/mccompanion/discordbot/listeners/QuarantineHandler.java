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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.mccompanion.discordbot.storage.ServerSettings;
import net.mccompanion.discordbot.util.BotColors;
import net.mccompanion.discordbot.util.ModerationHelper;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QuarantineHandler extends ListenerAdapter {
    private final Set<Long> claimedMessages = ConcurrentHashMap.newKeySet();

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.isFromGuild()) return;
        String customId = event.getComponentId();
        if (!customId.equals("quarantine-handler")) return;

        String actionId = event.getValues().getFirst();
        Permission requiredPermission = switch (actionId) {
            case "unquarantine", "honeypot-misuse", "timeout" -> Permission.MODERATE_MEMBERS;
            case "kick" -> Permission.KICK_MEMBERS;
            case "compromised", "ban" -> Permission.BAN_MEMBERS;
            default -> null;
        };
        if (requiredPermission == null) {
            event.reply("Invalid quarantine action.").setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        var moderationRole = ServerSettings.getModRole(event.getGuild());
        if (moderator == null || !(moderator.hasPermission(requiredPermission)
                || moderationRole != null && moderator.getRoles().contains(moderationRole))) {
            event.reply("You are not authorized to handle quarantine actions.").setEphemeral(true).queue();
            return;
        }

        long messageId = event.getMessageIdLong();
        if (!claimedMessages.add(messageId)) {
            event.reply("Another moderator is already handling this quarantine.").setEphemeral(true).queue();
            return;
        }

        // Acknowledge the interaction immediately to prevent webhook expiration errors
        event.deferEdit().queue();

        String userId = event.getMessage().getContentRaw().substring(2, event.getMessage().getContentRaw().length() - 1);

        Member member = event.getGuild().getMemberById(userId);
        if (member == null) {
            claimedMessages.remove(messageId);
            event.getHook().sendMessage("Member has left server. Cannot take quarantine action.").queue();
            return;
        }

        switch (actionId) {
            case "unquarantine" -> {
                member.removeTimeout().queue(v -> {
                    finishAction(event, actionId, new EmbedBuilder()
                            .setTitle("Unquarantined member")
                            .setDescription("Unquarantined " + member.getAsMention() + ".")
                            .build());

                    member.getUser().openPrivateChannel().queue((channel) -> {
                        EmbedBuilder embedBuilder = new EmbedBuilder()
                                .setTitle("Welcome back!")
                                .setDescription("You have been unquarantined from " + event.getGuild().getName() + "!")
                                .setTimestamp(Instant.now())
                                .setColor(BotColors.SUCCESS.getColor());

                        channel.sendMessageEmbeds(embedBuilder.build()).queue();
                    });
                }, throwable -> {
                    claimedMessages.remove(messageId);
                    event.getHook().sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("Error")
                                    .setDescription("Issue unquarantining " + member.getAsMention() + ".")
                                    .build()
                    ).queue();
                });
            }
            case "honeypot-misuse", "timeout" -> {
                String reason = actionId.equals("honeypot-misuse") ? "Honey pot channel misuse." : "Timed out while in quarantine.";
                int days = actionId.equals("honeypot-misuse") ? 1 : 7;
                ModerationHelper.timeoutUser(member, moderator, event.getGuild(), Duration.ofDays(days), false, reason, (embed, succeeded) -> {
                    if (succeeded) {
                        finishAction(event, actionId, embed);
                    } else {
                        claimedMessages.remove(messageId);
                        event.getHook().sendMessageEmbeds(embed).queue();
                    }
                });
            }
            case "kick" -> {
                ModerationHelper.kickUser(member, moderator, event.getGuild(), false, "Kicked from quarantine", event.getChannel(), (embed, succeeded) -> {
                    if (succeeded) {
                        finishAction(event, actionId, embed);
                    } else {
                        claimedMessages.remove(messageId);
                        event.getHook().sendMessageEmbeds(embed).queue();
                    }
                });
            }
            case "compromised", "ban" -> {
                String reason = actionId.equals("compromised") ? "Scammer or compromised account" : "Banned while in quarantine";
                int days = actionId.equals("compromised") ? 1 : 0;
                ModerationHelper.banUser(member, moderator, event.getGuild(), days, false, reason, event.getChannel(), (embed, succeeded) -> {
                    if (succeeded) {
                        finishAction(event, actionId, embed);
                    } else {
                        claimedMessages.remove(messageId);
                        event.getHook().sendMessageEmbeds(embed).queue();
                    }
                });
            }
        }
    }

    private void finishAction(StringSelectInteractionEvent event, String actionId, net.dv8tion.jda.api.entities.MessageEmbed result) {
        claimedMessages.remove(event.getMessageIdLong());
        event.getHook().editOriginal(new MessageEditBuilder()
                .setContent("Handled by: " + event.getUser().getAsMention())
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Quarantine action handled.")
                        .setDescription("This quarantine was handled with action `%s`.".formatted(actionId))
                        .setTimestamp(Instant.now())
                        .setColor(BotColors.SUCCESS.getColor())
                        .build())
                .setComponents()
                .build()).queue();
        event.getHook().sendMessageEmbeds(result).queue();
    }
}
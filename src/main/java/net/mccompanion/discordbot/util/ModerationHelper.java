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

package net.mccompanion.discordbot.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.mccompanion.discordbot.MCCBot;
import net.mccompanion.discordbot.storage.ServerSettings;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class ModerationHelper {
    private ModerationHelper() {}

    public static void quarantineMember(Member user, Guild guild, String reason, boolean automatic, @Nullable Member staffMember, @Nullable Message referenceMessage, boolean deleteReferenceMessage) {
        Member actor = staffMember == null ? guild.getSelfMember() : staffMember;

        Member checkMember = guild.getMemberById(user.getId());
        if (checkMember == null) {
            if (referenceMessage != null && deleteReferenceMessage) {
                referenceMessage.delete().queue(v -> {}, throwable -> {});
            }
            return;
        }

        TextChannel moderationChannel = ServerSettings.getModChannel(guild);
        if (moderationChannel == null) {
            if (referenceMessage != null) {
                referenceMessage.reply("Quarantine could not be applied because the moderation channel is not configured.")
                        .queue(ignored -> {}, ignored -> {});
            }
            return;
        }

        String title;

        if (automatic) {
            title = "You have been automatically timed out in " + guild.getName() + "!";
        } else {
            title = "You have been timed out in " + guild.getName() + "!";
        }

        if (!actor.canInteract(user)) {
            MessageEmbed modChatEmbed = new EmbedBuilder()
                    .setTitle("Unactioned quarantine.")
                    .setDescription(user.getAsMention() + " cannot be quarantined as I do not have permission to timeout the user. Please take manual action!")
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.FAILURE.getColor())
                    .build();

            moderationChannel.sendMessage(
                    new MessageCreateBuilder()
                            .setContent(user.getAsMention())
                            .setEmbeds(modChatEmbed)
                            .build()
            ).queue(message -> {
                Role moderationRole = ServerSettings.getModRole(guild);
                if (moderationRole != null) {
                    message.reply(moderationRole.getAsMention())
                            .setAllowedMentions(null) // Allows the ping, null means all confusingly
                            .queue();
                }

                if (referenceMessage != null) {
                    referenceMessage.forwardTo(message.getChannel()).queue(msg -> {
                        if (deleteReferenceMessage) {
                            referenceMessage.delete().queue(v -> {}, throwable -> {});
                        }
                    }, throwable -> {
                        if (deleteReferenceMessage) {
                            referenceMessage.delete().queue(v -> {}, throwable2 -> {});
                        }
                    });
                }
            });

            // Now log it!
            int id = MCCBot.storageManager.addLog(actor, "quarantine", user, reason);

            MessageEmbed quarantinedEmbed = new EmbedBuilder()
                    .setTitle("Quarantined user (Unactioned!)")
                    .addField("User", user.getAsMention(), false)
                    .addField("Staff member", actor.getAsMention(), false)
                    .addField("Reason", reason, false)
                    .setFooter("ID: " + id)
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.WARNING.getColor())
                    .build();

            ServerSettings.getLogChannel(guild).sendMessageEmbeds(quarantinedEmbed).queue();

            return;
        }

        Duration duration = Duration.ofSeconds(60 * 60 * 24 * 28); // 28 days
        Instant expiry = Instant.now().plus(duration);
        user.timeoutFor(duration).queue(ignored -> {
            user.getUser().openPrivateChannel().queue(channel -> {
                MessageEmbed notification = new EmbedBuilder()
                        .setTitle(title)
                        .addField("Reason", reason, false)
                        .addField("Recommended Actions", "Change your Discord password, enable 2FA, and scan your computer for malware. See [Discord's article](https://support.discord.com/hc/en-us/articles/24160905919511-My-Discord-Account-was-Hacked-or-Compromised) for more info.", false)
                        .addField("Information", "If you believe this was an accident or a false flag, please reach out to a member of staff in order to get this sorted.", false)
                        .setTimestamp(Instant.now())
                        .setColor(BotColors.WARNING.getColor())
                        .build();
                channel.sendMessageEmbeds(notification).queue(ignoredMessage -> {}, ignoredFailure -> {});
            }, ignoredChannel -> {});

            ActionRow row = ActionRow.of(
                    StringSelectMenu.create("quarantine-handler")
                            .setPlaceholder("Select an action")
                            .addOption("Unquarantine", "unquarantine", "Unquarantine the user.")
                            .addOption("Honey pot misuse", "honeypot-misuse", "Punish the user for misuse of the honeypot channel. (1 day timeout.)")
                            .addOption("Compromised account", "compromised", "Ban the user for compromised account.")
                            .addOption("Timeout (1 week)", "timeout", "Timeout the user for 1 week.")
                            .addOption("Kick", "kick", "Kick the user.")
                            .addOption("Ban", "ban", "Ban the user.")
                            .build()
            );

            MessageEmbed modChatEmbed = new EmbedBuilder()
                    .setTitle("Quarantined user")
                    .setDescription(user.getAsMention() + " has been quarantined. Select an action below to take. Quarantine expires %s.".formatted(TimeFormat.RELATIVE.format(expiry)))
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.FAILURE.getColor())
                    .build();

            moderationChannel.sendMessage(
                    new MessageCreateBuilder()
                            .setContent(user.getAsMention())
                            .setEmbeds(modChatEmbed)
                            .build()
            ).addComponents(row).queue(message -> {
                Role moderationRole = ServerSettings.getModRole(guild);
                if (moderationRole != null) {
                    message.reply(moderationRole.getAsMention())
                            .setAllowedMentions(null)
                            .queue();
                }

                forwardReferenceMessage(referenceMessage, message.getChannel(), deleteReferenceMessage);
            }, ignoredMessage -> {});

            int id = MCCBot.storageManager.addLog(actor, "quarantine", user, reason);
            MessageEmbed quarantinedEmbed = new EmbedBuilder()
                    .setTitle("Quarantined user")
                    .addField("User", user.getAsMention(), false)
                    .addField("Staff member", actor.getAsMention(), false)
                    .addField("Reason", reason, false)
                    .setFooter("ID: " + id)
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.WARNING.getColor())
                    .build();
            ServerSettings.getLogChannel(guild).sendMessageEmbeds(quarantinedEmbed).queue();
        }, ignored -> {
            moderationChannel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Quarantine failed")
                    .setDescription("I couldn't timeout " + user.getAsMention() + ". No quarantine action was taken.")
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.FAILURE.getColor())
                    .build()).queue();
        });
    }

    private static void forwardReferenceMessage(@Nullable Message referenceMessage, MessageChannel destination, boolean deleteReferenceMessage) {
        if (referenceMessage == null) return;
        referenceMessage.forwardTo(destination).queue(message -> {
            if (deleteReferenceMessage) {
                referenceMessage.delete().queue(ignoredResult -> {}, ignoredDeleteFailure -> {});
            }
        }, ignored -> {
            if (deleteReferenceMessage) {
                referenceMessage.delete().queue(ignoredResult -> {}, ignoredDeleteFailure -> {});
            }
        });
    }

    public static void timeoutUser(Member member, @Nullable Member moderator, Guild guild, Duration duration, boolean silent, String reason, BiConsumer<MessageEmbed, Boolean> callback) {
        moderator = moderator == null ? guild.getSelfMember() : moderator;
        if (!prepareModeration(member, moderator, guild, callback)) return;

        User user = member.getUser();
        executePunishment(member, moderator, guild, user, "timeout", "Timed out user", "You have been timed out from " + guild.getName() + "!", reason, silent,
                guild.timeoutFor(user, duration).reason(reason), null, callback);
    }

    public static void kickUser(Member member, @Nullable Member moderator, Guild guild, boolean silent, String reason, @Nullable Channel originChannel, BiConsumer<MessageEmbed, Boolean> callback) {
        moderator = moderator == null ? guild.getSelfMember() : moderator;
        if (!prepareModeration(member, moderator, guild, callback)) return;

        User user = member.getUser();
        executePunishment(member, moderator, guild, user, "kick", "Kicked user", "You have been kicked from " + guild.getName() + "!", reason, silent,
                guild.kick(user).reason(reason), originChannel, callback);
    }

    public static void banUser(Member member, @Nullable Member moderator, Guild guild, int days, boolean silent, String reason, @Nullable Channel originChannel, BiConsumer<MessageEmbed, Boolean> callback) {
        moderator = moderator == null ? guild.getSelfMember() : moderator;
        if (!prepareModeration(member, moderator, guild, callback)) return;
        if (days < 0 || days > 7) {
            callback.accept(errorEmbed("Invalid message history window", "The number of days to delete must be between 0 and 7."), false);
            return;
        }

        User user = member.getUser();
        executePunishment(member, moderator, guild, user, "ban", "Banned user", "You have been banned from " + guild.getName() + "!", reason, silent,
                guild.ban(user, days, TimeUnit.DAYS).reason(reason), originChannel, callback);
    }

    private static boolean prepareModeration(@Nullable Member member, @Nullable Member moderator, Guild guild, BiConsumer<MessageEmbed, Boolean> callback) {
        if (member == null) {
            callback.accept(errorEmbed("Invalid user", "The user ID specified doesn't link with any valid user in this server."), false);
            return false;
        }
        if (!BotHelpers.canTarget(moderator, member)) {
            callback.accept(errorEmbed("Higher role", "Either the bot or you cannot target that user."), false);
            return false;
        }
        return true;
    }

    private static void executePunishment(Member member, Member moderator, Guild guild, User user, String action, String successTitle, String notificationTitle, String reason, boolean silent, RestAction<Void> request, @Nullable Channel originChannel, BiConsumer<MessageEmbed, Boolean> callback) {
        request.queue(ignored -> {
            int id = MCCBot.storageManager.addLog(moderator, action, user, reason);
            MessageEmbed result = new EmbedBuilder()
                    .setTitle(successTitle)
                    .addField("User", user.getAsMention(), false)
                    .addField("Staff member", moderator.getAsMention(), false)
                    .addField("Reason", reason, false)
                    .setFooter("ID: " + id)
                    .setTimestamp(Instant.now())
                    .setColor(BotColors.SUCCESS.getColor())
                    .build();

            ServerSettings.getLogChannel(guild).sendMessageEmbeds(result).queue();
            TextChannel moderationChannel = ServerSettings.getModChannel(guild);
            if ((originChannel == null || !ServerSettings.isModChannel(guild, originChannel)) && moderationChannel != null) {
                moderationChannel.sendMessageEmbeds(result).queue();
            }

            callback.accept(result, true);
            if (!silent) {
                EmbedBuilder notification = new EmbedBuilder()
                        .setTitle(notificationTitle)
                        .addField("Reason", reason, false)
                        .setTimestamp(Instant.now())
                        .setColor(BotColors.FAILURE.getColor());
                String punishmentMessage = MCCBot.storageManager.getServerPreference(guild.getIdLong(), "punishment-message");
                if (punishmentMessage != null && !punishmentMessage.isEmpty()) {
                    notification.addField("Additional Info", punishmentMessage, false);
                }
                user.openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(notification.build()).queue(ignoredMessage -> {}, ignoredFailure -> {}), ignoredChannel -> {});
            }
        }, ignored -> callback.accept(errorEmbed("Moderation action failed", "I couldn't " + action + " " + user.getAsMention() + "."), false));
    }

    private static MessageEmbed errorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .setColor(BotColors.FAILURE.getColor())
                .build();
    }
}

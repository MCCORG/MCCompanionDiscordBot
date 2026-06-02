/*
 * Copyright (c) 2021-2026 GeyserMC. http://geysermc.org
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

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.CommandListener;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class BotCommandListener implements CommandListener {
    // Methods to detect command usage. Currently unused

    public void onCommand(CommandEvent event, Command command) {
    }

    public void onSlashCommand(SlashCommandEvent event, SlashCommand command) {
    }

    public void onCommandException(CommandEvent event, Command command, Throwable throwable) {
        // Default rethrow as a runtime exception.
        throw throwable instanceof RuntimeException? (RuntimeException)throwable : new RuntimeException(throwable);
    }

    public void onSlashCommandException(SlashCommandEvent event, SlashCommand command, Throwable throwable) {
        // Default rethrow as a runtime exception.
        throw throwable instanceof RuntimeException? (RuntimeException)throwable : new RuntimeException(throwable);
    }
    
}

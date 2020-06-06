package com.discord.katrine;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MINUTES;

public class ChannelUpdater {
    private Logger log = LoggerFactory.getLogger(ChannelUpdater.class);
    private Map<String, String> channels = new HashMap<>();

    public ChannelUpdater(JDA jda, Properties prop) {
        createChannels(jda.getGuildById(prop.getProperty("guild.id")), prop);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(getChannelNameUpdateTask(jda.getGuildById(prop.getProperty("guild.id")), prop),
                Integer.parseInt(prop.getProperty("timer.delay", "0")),
                Integer.parseInt(prop.getProperty("timer.period", "5")),
                MINUTES);
    }

    private void createChannels(Guild guild, Properties prop) {
        if (guild == null) {
            log.error("Attention! The guild ID is incorrect!");
            System.exit(1);
        }
        if (guild.getCategoriesByName(prop.getProperty("guild.category.title", "Чертоги"), false).size() == 0) {
            guild.createCategory(prop.getProperty("guild.category.title", "Чертоги"))
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL))
                    .complete();
        }
        Category category = guild.getCategoriesByName(prop.getProperty("guild.category.title", "Чертоги"), false).get(0);

        if (category.getVoiceChannels().size() == 0) {
            guild.createVoiceChannel(prop.getProperty("guild.channel.title.all", "Душ в аду:"))
                    .setParent(category)
                    .complete();
            guild.createVoiceChannel(prop.getProperty("guild.channel.title.online", "Онлайн:"))
                    .setParent(category)
                    .complete();
            guild.createVoiceChannel(prop.getProperty("guild.channel.title.voice", "Разговаривают:"))
                    .setParent(category)
                    .complete();
        }

        category.getVoiceChannels().forEach(channel -> {
                    if (channel.getName().equals(prop.getProperty("guild.channel.title.all", "Душ в аду:"))) {
                        channels.put("all", channel.getId());
                    } else if (channel.getName().equals(prop.getProperty("guild.channel.title.online", "Онлайн:"))) {
                        channels.put("online", channel.getId());
                    } else if (channel.getName().equals(prop.getProperty("guild.channel.title.voice", "Разговаривают:"))) {
                        channels.put("voice", channel.getId());
                    }
                }
        );

    }

    private Runnable getChannelNameUpdateTask(Guild guild, Properties prop) {
        return () -> {
            List<Member> members = guild.getMembers();
            long voiceCount = guild.getVoiceChannels().stream()
                    .mapToLong(channel -> channel.getMembers().stream().filter(member -> !member.getUser().isBot()).count())
                    .sum();
            long allCount = members.stream().filter(member -> !member.getUser().isBot()).count();
            long onlineCount = guild.getMembers().stream()
                    .filter(member -> !member.getOnlineStatus().equals(OnlineStatus.OFFLINE) && !member.getUser().isBot())
                    .count();

            log.info("\nTime: {} \nAll count: {}, Online count: {}, Voice count: {}",
                    LocalDateTime.now().toString(), allCount, onlineCount, voiceCount);

            Objects.requireNonNull(guild.getVoiceChannelById(channels.get("all")), "There is no such channel")
                    .getManager()
                    .setName(prop.getProperty("guild.channel.title.all", "Душ в аду:") + " " + allCount).queue();
            Objects.requireNonNull(guild.getVoiceChannelById(channels.get("online")), "There is no such channel")
                    .getManager()
                    .setName(prop.getProperty("guild.channel.title.online", "Онлайн:") + " " + onlineCount).queue();
            Objects.requireNonNull(guild.getVoiceChannelById(channels.get("voice")), "There is no such channel")
                    .getManager()
                    .setName(prop.getProperty("guild.channel.title.voice", "Разговаривают:") + " " + voiceCount).queue();
        };
    }

}

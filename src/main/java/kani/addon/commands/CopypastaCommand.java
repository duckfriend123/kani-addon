package kani.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import kani.addon.utils.CopypastaManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.command.CommandSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CopypastaCommand extends Command {
    private static final int CHAT_LIMIT = 256;
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int delayTicks = 10;
    private static int ticksLeft;

    public CopypastaCommand() {
        super("copypasta", "Sends a copypasta from your Kani copypasta folder.");
        MeteorClient.EVENT_BUS.subscribe(Listener.INSTANCE);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("name", StringArgumentType.word())
            .suggests(this::suggestPastas)
            .executes(this::run));

        builder.then(literal("delay")
            .then(argument("ticks", IntegerArgumentType.integer(0, 200))
                .executes(context -> {
                    delayTicks = IntegerArgumentType.getInteger(context, "ticks");
                    info("Set copypasta delay to %d tick(s).", delayTicks);
                    return SINGLE_SUCCESS;
                })));
    }

    private int run(CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        Optional<String> content = CopypastaManager.read(name);
        if (content.isEmpty()) {
            error("Unknown copypasta \"%s\".", name);
            return SINGLE_SUCCESS;
        }

        sendChunks(content.get());
        return SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestPastas(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        for (String name : CopypastaManager.listPastas()) builder.suggest(name);
        return builder.buildFuture();
    }

    private void sendChunks(String text) {
        for (String chunk : split(text)) {
            if (!chunk.isEmpty()) QUEUE.add(chunk);
        }
    }

    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        int index = 0;

        while (index < text.length()) {
            int end = Math.min(text.length(), index + CHAT_LIMIT);
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end - 1);
                if (lastSpace > index) end = lastSpace + 1;
            }

            String chunk = text.substring(index, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);

            index = end;
            while (index < text.length() && text.charAt(index) == ' ') index++;
        }

        return chunks;
    }

    private static final class Listener {
        private static final Listener INSTANCE = new Listener();

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (QUEUE.isEmpty()) return;

            if (ticksLeft > 0) {
                ticksLeft--;
                return;
            }

            String next = QUEUE.poll();
            if (next != null) {
                ChatUtils.sendPlayerMsg(next);
                ticksLeft = Math.max(0, delayTicks);
            }
        }
    }
}

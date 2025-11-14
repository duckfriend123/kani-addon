package kani.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class RepeatCommand extends Command {
    public RepeatCommand() {
        super("repeat", "Repeats a message or command multiple times. Supports %n placeholder.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("amount", IntegerArgumentType.integer(1))
            .then(argument("message", StringArgumentType.greedyString()).executes(context -> {
                if (mc.player == null) {
                    error("You need to be in-game to use this.");
                    return SINGLE_SUCCESS;
                }

                int amount = IntegerArgumentType.getInteger(context, "amount");
                String template = StringArgumentType.getString(context, "message");

                for (int i = 1; i <= amount; i++) {
                    String message = template.replace("%n", Integer.toString(i));
                    ChatUtils.sendPlayerMsg(message);
                }

                info("Repeated %d time%s.", amount, amount == 1 ? "" : "s");
                return SINGLE_SUCCESS;
            })));
    }
}

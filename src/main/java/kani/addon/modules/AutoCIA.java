package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

public class AutoCIA extends Module {
    private static final char BLOCK = '█';

    public AutoCIA() {
        super(KaniAddon.CATEGORY, "AutoCIA", "███████ █████ ███ ████████ ██ ████ ██ ████ ██████ █████████.");
    }

    @EventHandler
    private void onSend(SendMessageEvent event) {
        event.message = filter(event.message);
    }

    @EventHandler
    private void onReceive(ReceiveMessageEvent event) {
        event.setMessage(Text.literal(filter(event.getMessage().getString())));
    }

    private String filter(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isAllowed(c)) sb.append(c);
            else sb.append(BLOCK);
        }
        return sb.toString();
    }

    private boolean isAllowed(char c) {
        return c == ' ' || c == ',' || c == '.' || c == '!' || c == '?' || c == '=';
    }
}

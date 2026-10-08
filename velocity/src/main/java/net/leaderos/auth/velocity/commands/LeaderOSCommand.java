package net.leaderos.auth.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.leaderos.auth.shared.security.BedrockLinkStore;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.helpers.ChatUtil;

public class LeaderOSCommand implements SimpleCommand {

    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 1 && args[0].equals("reload")) {
            if (source.hasPermission("leaderosauth.reload")) {
                Velocity.getInstance().reloadConfiguration();

                ChatUtil.sendMessage(source, Velocity.getInstance().getLangFile().getMessages().getReload());
            } else
                ChatUtil.sendMessage(source, Velocity.getInstance().getLangFile().getMessages().getCommand().getNoPerm());
        } else if (args.length == 2 && args[0].equals("unlinkbedrock")) {
            // Removes the Bedrock (Xbox) login trust of an account, e.g. after the owner changed Xbox accounts.
            if (!source.hasPermission("leaderos.bedrock.unlink")) {
                ChatUtil.sendMessage(source, Velocity.getInstance().getLangFile().getMessages().getCommand().getNoPerm());
                return;
            }
            Velocity plugin = Velocity.getInstance();
            String playerName = args[1];
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                BedrockLinkStore store = plugin.getDatabase() == null ? null : plugin.getDatabase().getBedrockLinkStore();
                boolean removed = store != null && store.unbind(playerName);
                String message = removed ? plugin.getLangFile().getMessages().getBedrockUnlinked()
                        : plugin.getLangFile().getMessages().getBedrockNotLinked();
                ChatUtil.sendMessage(source, message.replace("{player}", playerName));
            }).schedule();
        } else
            ChatUtil.sendMessage(source, Velocity.getInstance().getLangFile().getMessages().getCommand().getInvalidArgument());
    }
}

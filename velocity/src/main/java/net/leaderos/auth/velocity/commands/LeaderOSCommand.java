package net.leaderos.auth.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
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
        } else
            ChatUtil.sendMessage(source, Velocity.getInstance().getLangFile().getMessages().getCommand().getInvalidArgument());
    }
}

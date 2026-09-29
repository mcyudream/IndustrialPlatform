package dev.celestiacraft.industrialplatform.command;

import dev.celestiacraft.industrialplatform.blueprint.Blueprint;
import dev.celestiacraft.industrialplatform.blueprint.BlueprintLibrary;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandBlueprints extends CommandBase {

    @Override
    public String getName() {
        return "ipblueprints";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ipblueprints <reload|list>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            int count = BlueprintLibrary.load(server.getDataDirectory());
            sender.sendMessage(new TextComponentTranslation("ip.cmd.blueprints_loaded", count));
        } else if ("list".equals(sub)) {
            java.util.List<Blueprint> all = BlueprintLibrary.all();
            if (all.isEmpty()) {
                sender.sendMessage(new TextComponentTranslation("ip.cmd.blueprints_loaded", 0));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Blueprint blueprint : all) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(blueprint.name);
            }
            sender.sendMessage(new TextComponentString(sb.toString()));
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }
}

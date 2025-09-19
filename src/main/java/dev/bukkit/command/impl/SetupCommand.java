package dev.bukkit.command.impl;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.kodysimpson.simpapi.command.SubCommand;

public class SetupCommand extends SubCommand{

    @Override
    public String getName() {
        return "Setup";
    }

    @Override
    public List<String> getAliases() {
        return null;
    }

    @Override
    public String getDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getSyntax() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<String> getSubcommandArguments(Player player, String[] args) {
        // TODO Auto-generated method stub
        return null;
    }

}

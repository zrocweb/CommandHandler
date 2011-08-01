package com.pneumaticraft.commandhandler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.lithium3141.shellparser.ShellParser;

public class CommandHandler {

    protected JavaPlugin plugin;

    protected List<QueuedCommand> queuedCommands;
    protected List<Command> allCommands;

    protected PermissionsInterface permissions;

    public CommandHandler(JavaPlugin plugin, PermissionsInterface permissions) {
        this.plugin = plugin;

        this.allCommands = new ArrayList<Command>();
        this.queuedCommands = new ArrayList<QueuedCommand>();
        this.permissions = permissions;
    }

    public List<Command> getCommands(CommandSender sender) {
        List<Command> permissiveCommands = new ArrayList<Command>();
        for (Command c : this.allCommands) {
            if (this.permissions.hasPermission(sender, c.getPermissionString(), c.isOpRequired())) {
                permissiveCommands.add(c);
            }
        }
        return permissiveCommands;
    }

    public List<Command> getAllCommands() {
        return this.allCommands;
    }

    public boolean locateAndRunCommand(CommandSender sender, List<String> args) {
        List<String> parsedArgs = parseAllQuotedStrings(args);
        String key = null;

        Iterator<Command> iterator = this.allCommands.iterator();
        Command foundCommand = null;
        // Initialize a list of all commands that match:
        List<Command> foundCommands = new ArrayList<Command>();
        List<String> foundKeys = new ArrayList<String>();

        while (iterator.hasNext()) {
            foundCommand = iterator.next();
            key = foundCommand.getKey(parsedArgs);
            if (key != null) {
                foundCommands.add(foundCommand);
                foundKeys.add(key);
            }
        }
        processFoundCommands(foundCommands, foundKeys, sender, parsedArgs);
        return true;
    }

    /**
     * The purpose of this method is to determine the most specific command matching the args and execute it.
     * 
     * @param foundCommands A list of all matching commands.
     * @param foundKeys A list of the key that was matched the command.
     * @param parsedArgs The arguments who have been combined, ie: "The world" is one argument
     * @param parsedArgs
     */
    private void processFoundCommands(List<Command> foundCommands, List<String> foundKeys, CommandSender sender, List<String> parsedArgs) {

        Command bestMatch = null;
        String matchingKey = null;
        int bestMatchInt = -1;

        for (int i = 0; i < foundCommands.size(); i++) {
            if (foundCommands.get(i).getNumKeyArgs(foundKeys.get(i)) > bestMatchInt) {
                bestMatch = foundCommands.get(i);
                matchingKey = foundKeys.get(i);
            }
        }
        if (bestMatch != null) {
            bestMatch.removeKeyArgs(parsedArgs, matchingKey);
            // Special case:
            // If the ONLY param is a '?' show them the usage.
            if (parsedArgs.size() == 1 && parsedArgs.get(0).equals("?") && this.permissions.hasAnyPermission(sender, bestMatch.getAllPermissionStrings(), bestMatch.isOpRequired())) {
                this.showHelp(sender, bestMatch);
            } else {
                checkAndRunCommand(sender, parsedArgs, bestMatch);
            }
        }
    }

    public void registerCommand(Command command) {
        this.allCommands.add(command);
    }

    /**
     * Combines all quoted strings
     * 
     * @param args
     * @return
     */
    private List<String> parseAllQuotedStrings(List<String> args) {
        String arg = null;
        if (args.size() == 0) {
            arg = "";
        } else {
            arg = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                arg = arg + " " + args.get(i);
            }
        }

        List<String> result = ShellParser.safeParseString(arg);
        if (result == null) {
            return new ArrayList<String>();
        } else {
            return result;
        }
    }

    /**
     * "The command " + ChatColor.RED + commandName + ChatColor.WHITE + " has been halted due to the fact that it could break something!" "If you still wish to execute " + ChatColor.RED + commandName + ChatColor.WHITE
     */
    public void queueCommand(CommandSender sender, String commandName, String methodName, List<? extends Object> args, Class<?>[] paramTypes, String message, String message2, String success, String fail, int seconds) {
        cancelQueuedCommand(sender);
        this.queuedCommands.add(new QueuedCommand(methodName, args, paramTypes, sender, Calendar.getInstance(), this.plugin, success, fail, seconds));

        if (message == null) {
            message = "The command " + ChatColor.RED + commandName + ChatColor.WHITE + " has been halted due to the fact that it could break something!";
        } else {
            message = message.replace("{CMD}", ChatColor.RED + commandName + ChatColor.WHITE);
        }

        if (message2 == null) {
            message2 = "If you still wish to execute " + ChatColor.RED + commandName + ChatColor.WHITE;
        } else {
            message2 = message2.replace("{CMD}", ChatColor.RED + commandName + ChatColor.WHITE);
        }

        sender.sendMessage(message);
        sender.sendMessage(message2);
        sender.sendMessage("please type: " + ChatColor.GREEN + "/mvconfirm");
        sender.sendMessage(ChatColor.GREEN + "/mvconfirm" + ChatColor.WHITE + " will only be available for " + seconds + " seconds.");
    }

    public void queueCommand(CommandSender sender, String commandName, String methodName, List<? extends Object> args, Class<?>[] paramTypes, String success, String fail) {
        this.queueCommand(sender, commandName, methodName, args, paramTypes, null, null, success, fail, 10);
    }

    /**
     * Tries to fire off the command
     * 
     * @param sender
     * @return
     */
    public boolean confirmQueuedCommand(CommandSender sender) {
        for (QueuedCommand com : this.queuedCommands) {
            if (com.getSender().equals(sender)) {
                if (com.execute()) {
                    if (com.getSuccess() != null && com.getSuccess().length() > 0) {
                        sender.sendMessage(com.getSuccess());
                    }
                    return true;
                } else {
                    if (com.getFail() != null && com.getFail().length() > 0) {
                        sender.sendMessage(com.getFail());
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Cancels(invalidates) a command that has been requested. This is called when a user types something other than 'yes' or when they try to queue a second command Queuing a second command will delete the first command entirely.
     * 
     * @param sender
     */
    public void cancelQueuedCommand(CommandSender sender) {
        QueuedCommand c = null;
        for (QueuedCommand com : this.queuedCommands) {
            if (com.getSender().equals(sender)) {
                c = com;
            }
        }
        if (c != null) {
            // Each person is allowed at most one queued command.
            this.queuedCommands.remove(c);
        }
    }

    /**
     * Returns the given flag value
     * 
     * @param flag A param flag, like -s or -g
     * @param args All arguments to search through
     * @return A string or null
     */
    public static String getFlag(String flag, List<String> args) {
        int i = 0;
        try {
            for (String s : args) {
                if (s.equalsIgnoreCase(flag)) {
                    return args.get(i + 1);
                }
                i++;
            }
        } catch (IndexOutOfBoundsException e) {
        }
        return null;
    }

    private void checkAndRunCommand(CommandSender sender, List<String> parsedArgs, Command foundCommand) {
        if (this.permissions.hasAnyPermission(sender, foundCommand.getAllPermissionStrings(), foundCommand.isOpRequired())) {
            if (foundCommand.checkArgLength(parsedArgs)) {
                foundCommand.runCommand(sender, parsedArgs);
            } else {
                showHelp(sender, foundCommand);
            }
        } else {
            sender.sendMessage("You do not have any of the required permission(s):");
            for (String perm : foundCommand.getAllPermissionStrings()) {
                sender.sendMessage(" - " + ChatColor.GREEN + perm);
            }
        }
    }

    private void showHelp(CommandSender sender, Command foundCommand) {
        sender.sendMessage(ChatColor.AQUA + foundCommand.getCommandName());
        sender.sendMessage(ChatColor.GOLD + foundCommand.getCommandDesc());
        sender.sendMessage(ChatColor.DARK_AQUA + foundCommand.getCommandUsage());
        sender.sendMessage(ChatColor.GREEN + foundCommand.getPermissionString());
        String keys = "";
        for (String key : foundCommand.getKeys()) {
            keys += key + ", ";
        }
        keys = keys.substring(0, keys.length() - 2);
        sender.sendMessage(ChatColor.BLUE + "Aliases: " + ChatColor.DARK_RED + keys);
        if (foundCommand.getExamples().size() > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Examples:");
            for(String ex : foundCommand.getExamples()) {
                sender.sendMessage(ex);
            }
        }
    }
}

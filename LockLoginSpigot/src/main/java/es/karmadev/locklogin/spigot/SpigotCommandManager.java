package es.karmadev.locklogin.spigot;

import es.karmadev.locklogin.api.event.extension.CommandProcessEvent;
import es.karmadev.locklogin.api.extension.Module;
import es.karmadev.locklogin.api.extension.command.ModuleCommand;
import es.karmadev.locklogin.api.extension.command.worker.CommandExecutor;
import es.karmadev.locklogin.api.extension.manager.ModuleManager;
import es.karmadev.locklogin.api.network.NetworkEntity;
import es.karmadev.locklogin.api.network.PluginNetwork;
import es.karmadev.locklogin.api.plugin.file.Messages;
import es.karmadev.locklogin.spigot.command.module.CommandHandler;
import es.karmadev.locklogin.spigot.command.module.ExecutorHelper;
import es.karmadev.locklogin.spigot.util.UserDataHandler;
import ml.karmaconfigs.api.common.string.StringUtils;
import ml.karmaconfigs.api.common.utils.enums.Level;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpigotCommandManager implements Function<ModuleCommand, Boolean>, Consumer<ModuleCommand> {

    private final LockLoginSpigot plugin;
    private final CommandMap map;

    private final Map<ModuleCommand, CommandHandler> handlers = new ConcurrentHashMap<>();

    public SpigotCommandManager(final LockLoginSpigot plugin, CommandMap map) {
        this.plugin = plugin;
        this.map = map;
    }

    /**
     * Performs this operation on the given argument.
     *
     * @param command the input argument
     */
    @Override
    public void accept(final ModuleCommand command) {
        if (handlers.containsKey(command)) {
            CommandHandler handler = handlers.remove(command);
            handler.unregister(map);

            plugin.console().send("Unregistered command {0} from module {1}", Level.INFO, command.getName(), command.getModule().name());

            for (Player online : plugin.plugin().getServer().getOnlinePlayers()) {
                try {
                    online.updateCommands();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Applies this function to the given argument.
     *
     * @param command the function argument
     * @return the function result
     */
    @Override
    public Boolean apply(final ModuleCommand command) {
        Module module = command.getModule();
        PluginNetwork network = plugin.network();
        ModuleManager manager = plugin.moduleManager();
        Messages messages = plugin.messages();

        CommandHandler handler = CommandHandler.builder()
                .name(command.getName())
                .description(command.getDescription())
                .aliases(command.getAliases())
                .executor(ExecutorHelper.createExecutor((pluginCommand) -> {
                    CommandExecutor executor = command.getExecutor();
                    if (executor != null) {
                        CommandSender sender = pluginCommand.getSender();
                        String label = pluginCommand.getLabel();
                        String[] args = pluginCommand.getArgs();

                        StringBuilder commandBuilder = new StringBuilder("/").append(command);
                        for (int i = 0; i < args.length; i++) {
                            commandBuilder.append(args[i]);
                            if (i != args.length - 1) commandBuilder.append(" ");
                        }

                        if (sender instanceof Player) {
                            Player player = (Player) sender;
                            int id = UserDataHandler.getNetworkId(player);
                            if (id >= 0) {
                                NetworkEntity entity = network.getEntity(id);
                                CommandProcessEvent event = new CommandProcessEvent(entity, commandBuilder.toString(), label, args);
                                manager.fireEvent(event);

                                if (!event.isCancelled()) {
                                    label = event.getCommand();
                                    args = event.getArguments();

                                    executor.execute(entity, label, args);
                                } else {
                                    player.sendMessage(StringUtils.toColor(messages.prefix() + "&cFailed to issue command " + event.getMessage() + ". &7" + event.cancelReason()));
                                }
                            } else {
                                player.sendMessage(StringUtils.toColor(messages.prefix() + "&cThere was a problem while performing that command"));
                            }
                        } else {
                            CommandProcessEvent event = new CommandProcessEvent(plugin, commandBuilder.toString(), label, args);
                            manager.fireEvent(event);

                            if (!event.isCancelled()) {
                                label = event.getCommand();
                                args = event.getArguments();

                                executor.execute(plugin, label, args);
                            } else {
                                plugin.console().send(messages.prefix() + "&cFailed to issue command " + event.getMessage() + ". &7" + event.cancelReason());
                            }
                        }
                    }
                })).build();

        if (map.register(command.getName(), module.name(), handler)) {
            handlers.put(command, handler);
            plugin.console().send("Registered command {0} from module {1}", Level.INFO, command.getName(), module.name());

            for (Player online : plugin.plugin().getServer().getOnlinePlayers()) {
                try {
                    online.updateCommands();
                } catch (Throwable ignored) {
                }
            }

            return true;
        }

        return false;
    }
}

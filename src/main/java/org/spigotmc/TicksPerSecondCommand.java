package org.spigotmc;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.MinecraftServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.galemc.gale.configuration.GaleGlobalConfiguration;

public class TicksPerSecondCommand extends Command
{

    public TicksPerSecondCommand(String name)
    {
        super( name );
        this.description = "Gets the current ticks per second for the server";
        this.usageMessage = "/tps";
        this.setPermission( "bukkit.command.tps" );
    }
    // Paper start
    private static final net.kyori.adventure.text.Component WARN_MSG = net.kyori.adventure.text.Component.text()
        .append(net.kyori.adventure.text.Component.text("Warning: ", net.kyori.adventure.text.format.NamedTextColor.RED))
        .append(net.kyori.adventure.text.Component.text("Memory usage on modern garbage collectors is not a stable value and it is perfectly normal to see it reach max. Please do not pay it much attention.", net.kyori.adventure.text.format.NamedTextColor.GOLD))
        .build();
    // Paper end

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !this.testPermission( sender ) )
        {
            return true;
        }

        // Paper start - Further improve tick handling
        double[] tps = org.bukkit.Bukkit.getTPSIncluding5SecondAverage(); // Gale - Purpur - 5 second TPS average
        net.kyori.adventure.text.Component[] tpsAvg = new net.kyori.adventure.text.Component[tps.length];

        for ( int i = 0; i < tps.length; i++) {
            tpsAvg[i] = TicksPerSecondCommand.format( tps[i] );
        }

        net.kyori.adventure.text.TextComponent.Builder builder = net.kyori.adventure.text.Component.text();
        builder.append(net.kyori.adventure.text.Component.text("TPS from last 5s, 1m, 5m, 15m: ", net.kyori.adventure.text.format.NamedTextColor.GOLD)); // Gale - Purpur - 5 second TPS average
        builder.append(net.kyori.adventure.text.Component.join(net.kyori.adventure.text.JoinConfiguration.commas(true), tpsAvg));
        sender.sendMessage(builder.asComponent());
        // Gale start - YAPFA - last tick time - in TPS command
        if (GaleGlobalConfiguration.get().misc.lastTickTimeInTpsCommand.enabled) {
            long lastTickProperTime = MinecraftServer.lastTickProperTime;
            long lastTickOversleepTime = MinecraftServer.lastTickOversleepTime;
            var lastTickTimeMessage = net.kyori.adventure.text.Component.text("Last tick: ")
                .append(formatTickTimeDuration(lastTickProperTime, 44, 50, 51));
            if (GaleGlobalConfiguration.get().misc.lastTickTimeInTpsCommand.addOversleep) {
                lastTickTimeMessage = lastTickTimeMessage.append(net.kyori.adventure.text.Component.text(" self + "))
                    .append(formatTickTimeDuration(lastTickOversleepTime, Math.max(1, 51 - lastTickProperTime), Math.max(2, 52 - lastTickProperTime), Math.max(3, 53 - lastTickProperTime)))
                    .append(net.kyori.adventure.text.Component.text(" oversleep = "))
                    .append(formatTickTimeDuration(lastTickProperTime + lastTickOversleepTime, 51, 52, 53));
            }
            lastTickTimeMessage = lastTickTimeMessage.color(net.kyori.adventure.text.format.NamedTextColor.GOLD);
            sender.sendMessage(
                lastTickTimeMessage
            );
        }
        // Gale end - YAPFA - last tick time - in TPS command
        if (args.length > 0 && args[0].equals("mem") && sender.hasPermission("bukkit.command.tpsmemory")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text("Current Memory Usage: ", net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(net.kyori.adventure.text.Component.text(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)) + "/" + (Runtime.getRuntime().totalMemory() / (1024 * 1024)) + " mb (Max: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " mb)", net.kyori.adventure.text.format.NamedTextColor.GREEN))
            );
            if (!this.hasShownMemoryWarning) {
                sender.sendMessage(WARN_MSG);
                this.hasShownMemoryWarning = true;
            }
        }
        // Paper end

        return true;
    }

    private boolean hasShownMemoryWarning; // Paper
    private static net.kyori.adventure.text.Component format(double tps) // Paper - Made static
    {
        // Paper
        net.kyori.adventure.text.format.TextColor color = ( ( tps > 18.0 ) ? net.kyori.adventure.text.format.NamedTextColor.GREEN : ( tps > 16.0 ) ? net.kyori.adventure.text.format.NamedTextColor.YELLOW : net.kyori.adventure.text.format.NamedTextColor.RED );
        String amount = Math.min(Math.round(tps * 100.0) / 100.0, 20.0) + (tps > 21.0  ? "*" : ""); // Paper - only print * at 21, we commonly peak to 20.02 as the tick sleep is not accurate enough, stop the noise
        return net.kyori.adventure.text.Component.text(amount, color);
        // Paper end
    }

    // Gale start - YAPFA - last tick time - in TPS command
    private static final TextColor safeColor = NamedTextColor.GREEN;
    private static final TextColor closeColor = NamedTextColor.YELLOW;
    private static final TextColor problematicColor = TextColor.color(0xf77c1e);
    private static final TextColor severeColor = NamedTextColor.RED;
    public static net.kyori.adventure.text.Component formatTickTimeDuration(long ms, long safeLimit, long closeLimit, long nonSevereLimit) {
        return net.kyori.adventure.text.Component.text(ms + " ", ms <= safeLimit ? safeColor : ms <= closeLimit ? closeColor : ms <= nonSevereLimit ? problematicColor : severeColor)
            .append(net.kyori.adventure.text.Component.text("ms", net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }
    // Gale end - YAPFA - last tick time - in TPS command

}

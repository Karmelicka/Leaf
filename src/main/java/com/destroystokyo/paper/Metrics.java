package com.destroystokyo.paper;

import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.plugin.Plugin;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * bStats collects some data for plugin authors.
 *
 * Check out https://bStats.org/ to learn more about bStats!
 */
public class Metrics {

    // Executor service for requests
    // We use an executor service because the Bukkit scheduler is affected by server lags
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // The version of this bStats class
    public static final int B_STATS_VERSION = 1;

    // The url to which the data is sent
    private static final String URL = "https://bStats.org/submitData/server-implementation";

    // Should failed requests be logged?
    private static boolean logFailedRequests = false;

    // The logger for the failed requests
    private static Logger logger = Logger.getLogger("bStats");

    // The name of the server software
    private final String name;

    // The uuid of the server
    private final String serverUUID;

    // A list with all custom charts
    private final List<CustomChart> charts = new ArrayList<>();

    /**
     * Class constructor.
     *
     * @param name              The name of the server software.
     * @param serverUUID        The uuid of the server.
     * @param logFailedRequests Whether failed requests should be logged or not.
     * @param logger            The logger for the failed requests.
     */
    public Metrics(String name, String serverUUID, boolean logFailedRequests, Logger logger) {
        this.name = name;
        this.serverUUID = serverUUID;
        Metrics.logFailedRequests = logFailedRequests;
        Metrics.logger = logger;

        // Start submitting the data
        startSubmitting();
    }

    /**
     * Adds a custom chart.
     *
     * @param chart The chart to add.
     */
    public void addCustomChart(CustomChart chart) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null!");
        }
        charts.add(chart);
    }

    /**
     * Starts the Scheduler which submits our data every 30 minutes.
     */
    private void startSubmitting() {
        final Runnable submitTask = () -> {
            if (MinecraftServer.getServer().hasStopped()) {
                return;
            }
            submitData();
        };

        // Many servers tend to restart at a fixed time at xx:00 which causes an uneven distribution of requests on the
        // bStats backend. To circumvent this problem, we introduce some randomness into the initial and second delay.
        // WARNING: You must not modify any part of this Metrics class, including the submit delay or frequency!
        // WARNING: Modifying this code will get your plugin banned on bStats. Just don't do it!
        long initialDelay = (long) (1000 * 60 * (3 + Math.random() * 3));
        long secondDelay = (long) (1000 * 60 * (Math.random() * 30));
        scheduler.schedule(submitTask, initialDelay, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(submitTask, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the plugin specific data.
     *
     * @return The plugin specific data.
     */
    private JSONObject getPluginData() {
        JSONObject data = new JSONObject();

        data.put("pluginName", name); // Append the name of the server software
        JSONArray customCharts = new JSONArray();
        for (CustomChart customChart : charts) {
            // Add the data of the custom charts
            JSONObject chart = customChart.getRequestJsonObject();
            if (chart == null) { // If the chart is null, we skip it
                continue;
            }
            customCharts.add(chart);
        }
        data.put("customCharts", customCharts);

        return data;
    }

    /**
     * Gets the server specific data.
     *
     * @return The server specific data.
     */
    private JSONObject getServerData() {
        // OS specific data
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        int coreCount = Runtime.getRuntime().availableProcessors();

        JSONObject data = new JSONObject();

        data.put("serverUUID", serverUUID);

        data.put("osName", osName);
        data.put("osArch", osArch);
        data.put("osVersion", osVersion);
        data.put("coreCount", coreCount);

        return data;
    }

    /**
     * Collects the data and sends it afterwards.
     */
    private void submitData() {
        final JSONObject data = getServerData();

        JSONArray pluginData = new JSONArray();
        pluginData.add(getPluginData());
        data.put("plugins", pluginData);

        try {
            // We are still in the Thread of the timer, so nothing get blocked :)
            sendData(data);
        } catch (Exception e) {
            // Something went wrong! :(
            if (logFailedRequests) {
                logger.log(Level.WARNING, "Could not submit stats of " + name, e);
            }
        }
    }

    /**
     * Sends the data to the bStats server.
     *
     * @param data The data to send.
     * @throws Exception If the request failed.
     */
    private static void sendData(JSONObject data) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null!");
        }
        HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();

        // Compress the data to save bandwidth
        byte[] compressedData = compress(data.toString());

        // Add headers
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");
        connection.addRequestProperty("Content-Encoding", "gzip"); // We gzip our request
        connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
        connection.setRequestProperty("Content-Type", "application/json"); // We send our data in JSON format
        connection.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

        // Send data
        connection.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.write(compressedData);
        outputStream.flush();
        outputStream.close();

        connection.getInputStream().close(); // We don't care about the response - Just send our data :)
    }

    /**
     * Gzips the given String.
     *
     * @param str The string to gzip.
     * @return The gzipped String.
     * @throws IOException If the compression failed.
     */
    private static byte[] compress(final String str) throws IOException {
        if (str == null) {
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        gzip.write(str.getBytes("UTF-8"));
        gzip.close();
        return outputStream.toByteArray();
    }

    /**
     * Represents a custom chart.
     */
    public static abstract class CustomChart {

        // The id of the chart
        final String chartId;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         */
        CustomChart(String chartId) {
            if (chartId == null || chartId.isEmpty()) {
                throw new IllegalArgumentException("ChartId cannot be null or empty!");
            }
            this.chartId = chartId;
        }

        private JSONObject getRequestJsonObject() {
            JSONObject chart = new JSONObject();
            chart.put("chartId", chartId);
            try {
                JSONObject data = getChartData();
                if (data == null) {
                    // If the data is null we don't send the chart.
                    return null;
                }
                chart.put("data", data);
            } catch (Throwable t) {
                if (logFailedRequests) {
                    logger.log(Level.WARNING, "Failed to get data for custom chart with id " + chartId, t);
                }
                return null;
            }
            return chart;
        }

        protected abstract JSONObject getChartData() throws Exception;

    }

    /**
     * Represents a custom simple pie.
     */
    public static class SimplePie extends CustomChart {

        private final Callable<String> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            data.put("value", value);
            return data;
        }
    }

    /**
     * Represents a custom advanced pie.
     */
    public static class AdvancedPie extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            JSONObject values = new JSONObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                values.put(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.put("values", values);
            return data;
        }
    }

    /**
     * Represents a custom drilldown pie.
     */
    public static class DrilldownPie extends CustomChart {

        private final Callable<Map<String, Map<String, Integer>>> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        public JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            JSONObject values = new JSONObject();
            Map<String, Map<String, Integer>> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean reallyAllSkipped = true;
            for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
                JSONObject value = new JSONObject();
                boolean allSkipped = true;
                for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                    value.put(valueEntry.getKey(), valueEntry.getValue());
                    allSkipped = false;
                }
                if (!allSkipped) {
                    reallyAllSkipped = false;
                    values.put(entryValues.getKey(), value);
                }
            }
            if (reallyAllSkipped) {
                // Null = skip the chart
                return null;
            }
            data.put("values", values);
            return data;
        }
    }

    /**
     * Represents a custom single line chart.
     */
    public static class SingleLineChart extends CustomChart {

        private final Callable<Integer> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SingleLineChart(String chartId, Callable<Integer> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            int value = callable.call();
            if (value == 0) {
                // Null = skip the chart
                return null;
            }
            data.put("value", value);
            return data;
        }

    }

    /**
     * Represents a custom multi line chart.
     */
    public static class MultiLineChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            JSONObject values = new JSONObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                values.put(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.put("values", values);
            return data;
        }

    }

    /**
     * Represents a custom simple bar chart.
     */
    public static class SimpleBarChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            JSONObject values = new JSONObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                JSONArray categoryValues = new JSONArray();
                categoryValues.add(entry.getValue());
                values.put(entry.getKey(), categoryValues);
            }
            data.put("values", values);
            return data;
        }

    }

    /**
     * Represents a custom advanced bar chart.
     */
    public static class AdvancedBarChart extends CustomChart {

        private final Callable<Map<String, int[]>> callable;

        /**
         * Class constructor.
         *
         * @param chartId  The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JSONObject getChartData() throws Exception {
            JSONObject data = new JSONObject();
            JSONObject values = new JSONObject();
            Map<String, int[]> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getValue().length == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                JSONArray categoryValues = new JSONArray();
                for (int categoryValue : entry.getValue()) {
                    categoryValues.add(categoryValue);
                }
                values.put(entry.getKey(), categoryValues);
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.put("values", values);
            return data;
        }

    }

    public static class PaperMetrics {
        public static void startMetrics() {
            // Get the config file
            File configFile = new File(new File((File) MinecraftServer.getServer().options.valueOf("plugins"), "bStats"), "config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            // Check if the config file exists
            if (!config.isSet("serverUuid")) {

                // Add default values
                config.addDefault("enabled", true);
                // Every server gets it's unique random id.
                config.addDefault("serverUuid", UUID.randomUUID().toString());
                // Should failed request be logged?
                config.addDefault("logFailedRequests", false);

                // Inform the server owners about bStats
                config.options().header(
                        "bStats collects some data for plugin authors like how many servers are using their plugins.\n" +
                                "To honor their work, you should not disable it.\n" +
                                "This has nearly no effect on the server performance!\n" +
                                "Check out https://bStats.org/ to learn more :)"
                ).copyDefaults(true);
                try {
                    config.save(configFile);
                } catch (IOException ignored) {
                }
            }
            // Load the data
            String serverUUID = config.getString("serverUuid");
            boolean logFailedRequests = config.getBoolean("logFailedRequests", false);
            // Only start Metrics, if it's enabled in the config
            if (config.getBoolean("enabled", true)) {
                Metrics metrics = new Metrics("Gale", serverUUID, logFailedRequests, Bukkit.getLogger()); // Gale - branding changes - metrics

                metrics.addCustomChart(new Metrics.SimplePie("minecraft_version", () -> {
                    String minecraftVersion = Bukkit.getVersion();
                    minecraftVersion = minecraftVersion.substring(minecraftVersion.indexOf("MC: ") + 4, minecraftVersion.length() - 1);
                    return minecraftVersion;
                }));

                metrics.addCustomChart(new Metrics.SingleLineChart("players", () -> Bukkit.getOnlinePlayers().size()));
                metrics.addCustomChart(new Metrics.SimplePie("online_mode", () -> Bukkit.getOnlineMode() ? "online" : "offline"));
                final String galeVersion; // Gale - branding changes - metrics
                final String implVersion = org.bukkit.craftbukkit.Main.class.getPackage().getImplementationVersion();
                if (implVersion != null) {
                    final String buildOrHash = implVersion.substring(implVersion.lastIndexOf('-') + 1);
                    galeVersion = "git-Gale-%s-%s".formatted(Bukkit.getServer().getMinecraftVersion(), buildOrHash); // Gale - branding changes - metrics
                } else {
                    galeVersion = "unknown"; // Gale - branding changes - metrics
                }
                metrics.addCustomChart(new Metrics.SimplePie("gale_version", () -> galeVersion)); // Gale - branding changes - metrics

                metrics.addCustomChart(new Metrics.DrilldownPie("java_version", () -> {
                    Map<String, Map<String, Integer>> map = new HashMap<>(2); // Gale - metrics - reduce HashMap capacity
                    String javaVersion = System.getProperty("java.version");
                    Map<String, Integer> entry = new HashMap<>(2); // Gale - metrics - reduce HashMap capacity
                    entry.put(javaVersion, 1);

                    // http://openjdk.java.net/jeps/223
                    // Java decided to change their versioning scheme and in doing so modified the java.version system
                    // property to return $major[.$minor][.$secuity][-ea], as opposed to 1.$major.0_$identifier
                    // we can handle pre-9 by checking if the "major" is equal to "1", otherwise, 9+
                    String majorVersion = javaVersion.split("\\.")[0];
                    String release;

                    int indexOf = javaVersion.lastIndexOf('.');

                    if (majorVersion.equals("1")) {
                        release = "Java " + javaVersion.substring(0, indexOf);
                    } else {
                        // of course, it really wouldn't be all that simple if they didn't add a quirk, now would it
                        // valid strings for the major may potentially include values such as -ea to deannotate a pre release
                        Matcher versionMatcher = Pattern.compile("\\d+").matcher(majorVersion);
                        if (versionMatcher.find()) {
                            majorVersion = versionMatcher.group(0);
                        }
                        release = "Java " + majorVersion;
                    }
                    map.put(release, entry);

                    return map;
                }));

                metrics.addCustomChart(new Metrics.DrilldownPie("legacy_plugins", () -> {
                    Map<String, Map<String, Integer>> map = new HashMap<>(2); // Gale - metrics - reduce HashMap capacity

                    // count legacy plugins
                    int legacy = 0;
                    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                        if (CraftMagicNumbers.isLegacy(plugin.getDescription())) {
                            legacy++;
                        }
                    }

                    // insert real value as lower dimension
                    Map<String, Integer> entry = new HashMap<>(2); // Gale - metrics - reduce HashMap capacity
                    entry.put(String.valueOf(legacy), 1);

                    // create buckets as higher dimension
                    if (legacy == 0) {
                        map.put("0 \uD83D\uDE0E", entry); // :sunglasses:
                    } else if (legacy <= 5) {
                        map.put("1-5", entry);
                    } else if (legacy <= 10) {
                        map.put("6-10", entry);
                    } else if (legacy <= 25) {
                        map.put("11-25", entry);
                    } else if (legacy <= 50) {
                        map.put("26-50", entry);
                    } else {
                        map.put("50+ \uD83D\uDE2D", entry); // :cry:
                    }

                    return map;
                }));

                // Gale start - metrics - proxy
                metrics.addCustomChart(new Metrics.DrilldownPie("proxy", () -> {
                    String type;
                    boolean onlineMode;
                    var proxiesConfig = io.papermc.paper.configuration.GlobalConfiguration.get().proxies;
                    if (proxiesConfig.velocity.enabled) {
                        type = "Velocity";
                        onlineMode = proxiesConfig.velocity.onlineMode;
                    } else if (org.spigotmc.SpigotConfig.bungee) {
                        type = "BungeeCord";
                        onlineMode = proxiesConfig.bungeeCord.onlineMode;
                    } else {
                        type = "none";
                        onlineMode = Bukkit.getOnlineMode();
                    }

                    Map<String, Map<String, Integer>> map = new HashMap<>(2);

                    // insert type and online mode as lower dimension
                    Map<String, Integer> entry = new HashMap<>(2);
                    entry.put(type + " (" + (onlineMode ? "online" : "offline") + ")", 1);

                    // create type as higher dimension
                    map.put(type, entry);

                    return map;
                }));
                // Gale end - metrics - proxy

                // Gale start - metrics - Java VM
                Map<String, Map<String, Integer>> javaVirtualMachineMap = new HashMap<>(2);
                {
                    Map<String, Integer> entry = new HashMap<>(2);
                    String vmVendor = null;
                    try {
                        vmVendor = System.getProperty("java.vm.vendor");
                    } catch (Exception ignored) {}
                    entry.put(vmVendor == null ? "Unknown" : vmVendor, 1);
                    String vmName = null;
                    try {
                        vmName = System.getProperty("java.vm.name");
                    } catch (Exception ignored) {}
                    javaVirtualMachineMap.put(vmName == null ? "Unknown" : vmName, entry);
                }
                metrics.addCustomChart(new Metrics.DrilldownPie("java_virtual_machine", () -> javaVirtualMachineMap));
                // Gale end - metrics - Java VM

                // Gale start - metrics - per-server player count
                metrics.addCustomChart(new Metrics.DrilldownPie("per_server_player_count", () -> {
                    Map<String, Map<String, Integer>> map = new HashMap<>(2);

                    // count players
                    int playerCount = Bukkit.getOnlinePlayers().size();

                    // insert real value as lower dimension
                    Map<String, Integer> entry = new HashMap<>(2);
                    entry.put(String.valueOf(playerCount), 1);

                    // create buckets as higher dimension
                    if (playerCount <= 5) {
                        map.put(String.valueOf(playerCount), entry);
                    } else if (playerCount > 1000) {
                        map.put("> 1000", entry);
                    } else {
                        int divisor;
                        if (playerCount <= 50) {
                            divisor = 5;
                        } else if (playerCount <= 100) {
                            divisor = 10;
                        } else if (playerCount <= 250) {
                            divisor = 25;
                        } else if (playerCount <= 500) {
                            divisor = 50;
                        } else {
                            divisor = 100;
                        }
                        int start = (playerCount - 1) / divisor * divisor + 1;
                        int end = start + divisor - 1;
                        map.put(start + "-" + end, entry);
                    }

                    return map;
                }));
                // Gale end - metrics - per-server player count

                // Gale start - metrics - plugin count
                metrics.addCustomChart(new Metrics.DrilldownPie("plugin_count", () -> {
                    Map<String, Map<String, Integer>> map = new HashMap<>(2);

                    // count plugins
                    int pluginCount = Bukkit.getPluginManager().getPlugins().length;

                    // insert real value as lower dimension
                    Map<String, Integer> entry = new HashMap<>(2);
                    entry.put(String.valueOf(pluginCount), 1);

                    // create buckets as higher dimension
                    if (pluginCount <= 5) {
                        map.put(String.valueOf(pluginCount), entry);
                    } else if (pluginCount > 1000) {
                        map.put("> 1000", entry);
                    } else {
                        int divisor;
                        if (pluginCount <= 50) {
                            divisor = 5;
                        } else if (pluginCount <= 100) {
                            divisor = 10;
                        } else if (pluginCount <= 250) {
                            divisor = 25;
                        } else if (pluginCount <= 500) {
                            divisor = 50;
                        } else {
                            divisor = 100;
                        }
                        int start = (pluginCount - 1) / divisor * divisor + 1;
                        int end = start + divisor - 1;
                        map.put(start + "-" + end, entry);
                    }

                    return map;
                }));
                // Gale end - metrics - plugin count

                // Gale start - metrics - netty threads
                metrics.addCustomChart(new Metrics.SimplePie("netty_thread_count", () -> {
                    // Try to get the number of Netty threads from the system property
                    try {
                        return System.getProperty("io.netty.eventLoopThreads");
                    } catch (Exception ignored) {}
                    // Otherwise, we fall back to nothing currently (reading from the Spigot configuration causes a re-read which is undesirable)
                    return null;
                }));
                // Gale end - metrics - netty threads

                metrics.addCustomChart(new Metrics.SimplePie("chunk_system_io_thread_count", () -> String.valueOf(io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.newChunkSystemIOThreads))); // Gale - metrics - chunk system IO threads

                // Gale start - metrics - physical cores
                metrics.addCustomChart(new Metrics.SimplePie("physical_core_count", () -> {
                    try {
                        int physicalProcessorCount = new oshi.SystemInfo().getHardware().getProcessor().getPhysicalProcessorCount();
                        if (physicalProcessorCount > 0) {
                            return String.valueOf(physicalProcessorCount);
                        }
                    } catch (Exception ignored) {}
                    return null;
                }));
                // Gale end - metrics - physical cores

                // Gale start - metrics - processor frequency
                metrics.addCustomChart(new Metrics.DrilldownPie("processor_frequency", () -> {
                    try {
                        long processorFrequency = new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getVendorFreq();
                        if (processorFrequency > 0) {

                            Map<String, Map<String, Integer>> map = new HashMap<>(2);

                            // use MHz as lower dimension
                            var flooredMHz = processorFrequency / 1_000_000L;
                            Map<String, Integer> entry = new HashMap<>(2);
                            if (flooredMHz < 1) {
                                entry.put("< 1 MHz", 1);
                            } else if (flooredMHz < 1000) {
                                entry.put(flooredMHz + " MHz", 1);
                            } else {
                                // Add a comma
                                StringBuilder flooredMHzAfterComma = new StringBuilder(String.valueOf(flooredMHz % 1000));
                                while (flooredMHzAfterComma.length() < 3) {
                                    flooredMHzAfterComma.insert(0, "0");
                                }
                                entry.put((flooredMHz / 1000) + "," + flooredMHzAfterComma + " MHz", 1);
                            }

                            // use tenth of GHz as higher dimension
                            long flooredTenthGHz = processorFrequency / 100_000_000L;
                            if (flooredTenthGHz < 1) {
                                map.put("< 0.1 GHz", entry);
                            } else {
                                // Add a dot
                                map.put((flooredTenthGHz / 10) + "." + (flooredTenthGHz % 10) + " GHz", entry);
                            }

                            return map;

                        }
                    } catch (Exception ignored) {}
                    return null;
                }));
                // Gale end - metrics - processor frequency

                // Gale start - metrics - physical memory
                metrics.addCustomChart(new Metrics.DrilldownPie("physical_memory_total", () -> {
                    try {
                        long physicalMemory = new oshi.SystemInfo().getHardware().getMemory().getTotal();
                        if (physicalMemory > 0) {

                            Map<String, Map<String, Integer>> map = new HashMap<>(2);

                            // use floored MB as lower dimension
                            var flooredMB = physicalMemory / (1L << 20);
                            Map<String, Integer> entry = new HashMap<>(2);
                            entry.put(flooredMB < 1 ? "< 1 MB" : flooredMB + " MB", 1);

                            // use floored GB as higher dimension
                            var flooredGB = physicalMemory / (1L << 30);
                            map.put(flooredGB < 1 ? "< 1 GB" : flooredGB + " GB", entry);

                            return map;

                        }
                    } catch (Exception ignored) {}
                    return null;
                }));
                // Gale end - metrics - physical memory

                // Gale start - metrics - runtime max memory
                metrics.addCustomChart(new Metrics.DrilldownPie("runtime_max_memory", () -> {

                    // get memory limit
                    long maxMemory = Runtime.getRuntime().maxMemory();
                    if (maxMemory <= 0) {
                        return null;
                    }

                    Map<String, Map<String, Integer>> map = new HashMap<>(2);

                    // in the case of no limit
                    if (maxMemory == Long.MAX_VALUE) {
                        Map<String, Integer> entry = new HashMap<>(2);
                        entry.put("no limit", 1);
                        map.put("no limit", entry);
                        return map;
                    }

                    // use floored MB as lower dimension
                    var flooredMB = maxMemory / (1L << 20);
                    Map<String, Integer> entry = new HashMap<>(2);
                    entry.put(flooredMB < 1 ? "< 1 MB" : flooredMB + " MB", 1);

                    // use floored GB as higher dimension
                    var flooredGB = maxMemory / (1L << 30);
                    map.put(flooredGB < 1 ? "< 1 GB" : flooredGB + " GB", entry);

                    return map;
                }));
                // Gale end - metrics - runtime max memory

                // Gale start - semantic version - include in metrics
                Map<String, Map<String, Integer>> semanticVersionMap = new HashMap<>(2);
                {
                    Map<String, Integer> entry = new HashMap<>(2);
                    entry.put(org.galemc.gale.version.GaleSemanticVersion.version, 1);
                    semanticVersionMap.put(org.galemc.gale.version.GaleSemanticVersion.majorMinorVersion, entry);
                }
                metrics.addCustomChart(new Metrics.DrilldownPie("gale_semantic_version", () -> semanticVersionMap));
                // Gale end - semantic version - include in metrics

                // Gale start - SIMD support - include in metrics
                Map<String, Map<String, Integer>> simdSupportMap = new HashMap<>(2); // Empty until initialized
                metrics.addCustomChart(new Metrics.DrilldownPie("simd_support", () -> {
                    if (!gg.pufferfish.pufferfish.simd.SIMDDetection.isInitialized()) {
                        return null;
                    }
                    if (simdSupportMap.isEmpty()) {
                        // Initialize
                        boolean isEnabled = gg.pufferfish.pufferfish.simd.SIMDDetection.isEnabled();

                        // use details as lower dimension
                        Map<String, Integer> entry = new HashMap<>(2);
                        String details;
                        if (isEnabled) {
                            details = "int " + gg.pufferfish.pufferfish.simd.SIMDDetection.intVectorBitSize() + "*" + gg.pufferfish.pufferfish.simd.SIMDDetection.intElementSize() + ", float " + gg.pufferfish.pufferfish.simd.SIMDDetection.floatVectorBitSize() + "*" + gg.pufferfish.pufferfish.simd.SIMDDetection.floatElementSize();
                        } else {
                            if (!gg.pufferfish.pufferfish.simd.SIMDDetection.supportingJavaVersion()) {
                                details = "unsupported Java";
                                try {
                                    var javaVersion = gg.pufferfish.pufferfish.simd.SIMDDetection.getJavaVersion();
                                    details += " (" + javaVersion + ")";
                                } catch (Throwable ignored) {}
                            } else if (!gg.pufferfish.pufferfish.simd.SIMDDetection.testRunCompleted()) {
                                details = "test failed";
                            } else if (gg.pufferfish.pufferfish.simd.SIMDDetection.unsupportingLaneSize()) {
                                details = "no supporting lane size";
                            } else {
                                details = "other reason";
                            }
                        }
                        entry.put(details, 1);

                        // use enabled or not as higher dimension
                        simdSupportMap.put(String.valueOf(isEnabled), entry);

                    }
                    return simdSupportMap;
                }));
                // Gale end - SIMD support - include in metrics

            }

        }
    }
}

package in.minetop.fakeplayers.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Plugin(id = "minetopfakeplayers", name = "MineTopFakePlayers", version = "1.0.0", authors = {"MineTop"})
public final class MineTopFakePlayersVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private volatile Settings settings = new Settings(true, 25, 999, List.of());

    @Inject
    public MineTopFakePlayersVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("fakeplayers").aliases("fpv").plugin(this).build(),
                new AdminCommand()
        );
        logger.info("MineTopFakePlayers Velocity enabled.");
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        Settings s = settings;
        int real = proxy.getPlayerCount();
        int shown = Math.max(0, s.addToReal ? real + s.fakeCount : s.fakeCount);
        int max = Math.max(shown, s.maxPlayers);

        List<ServerPing.SamplePlayer> samples = new ArrayList<>();
        for (String name : s.sampleNames) {
            samples.add(new ServerPing.SamplePlayer(name, UUID.nameUUIDFromBytes(("MineTop:" + name).getBytes())));
        }

        ServerPing changed = event.getPing().asBuilder()
                .onlinePlayers(shown)
                .maximumPlayers(max)
                .samplePlayers(samples.toArray(ServerPing.SamplePlayer[]::new))
                .build();
        event.setPing(changed);
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (Files.notExists(configPath)) {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (input == null) throw new IOException("Bundled config.yml missing");
                    Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            ConfigurationNode root = YamlConfigurationLoader.builder().path(configPath).build().load();
            settings = new Settings(
                    root.node("add-to-real").getBoolean(true),
                    Math.max(0, root.node("fake-count").getInt(25)),
                    Math.max(1, root.node("max-players").getInt(999)),
                    root.node("sample-names").getList(String.class, List.of())
            );
        } catch (Exception ex) {
            logger.error("Could not load config.yml; keeping previous/default settings.", ex);
        }
    }

    private final class AdminCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();
            if (!source.hasPermission("minetop.fakeplayers.admin")) {
                source.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return;
            }
            if (args.length == 0) {
                Settings s = settings;
                source.sendMessage(Component.text("Fake count: " + s.fakeCount + " | mode: " + (s.addToReal ? "real + fake" : "fixed") + " | max: " + s.maxPlayers, NamedTextColor.AQUA));
                source.sendMessage(Component.text("/fpv set <number> | /fpv mode <add|fixed> | /fpv reload", NamedTextColor.WHITE));
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    loadConfig();
                    source.sendMessage(Component.text("Velocity fake-player config reloaded.", NamedTextColor.GREEN));
                }
                case "set" -> {
                    if (args.length < 2) {
                        source.sendMessage(Component.text("Usage: /fpv set <number>", NamedTextColor.YELLOW));
                        return;
                    }
                    try {
                        int count = Math.max(0, Integer.parseInt(args[1]));
                        Settings old = settings;
                        settings = new Settings(old.addToReal, count, old.maxPlayers, old.sampleNames);
                        saveRuntimeSettings();
                        source.sendMessage(Component.text("Fake count set to " + count + ".", NamedTextColor.GREEN));
                    } catch (NumberFormatException ex) {
                        source.sendMessage(Component.text("Number is invalid.", NamedTextColor.RED));
                    }
                }
                case "mode" -> {
                    if (args.length < 2 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("fixed"))) {
                        source.sendMessage(Component.text("Usage: /fpv mode <add|fixed>", NamedTextColor.YELLOW));
                        return;
                    }
                    Settings old = settings;
                    boolean add = args[1].equalsIgnoreCase("add");
                    settings = new Settings(add, old.fakeCount, old.maxPlayers, old.sampleNames);
                    saveRuntimeSettings();
                    source.sendMessage(Component.text("Mode set to " + (add ? "real + fake" : "fixed") + ".", NamedTextColor.GREEN));
                }
                default -> source.sendMessage(Component.text("/fpv set <number> | /fpv mode <add|fixed> | /fpv reload", NamedTextColor.YELLOW));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("minetop.fakeplayers.admin");
        }
    }

    private void saveRuntimeSettings() {
        Path configPath = dataDirectory.resolve("config.yml");
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(configPath).build();
            ConfigurationNode root = loader.load();
            Settings s = settings;
            root.node("add-to-real").set(s.addToReal);
            root.node("fake-count").set(s.fakeCount);
            root.node("max-players").set(s.maxPlayers);
            root.node("sample-names").setList(String.class, s.sampleNames);
            loader.save(root);
        } catch (Exception ex) {
            logger.error("Could not save config.yml", ex);
        }
    }

    private record Settings(boolean addToReal, int fakeCount, int maxPlayers, List<String> sampleNames) {}
}

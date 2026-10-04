package vn.hideseek;

import org.bukkit.plugin.java.JavaPlugin;

public final class HideSeekPlugin extends JavaPlugin {

    private Cfg cfg;
    private Disguises disguises;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new Cfg(this);
        disguises = new Disguises(this);
        game = new Game(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        Commands commands = new Commands(this);
        var cmd = getCommand("hs");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        game.startLoop();
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (disguises != null) disguises.removeAll();
    }

    public Cfg cfg() { return cfg; }
    public Disguises disguises() { return disguises; }
    public Game game() { return game; }
}

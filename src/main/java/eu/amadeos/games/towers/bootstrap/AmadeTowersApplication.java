package eu.amadeos.games.towers.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.games.towers.ui.game.TowersGame;
import eu.amadeos.games.towers.ui.main.TowersPresenter;
import eu.amadeos.games.towers.ui.main.TowersWindow;

public final class AmadeTowersApplication {
    public static final AppInfo APP = new AppInfo("amade-towers-game", "Towers", "towers");

    private AmadeTowersApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeTowersApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            TowersWindow window = injector.getInstance(TowersWindow.class);
            TowersPresenter presenter = injector.getInstance(TowersPresenter.class);
            TowersGame game = injector.getInstance(TowersGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new TowersModule());
    }
    // --8<-- [end:main]
}

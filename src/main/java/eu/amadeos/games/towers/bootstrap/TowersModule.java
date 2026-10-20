package eu.amadeos.games.towers.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.games.towers.repository.TowersSettings;
import eu.amadeos.games.towers.repository.Progress;
import eu.amadeos.games.towers.ui.game.Arena;
import eu.amadeos.games.towers.ui.game.TowersGame;
import eu.amadeos.games.towers.ui.main.TowersPresenter;
import eu.amadeos.games.towers.ui.main.TowersView;
import eu.amadeos.games.towers.ui.main.TowersWindow;

import java.util.Locale;

/**
 * amade-towers-game bindings. Theme, language and the XDG directories (where {@link Progress} saves) come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class TowersModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.towers.messages";

    @Override
    protected void configure() {
        bind(TowersSettings.class).in(Scopes.SINGLETON);
        bind(Progress.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mouse.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(TowersGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(TowersGame.class);
        bind(TowersWindow.class).in(Scopes.SINGLETON);
        bind(TowersView.class).to(TowersWindow.class);
        bind(TowersPresenter.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(TowersModule.class, MESSAGES, locale);
    }
}

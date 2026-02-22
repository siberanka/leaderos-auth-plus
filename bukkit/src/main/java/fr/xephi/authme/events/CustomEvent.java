package fr.xephi.authme.events;

import org.bukkit.event.Event;

/**
 * Minimal AuthMe-compatible base event.
 */
public abstract class CustomEvent extends Event {

    public CustomEvent() {
        super(false);
    }

    public CustomEvent(boolean isAsync) {
        super(isAsync);
    }

}

package com.mcserby.agent.bot;

import com.mcserby.agent.dedicated.emag.DedicatedMessage;
import com.mcserby.agent.dedicated.emag.ShoppingAction;

import java.util.UUID;

public interface ShoppingAutomationBot {
    UUID newSession();

    DedicatedMessage performAction(UUID sessionId, ShoppingAction shoppingAction);

    void trySleep(int ms);

    void closeSession(UUID uuid);
}

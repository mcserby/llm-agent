package com.mcserby.agent.dedicated.emag;

import com.mcserby.agent.model.MessageType;

public record DedicatedMessage(DedicatedMessageType type, String text){

    public String toString(){
        return type + ": " + text;
    }

}

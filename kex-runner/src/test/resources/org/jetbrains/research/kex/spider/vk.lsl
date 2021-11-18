libsl "1.0.0";
library vkjavasdk version "1.0.11" url "https://github.com/VKCOM/vk-java-sdk";

types {
    Integer (java.lang.Integer);
    String (string);
    MessageSendQuery(com.vk.api.sdk.queries.messages.MessagesSendQuery);
    int (int32);
}

automaton com.vk.api.sdk.queries.messages.MessagesSendQuery : MessageSendQuery {
    initstate Created;
    var messageSet: bool = false;
    var peerIdSet: bool = false;
    var fromId: int = 0;
    finishstate Executed;

    shift Created->Executed(execute);

    fun peerId(value: Integer): MessageSendQuery
        requires value > 0;

    fun ownerId(value: Integer): MessageSendQuery {
        fromId = value; // todo
    }

    fun message(value: String): MessageSendQuery
        requires isMessageNotEmpty: value != "";
}

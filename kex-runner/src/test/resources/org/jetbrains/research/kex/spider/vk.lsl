libsl "1.0.0";
library vkjavasdk version "1.0.11" url "https://github.com/VKCOM/vk-java-sdk";

types {
    Integer (java.lang.Integer);
    Integers (array<java.lang.Integer>);
    IntegerList (java.util.List<Integer>);
    String (string);
    int (int32);
    Object (java.lang.Object);
    MessagesSendQuery (com.vk.api.sdk.queries.messages.MessagesSendQuery);
    VkApiClient (com.vk.api.sdk.client.VkApiClient);
    GroupActor (com.vk.api.sdk.client.actors.GroupActor);
    ApiRequest (com.vk.api.sdk.client.ApiRequest);
}

automaton com.vk.api.sdk.client.VkApiClient : VkApiClient {
    initstate Created;

    fun setVersion(`version`: String)
        requires isVersionSupported: `version` = "5.102" | `version` = "5.100" | `version` = "5.95";
}

automaton com.vk.api.sdk.client.actors.GroupActor : GroupActor {
    initstate Created;

    fun `<init>`(groupId: Integer, accessToken: String)
        requires isGroupIdPositive: groupId > 0;
}

automaton com.vk.api.sdk.queries.messages.MessagesSendQuery : MessagesSendQuery {
    initstate Created;

    var contentSet: bool = false;
    var targetIdSet: bool = false;

    finishstate Executed;

    shift Created->Executed(execute);

    fun peerId(value: Integer): MessagesSendQuery
        requires isIdPos: value > 0;
        requires isIdWasntSet: !targetIdSet;
    {
        targetIdSet = true;
    }

    fun peerIds(value: Integers): MessagesSendQuery
        requires isIdWasntSet: !targetIdSet;
    {
        targetIdSet = true;
    }

    fun peerIds(value: IntegerList): MessagesSendQuery
        requires isIdWasntSet: !targetIdSet;
    {
        targetIdSet = true;
    }

    fun chatId(value: Integer): MessagesSendQuery
        requires isIdWasntSet: !targetIdSet;
        requires isIdPos: value > 0;
    {
        targetIdSet = true;
    }

    fun message(value: String): MessagesSendQuery
        requires isMessageNotEmpty: value != "";
    {
        contentSet = true;
    }

    fun attachment(value: String): MessagesSendQuery  // todo
    {
        contentSet = true;
    }

    fun forwardMessages(value: IntegerList): MessagesSendQuery
    {
        contentSet = true;
    }

    fun forwardMessages(value: Integers): MessagesSendQuery
    {
        contentSet = true;
    }

    fun stickerId(value: Integer): MessagesSendQuery
    {
        contentSet = true;
    }

    fun execute(): Object
        requires isContentSet: contentSet;
        requires isTargetSet: targetIdSet;
}
